package com.example.order.service.impl;

import com.example.order.dto.request.OrderItemRequest;
import com.example.order.dto.request.OrderRequest;
import com.example.order.dto.request.UpdateStatusRequest;
import com.example.order.dto.response.OrderEvent;
import com.example.order.dto.response.OrderItemResponse;
import com.example.order.dto.response.OrderResponse;
import com.example.order.exception.InsufficientStockException;
import com.example.order.exception.InvalidStatusTransitionException;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.exception.ProductNotFoundException;
import com.example.order.kafka.producer.OrderEventProducer;
import com.example.order.model.*;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.ProductRepository;
import com.example.order.repository.UserRepository;
import com.example.order.service.OrderService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderEventProducer orderEventProducer;

    public OrderServiceImpl(OrderRepository orderRepository,
                            ProductRepository productRepository,
                            UserRepository userRepository,
                            OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderEventProducer = orderEventProducer;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
        return mapToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(OrderRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .orderItems(new ArrayList<>())
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + itemRequest.getProductId()));

            // Stock Check
            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(String.format(
                        "Insufficient stock for product '%s'. Requested: %d, Available: %d",
                        product.getName(), itemRequest.getQuantity(), product.getStockQuantity()
                ));
            }

            // Deduct stock
            product.setStockQuantity(product.getStockQuantity() - itemRequest.getQuantity());
            productRepository.save(product);

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            order.addOrderItem(orderItem);
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(savedOrderInstance(order));

        OrderResponse response = mapToResponse(savedOrder);

        // Publish Order Created Event to Kafka
        OrderEvent event = OrderEvent.builder()
                .orderId(savedOrder.getId())
                .eventType("ORDER_CREATED")
                .status(savedOrder.getStatus().name())
                .totalAmount(savedOrder.getTotalAmount())
                .username(user.getUsername())
                .timestamp(LocalDateTime.now())
                .build();
        orderEventProducer.sendOrderCreatedEvent(event);

        return response;
    }

    // Helper to bypass lambda-related builder compilation issues if any
    private Order savedOrderInstance(Order order) {
        return order;
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long id, UpdateStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status provided. Valid states are: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED");
        }

        OrderStatus currentStatus = order.getStatus();

        // Validate state transitions
        if (currentStatus == OrderStatus.DELIVERED) {
            throw new InvalidStatusTransitionException("Cannot change status of a DELIVERED order");
        }
        if (currentStatus == OrderStatus.CANCELLED) {
            throw new InvalidStatusTransitionException("Cannot change status of a CANCELLED order");
        }

        boolean validTransition = false;
        switch (currentStatus) {
            case PENDING:
                if (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.CANCELLED) {
                    validTransition = true;
                }
                break;
            case CONFIRMED:
                if (newStatus == OrderStatus.SHIPPED || newStatus == OrderStatus.CANCELLED) {
                    validTransition = true;
                }
                break;
            case SHIPPED:
                if (newStatus == OrderStatus.DELIVERED) {
                    validTransition = true;
                }
                break;
        }

        if (!validTransition) {
            throw new InvalidStatusTransitionException(
                    String.format("Invalid status transition from %s to %s", currentStatus, newStatus)
            );
        }

        // If transitioning to CANCELLED, return product stock
        if (newStatus == OrderStatus.CANCELLED) {
            return cancelOrderInternal(order);
        }

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        OrderResponse response = mapToResponse(updatedOrder);

        // Publish Status Updated Event
        OrderEvent event = OrderEvent.builder()
                .orderId(updatedOrder.getId())
                .eventType("STATUS_UPDATED")
                .status(updatedOrder.getStatus().name())
                .totalAmount(updatedOrder.getTotalAmount())
                .username(updatedOrder.getUser().getUsername())
                .timestamp(LocalDateTime.now())
                .build();
        orderEventProducer.sendOrderStatusUpdatedEvent(event);

        return response;
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.SHIPPED || currentStatus == OrderStatus.DELIVERED) {
            throw new InvalidStatusTransitionException(
                    String.format("Cannot cancel an order that is already %s", currentStatus)
            );
        }
        if (currentStatus == OrderStatus.CANCELLED) {
            throw new InvalidStatusTransitionException("Order is already cancelled");
        }

        return cancelOrderInternal(order);
    }

    private OrderResponse cancelOrderInternal(Order order) {
        order.setStatus(OrderStatus.CANCELLED);

        // Return catalog stock
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        Order cancelledOrder = orderRepository.save(order);
        OrderResponse response = mapToResponse(cancelledOrder);

        // Publish Order Cancelled Event
        OrderEvent event = OrderEvent.builder()
                .orderId(cancelledOrder.getId())
                .eventType("ORDER_CANCELLED")
                .status(cancelledOrder.getStatus().name())
                .totalAmount(cancelledOrder.getTotalAmount())
                .username(cancelledOrder.getUser().getUsername())
                .timestamp(LocalDateTime.now())
                .build();
        orderEventProducer.sendOrderCancelledEvent(event);

        return response;
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .username(order.getUser().getUsername())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
