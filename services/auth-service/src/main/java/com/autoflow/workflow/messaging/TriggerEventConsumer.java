package com.autoflow.workflow.messaging;

import com.autoflow.workflow.messaging.event.TriggerEvent;
import com.autoflow.workflow.service.TriggerEvaluatorService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TriggerEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TriggerEventConsumer.class);

    private final TriggerEvaluatorService evaluatorService;

    public TriggerEventConsumer(TriggerEvaluatorService evaluatorService) {
        this.evaluatorService = evaluatorService;
    }

    /**
     * Consumes from {@code workflow.trigger.events}.
     *
     * Partition key is {@code user_id}, so all events for a given user arrive
     * on the same partition in order.  Manual acknowledgment (MANUAL_IMMEDIATE)
     * means we only commit the offset once the evaluator has durably persisted
     * the WorkflowExecution row — if the service throws, the message is redelivered.
     *
     * concurrency = "6" matches the 6 partitions defined in create-topics.sh.
     */
    @KafkaListener(
        topics     = "${autoflow.kafka.topics.trigger-events:workflow.trigger.events}",
        groupId    = "${autoflow.kafka.consumer-groups.workflow-engine:workflow-engine-group}",
        concurrency = "6",
        containerFactory = "manualAckListenerContainerFactory"
    )
    public void onTriggerEvent(ConsumerRecord<String, TriggerEvent> record, Acknowledgment ack) {
        TriggerEvent event = record.value();

        log.info(
            "Received trigger event [eventId={}, source={}, eventType={}, userId={}, partition={}, offset={}]",
            event.eventId(), event.source(), event.eventType(), event.userId(),
            record.partition(), record.offset()
        );

        try {
            int matchedWorkflows = evaluatorService.evaluate(event);

            log.info(
                "Trigger evaluation complete [eventId={}, matchedWorkflows={}]",
                event.eventId(), matchedWorkflows
            );

            ack.acknowledge();

        } catch (Exception ex) {
            /*
             * Do NOT acknowledge — let Kafka redeliver.
             * The evaluator is idempotent (WorkflowExecution rows are keyed on
             * correlationId), so redelivery is safe.
             */
            log.error(
                "Trigger evaluation failed — will redeliver [eventId={}, eventType={}]",
                event.eventId(), event.eventType(), ex
            );
            // Intentionally not calling ack.acknowledge()
        }
    }
}