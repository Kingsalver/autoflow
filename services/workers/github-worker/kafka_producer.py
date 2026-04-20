import json
import logging
import uuid
from datetime import datetime, timezone
from typing import Optional

from confluent_kafka import Producer

from config import settings

logger = logging.getLogger(__name__)

TOPIC = "job.intake.events"

_producer: Optional[Producer] = None


def init_producer() -> None:
    global _producer
    _producer = Producer(
        {
            "bootstrap.servers": settings.kafka_bootstrap_servers,
            "acks": "all",
            "retries": 3,
            "retry.backoff.ms": 500,
        }
    )
    logger.info("Kafka producer initialized (bootstrap: %s)", settings.kafka_bootstrap_servers)


def close_producer() -> None:
    global _producer
    if _producer:
        _producer.flush(timeout=10)
        logger.info("Kafka producer flushed and closed")


def _delivery_report(err, msg) -> None:
    if err:
        logger.error("Kafka delivery failed for %s: %s", msg.key(), err)
    else:
        logger.debug(
            "Kafka delivered to %s [partition %d] offset %d",
            msg.topic(),
            msg.partition(),
            msg.offset(),
        )


def publish_job_event(
    *,
    user_id: str,
    job_listing_id: str,
    company: str,
    role: str,
    location: Optional[str],
    url: Optional[str],
) -> None:
    if _producer is None:
        raise RuntimeError("Kafka producer not initialized — call init_producer() first")

    event = {
        "event_id": str(uuid.uuid4()),
        "user_id": user_id,
        "source": "GITHUB",
        "event_type": "JOB_LISTING_INGESTED",
        "payload": {
            "job_listing_id": job_listing_id,
            "company": company,
            "role": role,
            "location": location,
            "url": url,
        },
        "occurred_at": datetime.now(timezone.utc).isoformat(),
    }

    _producer.produce(
        topic=TOPIC,
        key=user_id,  # partition by user_id for per-user ordering
        value=json.dumps(event),
        callback=_delivery_report,
    )
    # Trigger delivery callbacks; don't block — use flush() at shutdown.
    _producer.poll(0)
