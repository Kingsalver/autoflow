package com.autoflow.workflow.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import com.autoflow.workflow.messaging.event.TriggerEvent;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Consumer factory for TriggerEvent messages.
     * Trusted package set to our kafka package so JsonDeserializer
     * will deserialise the inbound JSON without rejecting the type.
     */
    @Bean
    public ConsumerFactory<String, TriggerEvent> triggerEventConsumerFactory() {
        JsonDeserializer<TriggerEvent> deserializer = new JsonDeserializer<>(TriggerEvent.class);
        deserializer.addTrustedPackages("com.autoflow.workflow.kafka");
        deserializer.setUseTypeHeaders(false);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "workflow-engine");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // manual ack

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    /**
     * Listener container factory with MANUAL ack mode.
     * The consumer calls ack.acknowledge() only after successful processing,
     * so a crash mid-execution will replay the event on restart.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TriggerEvent>
    manualAckListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, TriggerEvent>();
        factory.setConsumerFactory(triggerEventConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}