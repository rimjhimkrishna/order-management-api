package com.example.order.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class KafkaConfig {

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_STATUS_UPDATED_TOPIC = "order.status.updated";
    public static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    public static final String ORDER_EVENTS_DLT = "order.events.DLT";

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(ORDER_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderStatusUpdatedTopic() {
        return TopicBuilder.name(ORDER_STATUS_UPDATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(ORDER_CANCELLED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead Letter Topic for failed messages.
     * Messages that exhaust all retry attempts are published here for investigation.
     */
    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name(ORDER_EVENTS_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Configures error handling for Kafka consumers.
     * Failed messages are retried up to 3 times with a 1-second interval,
     * then forwarded to the Dead Letter Topic (DLT) for manual investigation.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[KAFKA RETRY] Attempt {} for topic '{}', key '{}': {}",
                        deliveryAttempt, record.topic(), record.key(), ex.getMessage()));

        return errorHandler;
    }
}
