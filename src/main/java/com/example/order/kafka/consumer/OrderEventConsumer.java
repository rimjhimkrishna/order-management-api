package com.example.order.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class OrderEventConsumer {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @KafkaListener(topics = {"order.created", "order.status.updated", "order.cancelled"}, groupId = "order-group")
    public void consumeOrderEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String timestamp = LocalDateTime.now().format(formatter);
        log.info("[CONSUMER LOG] [{}] Received Kafka event on Topic: '{}', Partition: {}, Key: '{}', Payload: {}", 
                timestamp, topic, partition, key, eventJson);
    }

    /**
     * Handles messages that have been forwarded to the Dead Letter Topic (DLT)
     * after exhausting all retry attempts. These messages require manual investigation.
     */
    @DltHandler
    @KafkaListener(topics = {"order.events.DLT"}, groupId = "order-group-dlt")
    public void handleDltMessage(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        
        String timestamp = LocalDateTime.now().format(formatter);
        log.error("[DLT HANDLER] [{}] Message landed in Dead Letter Topic '{}', Key: '{}', Payload: {}. " +
                  "This message requires manual investigation.", 
                timestamp, topic, key, eventJson);
    }
}

