package com.example.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {
    private Long orderId;
    private String eventType; // "ORDER_CREATED", "STATUS_UPDATED", "ORDER_CANCELLED"
    private String status;
    private BigDecimal totalAmount;
    private String username;
    private LocalDateTime timestamp;
}
