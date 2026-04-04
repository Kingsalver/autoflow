"""
Autoflow — GitHub Worker

Polls SimplifyJobs/New-Grad-Positions for new job listings and ingests them
into PostgreSQL + Kafka.  APScheduler runs the poll loop inside the FastAPI
process; no external cron needed.
"""

import logging
import threading
from contextlib import asynccontextmanager
from typing import Any

import uvicorn
from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI, HTTPException

import db
import kafka_producer
import redis_client
from config import settings
from github_poller import fetch_readme_listings, row_hash

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s — %(message)s",
)
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Session stats (in-memory, reset on restart)
# ---------------------------------------------------------------------------
_stats_lock = threading.Lock()
_stats: dict[str, Any] = {
    "jobs_ingested_this_session": 0,
    "polls_completed": 0,
    "last_poll_at": None,
    "last_error": None,
}

# Filled in at startup
_system_user_id: str = ""


# ---------------------------------------------------------------------------
# Core poll logic
# ---------------------------------------------------------------------------

def run_poll() -> int:
    """
    Fetch the README, deduplicate, and ingest new listings.
    Returns the number of new jobs ingested in this run.
    """
    global _system_user_id

    logger.info("Starting poll for repo: %s", settings.github_repo)
    new_count = 0

    try:
        rows, commit_sha = fetch_readme_listings()
    except Exception as exc:
        logger.error("Failed to fetch listings from GitHub: %s", exc)
        with _stats_lock:
            _stats["last_error"] = str(exc)
        raise

    for row in rows:
        raw_row = row["raw_row"]
        r_hash = row_hash(raw_row)
        redis_key = f"processed:{settings.github_repo}:{r_hash}"

        # 1. Redis fast-path dedup
        if redis_client.is_processed(redis_key):
            continue

        # external_id used for DB-level dedup
        external_id = f"{settings.github_repo}:{r_hash}"

        # 2. Write to DB
        try:
            listing_id = db.insert_job_listing(
                user_id=_system_user_id,
                company=row["company"],
                role=row["role"],
                location=row.get("location"),
                url=row.get("url"),
                raw_description=raw_row,
                source_repo=settings.github_repo,
                source_commit_sha=commit_sha,
                external_id=external_id,
            )
        except Exception as exc:
            logger.error("DB insert failed for row %r: %s", raw_row[:60], exc)
            continue

        if listing_id is None:
            # Row already existed in DB — update Redis cache and move on
            redis_client.mark_processed(redis_key)
            continue

        # 3. Record in processed_events
        try:
            db.record_processed_event(redis_key)
        except Exception as exc:
            logger.warning("processed_events insert failed: %s", exc)

        # 4. Mark in Redis
        redis_client.mark_processed(redis_key)

        # 5. Publish Kafka event
        try:
            kafka_producer.publish_job_event(
                user_id=_system_user_id,
                job_listing_id=listing_id,
                company=row["company"],
                role=row["role"],
                location=row.get("location"),
                url=row.get("url"),
            )
        except Exception as exc:
            logger.error("Kafka publish failed for listing %s: %s", listing_id, exc)
            # Continue — the row is already in Postgres; Kafka failure is
            # non-fatal for ingest correctness.

        new_count += 1
        logger.info(
            "Ingested: %s — %s (%s)",
            row["company"],
            row["role"],
            row.get("location", ""),
        )

    with _stats_lock:
        _stats["jobs_ingested_this_session"] += new_count
        _stats["polls_completed"] += 1
        from datetime import datetime, timezone
        _stats["last_poll_at"] = datetime.now(timezone.utc).isoformat()
        if new_count:
            _stats["last_error"] = None

    logger.info("Poll complete — %d new listings ingested", new_count)
    return new_count


# ---------------------------------------------------------------------------
# Application lifespan
# ---------------------------------------------------------------------------

scheduler = BackgroundScheduler()


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _system_user_id

    logger.info("GitHub Worker starting up…")

    # Init connections
    db.init_pool()
    redis_client.init_redis()
    kafka_producer.init_producer()

    # Resolve system user
    _system_user_id = db.get_or_create_system_user()
    logger.info("System user id: %s", _system_user_id)

    # Run an immediate poll at startup
    try:
        run_poll()
    except Exception as exc:
        logger.error("Initial poll failed: %s", exc)

    # Schedule recurring polls
    scheduler.add_job(
        run_poll,
        trigger="interval",
        minutes=settings.poll_interval_minutes,
        id="github_poll",
        replace_existing=True,
    )
    scheduler.start()
    logger.info(
        "Scheduler started — polling every %d minutes", settings.poll_interval_minutes
    )

    yield

    # Shutdown
    scheduler.shutdown(wait=False)
    kafka_producer.close_producer()
    db.close_pool()
    redis_client.close_redis()
    logger.info("GitHub Worker shut down cleanly")


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Autoflow GitHub Worker",
    description="Polls SimplifyJobs/New-Grad-Positions and ingests job listings",
    version="1.0.0",
    lifespan=lifespan,
)


@app.get("/health", tags=["ops"])
def health():
    return {"status": "ok"}


@app.post("/poll", tags=["ops"])
def trigger_poll():
    """Trigger an immediate poll run synchronously and return the result."""
    try:
        new_count = run_poll()
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    return {"new_listings_ingested": new_count}


@app.get("/stats", tags=["ops"])
def stats():
    with _stats_lock:
        return dict(_stats)


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=settings.port,
        reload=False,
    )
