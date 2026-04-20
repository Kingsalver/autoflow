import logging
import uuid
from typing import Optional

import psycopg2
from psycopg2 import pool
from psycopg2.extras import RealDictCursor

from config import settings

logger = logging.getLogger(__name__)

_pool: Optional[pool.ThreadedConnectionPool] = None

SYSTEM_USER_EMAIL = "system@autoflow.internal"
SYSTEM_USER_DISPLAY_NAME = "System Worker"


def init_pool() -> None:
    global _pool
    _pool = pool.ThreadedConnectionPool(
        minconn=1,
        maxconn=10,
        host=settings.db_host,
        port=settings.db_port,
        dbname=settings.db_name,
        user=settings.db_user,
        password=settings.db_password,
    )
    logger.info("PostgreSQL connection pool initialized")


def close_pool() -> None:
    global _pool
    if _pool:
        _pool.closeall()
        logger.info("PostgreSQL connection pool closed")


def get_connection():
    if _pool is None:
        raise RuntimeError("DB pool not initialized — call init_pool() first")
    return _pool.getconn()


def release_connection(conn) -> None:
    if _pool:
        _pool.putconn(conn)


def get_or_create_system_user() -> str:
    """Upsert the system user and return its UUID as a string."""
    conn = get_connection()
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                """
                INSERT INTO users (email, display_name)
                VALUES (%s, %s)
                ON CONFLICT (email) DO UPDATE
                    SET display_name = EXCLUDED.display_name,
                        updated_at   = NOW()
                RETURNING id
                """,
                (SYSTEM_USER_EMAIL, SYSTEM_USER_DISPLAY_NAME),
            )
            row = cur.fetchone()
            conn.commit()
            user_id = str(row["id"])
            logger.info("System user id: %s", user_id)
            return user_id
    except Exception:
        conn.rollback()
        raise
    finally:
        release_connection(conn)


def insert_job_listing(
    *,
    user_id: str,
    company: str,
    role: str,
    location: Optional[str],
    url: Optional[str],
    raw_description: Optional[str],
    source_repo: str,
    source_commit_sha: Optional[str],
    external_id: str,
) -> Optional[str]:
    """
    Insert a job listing row.  Returns the new UUID string, or None if the
    external_id already exists for this user (soft-dedup at DB level).
    """
    conn = get_connection()
    try:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            # Check for pre-existing external_id to avoid duplicate rows when
            # the Redis TTL has expired but the row is already in the DB.
            cur.execute(
                "SELECT id FROM job_listings WHERE user_id = %s AND external_id = %s",
                (user_id, external_id),
            )
            existing = cur.fetchone()
            if existing:
                return None

            listing_id = str(uuid.uuid4())
            cur.execute(
                """
                INSERT INTO job_listings
                    (id, user_id, company, role, location, url,
                     raw_description, source_repo, source_commit_sha, external_id)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    listing_id,
                    user_id,
                    company,
                    role,
                    location,
                    url,
                    raw_description,
                    source_repo,
                    source_commit_sha,
                    external_id,
                ),
            )
            conn.commit()
            return listing_id
    except Exception:
        conn.rollback()
        raise
    finally:
        release_connection(conn)


def record_processed_event(event_key: str) -> None:
    """Insert into processed_events; silently ignore duplicate key violations."""
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO processed_events (event_key)
                VALUES (%s)
                ON CONFLICT (event_key) DO NOTHING
                """,
                (event_key,),
            )
            conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        release_connection(conn)
