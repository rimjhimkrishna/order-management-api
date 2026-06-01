package com.example.order.kafka.producer;

import com.example.order.config.KafkaConfig;
import com.example.order.dto.response.OrderEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendOrderCreatedEvent(OrderEvent event) {
        sendEvent(KafkaConfig.ORDER_CREATED_TOPIC, event);
    }

    public void sendOrderStatusUpdatedEvent(OrderEvent event) {
        sendEvent(KafkaConfig.ORDER_STATUS_UPDATED_TOPIC, event);
    }

    public void sendOrderCancelledEvent(OrderEvent event) {
        sendEvent(KafkaConfig.ORDER_CANCELLED_TOPIC, event);
    }

    private void sendEvent(String topic, OrderEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            log.info("Sending Kafka event to topic {}: {}", topic, message);
            kafkaTemplate.send(topic, String.valueOf(event.getOrderId()), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to deliver message [key={}] to topic [{}]: {}", 
                                    event.getOrderId(), topic, ex.getMessage());
                        } else {
                            log.info("Successfully sent message to topic [{}] partition [{}] offset [{}]",
                                    topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Error serializing OrderEvent: {}", e.getMessage(), e);
        }
    }
}
