package com.autoflow.workflow.messaging.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound event published to {@code workflow.trigger.events} by integration workers
 * (GitHub worker, Gmail worker, etc.).  The workflow engine consumes this, evaluates
 * all enabled workflows whose triggerConfig matches, and fans out action events.
 */
public record TriggerEvent(

    /** Stable deduplication key — workers should use their own idempotency ID here. */
    @JsonProperty("event_id")
    String eventId,

    /** Owning user — Kafka partition key, used for row-level security checks. */
    @JsonProperty("user_id")
    UUID userId,

    /**
     * Integration source that produced this event.
     * Values: GITHUB, GMAIL, MANUAL, SCHEDULER
     */
    @JsonProperty("source")
    String source,

    /**
     * Fine-grained event type within the source.
     * Examples: GITHUB_PUSH, GITHUB_PR_OPENED, GMAIL_MESSAGE_RECEIVED, JOB_LISTING_INGESTED
     */
    @JsonProperty("event_type")
    String eventType,

    /**
     * Arbitrary source-specific payload.  The workflow engine passes this through
     * untouched into {@code WorkflowExecution.triggerPayload}.
     */
    @JsonProperty("payload")
    Map<String, Object> payload,

    /** Wall-clock time the originating worker produced this event. */
    @JsonProperty("occurred_at")
    Instant occurredAt
) {
    /** Convenience factory with auto-generated event ID and current timestamp. */
    public static TriggerEvent of(UUID userId, String source, String eventType, Map<String, Object> payload) {
        return new TriggerEvent(
            UUID.randomUUID().toString(),
            userId,
            source,
            eventType,
            payload,
            Instant.now()
        );
    }
}