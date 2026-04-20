import logging
from typing import Optional

import redis

from config import settings

logger = logging.getLogger(__name__)

DEDUP_TTL_SECONDS = 7 * 24 * 60 * 60  # 7 days

_client: Optional[redis.Redis] = None


def init_redis() -> None:
    global _client
    _client = redis.Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        decode_responses=True,
    )
    _client.ping()
    logger.info("Redis connection established")


def close_redis() -> None:
    global _client
    if _client:
        _client.close()
        logger.info("Redis connection closed")


def _get_client() -> redis.Redis:
    if _client is None:
        raise RuntimeError("Redis not initialized — call init_redis() first")
    return _client


def is_processed(key: str) -> bool:
    """Return True if the key exists in the Redis dedup cache."""
    try:
        return bool(_get_client().exists(key))
    except redis.RedisError as exc:
        # On Redis failure, fall through to DB-level dedup rather than
        # blocking ingest entirely.
        logger.warning("Redis is_processed check failed for %s: %s", key, exc)
        return False


def mark_processed(key: str) -> None:
    """Set the key in Redis with a 7-day TTL."""
    try:
        _get_client().set(key, "1", ex=DEDUP_TTL_SECONDS)
    except redis.RedisError as exc:
        logger.warning("Redis mark_processed failed for %s: %s", key, exc)
