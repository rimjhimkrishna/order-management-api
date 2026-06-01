package com.example.order.service.impl;

import com.example.order.dto.request.OrderItemRequest;
import com.example.order.dto.request.OrderRequest;
import com.example.order.dto.request.UpdateStatusRequest;
import com.example.order.dto.response.OrderEvent;
import com.example.order.dto.response.OrderResponse;
import com.example.order.exception.InsufficientStockException;
import com.example.order.exception.InvalidStatusTransitionException;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.kafka.producer.OrderEventProducer;
import com.example.order.model.*;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.ProductRepository;
import com.example.order.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderServiceImpl}.
 * Covers order placement with stock validation, status transitions,
 * cancellation with stock restoration, and Kafka event publishing.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderEventProducer orderEventProducer;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User testUser;
    private Product testProduct;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encoded_password")
                .role(Role.ROLE_USER)
                .createdAt(LocalDateTime.now())
                .build();

        testProduct = Product.builder()
                .id(1L)
                .name("Wireless Mouse")
                .description("Ergonomic wireless mouse")
                .price(BigDecimal.valueOf(29.99))
                .stockQuantity(100)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        OrderItem orderItem = OrderItem.builder()
                .id(1L)
                .product(testProduct)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(29.99))
                .build();

        testOrder = Order.builder()
                .id(1L)
                .user(testUser)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(59.98))
                .orderItems(new ArrayList<>(List.of(orderItem)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        orderItem.setOrder(testOrder);
    }

    // ========================
    // Place Order Tests
    // ========================

    @Nested
    @DisplayName("Place Order")
    class PlaceOrderTests {

        @Test
        @DisplayName("Should place order successfully and deduct stock")
        void shouldPlaceOrderSuccessfully() {
            // Arrange
            OrderItemRequest itemRequest = OrderItemRequest.builder()
                    .productId(1L)
                    .quantity(5)
                    .build();

            OrderRequest orderRequest = OrderRequest.builder()
                    .items(List.of(itemRequest))
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                order.setCreatedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                return order;
            });

            // Act
            OrderResponse response = orderService.placeOrder(orderRequest, "testuser");

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(testProduct.getStockQuantity()).isEqualTo(95); // 100 - 5

            // Verify stock was saved
            verify(productRepository).save(testProduct);

            // Verify Kafka event was published
            ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
            verify(orderEventProducer).sendOrderCreatedEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo("ORDER_CREATED");
            assertThat(eventCaptor.getValue().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should throw InsufficientStockException when stock is not enough")
        void shouldThrowInsufficientStockException() {
            // Arrange
            testProduct.setStockQuantity(3); // Only 3 items in stock

            OrderItemRequest itemRequest = OrderItemRequest.builder()
                    .productId(1L)
                    .quantity(10) // Requesting 10
                    .build();

            OrderRequest orderRequest = OrderRequest.builder()
                    .items(List.of(itemRequest))
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

            // Act & Assert
            assertThatThrownBy(() -> orderService.placeOrder(orderRequest, "testuser"))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock")
                    .hasMessageContaining("Wireless Mouse");

            // Verify order was never saved
            verify(orderRepository, never()).save(any(Order.class));
            // Verify no Kafka event was published
            verify(orderEventProducer, never()).sendOrderCreatedEvent(any());
        }

        @Test
        @DisplayName("Should calculate total amount correctly for multiple items")
        void shouldCalculateTotalAmountCorrectly() {
            // Arrange
            Product product2 = Product.builder()
                    .id(2L)
                    .name("Keyboard")
                    .price(BigDecimal.valueOf(49.99))
                    .stockQuantity(50)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            OrderItemRequest item1 = OrderItemRequest.builder().productId(1L).quantity(2).build();
            OrderItemRequest item2 = OrderItemRequest.builder().productId(2L).quantity(3).build();

            OrderRequest orderRequest = OrderRequest.builder()
                    .items(List.of(item1, item2))
                    .build();

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            when(productRepository.findById(2L)).thenReturn(Optional.of(product2));
            when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                order.setCreatedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                return order;
            });

            // Act
            OrderResponse response = orderService.placeOrder(orderRequest, "testuser");

            // Assert — (2 * 29.99) + (3 * 49.99) = 59.98 + 149.97 = 209.95
            BigDecimal expectedTotal = BigDecimal.valueOf(29.99).multiply(BigDecimal.valueOf(2))
                    .add(BigDecimal.valueOf(49.99).multiply(BigDecimal.valueOf(3)));
            assertThat(response.getTotalAmount()).isEqualByComparingTo(expectedTotal);
        }
    }

    // ========================
    // Cancel Order Tests
    // ========================

    @Nested
    @DisplayName("Cancel Order")
    class CancelOrderTests {

        @Test
        @DisplayName("Should cancel order and restore product stock")
        void shouldCancelOrderAndRestoreStock() {
            // Arrange
            int originalStock = testProduct.getStockQuantity();
            int orderedQuantity = testOrder.getOrderItems().get(0).getQuantity();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            OrderResponse response = orderService.cancelOrder(1L);

            // Assert
            assertThat(response.getStatus()).isEqualTo("CANCELLED");
            assertThat(testProduct.getStockQuantity()).isEqualTo(originalStock + orderedQuantity);

            // Verify Kafka cancelled event
            ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
            verify(orderEventProducer).sendOrderCancelledEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo("ORDER_CANCELLED");
        }

        @Test
        @DisplayName("Should throw exception when cancelling a SHIPPED order")
        void shouldThrowExceptionWhenCancellingShippedOrder() {
            // Arrange
            testOrder.setStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("SHIPPED");

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("Should throw exception when cancelling a DELIVERED order")
        void shouldThrowExceptionWhenCancellingDeliveredOrder() {
            // Arrange
            testOrder.setStatus(OrderStatus.DELIVERED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("DELIVERED");
        }

        @Test
        @DisplayName("Should throw exception when cancelling an already CANCELLED order")
        void shouldThrowExceptionWhenCancellingAlreadyCancelledOrder() {
            // Arrange
            testOrder.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("already cancelled");
        }

        @Test
        @DisplayName("Should throw OrderNotFoundException for non-existent order")
        void shouldThrowOrderNotFoundExceptionOnCancel() {
            // Arrange
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.cancelOrder(999L))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ========================
    // Update Order Status Tests
    // ========================

    @Nested
    @DisplayName("Update Order Status")
    class UpdateOrderStatusTests {

        @Test
        @DisplayName("Should transition PENDING → CONFIRMED successfully")
        void shouldTransitionPendingToConfirmed() {
            // Arrange
            testOrder.setStatus(OrderStatus.PENDING);
            UpdateStatusRequest request = UpdateStatusRequest.builder().status("CONFIRMED").build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            OrderResponse response = orderService.updateOrderStatus(1L, request);

            // Assert
            assertThat(response.getStatus()).isEqualTo("CONFIRMED");

            ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
            verify(orderEventProducer).sendOrderStatusUpdatedEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo("STATUS_UPDATED");
        }

        @Test
        @DisplayName("Should transition CONFIRMED → SHIPPED successfully")
        void shouldTransitionConfirmedToShipped() {
            // Arrange
            testOrder.setStatus(OrderStatus.CONFIRMED);
            UpdateStatusRequest request = UpdateStatusRequest.builder().status("SHIPPED").build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            OrderResponse response = orderService.updateOrderStatus(1L, request);

            // Assert
            assertThat(response.getStatus()).isEqualTo("SHIPPED");
        }

        @Test
        @DisplayName("Should transition SHIPPED → DELIVERED successfully")
        void shouldTransitionShippedToDelivered() {
            // Arrange
            testOrder.setStatus(OrderStatus.SHIPPED);
            UpdateStatusRequest request = UpdateStatusRequest.builder().status("DELIVERED").build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            OrderResponse response = orderService.updateOrderStatus(1L, request);

            // Assert
            assertThat(response.getStatus()).isEqualTo("DELIVERED");
        }

        @Test
        @DisplayName("Should reject invalid transition PENDING → SHIPPED")
        void shouldRejectPendingToShipped() {
            // Arrange
            testOrder.setStatus(OrderStatus.PENDING);
            UpdateStatusRequest request = UpdateStatusRequest.builder().status("SHIPPED").build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, request))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("PENDING")
                    .hasMessageContaining("SHIPPED");
        }

        @Test
        @DisplayName("Should reject invalid transition PENDING → DELIVERED")
        void shouldRejectPendingToDelivered() {
            // Arrange
            testOrder.setStatus(OrderStatus.PENDING);
            UpdateStatusRequest request = UpdateStatusRequest.builder().status("DELIVERED").build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, request))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        @DisplayName("Should reject any status change on a DELIVERED order")
        void shouldRejectStatusChangeOnDeliveredOrder() {
            // Arrange
            testOrder.setStatus(OrderStatus.DELIVERED);
            UpdateStatusRequest request = UpdateStatusRequest.builder().status("CONFIRMED").build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, request))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("DELIVERED");
        }

        @Test
        @DisplayName("Should reject any status change on a CANCELLED order")
        void shouldRejectStatusChangeOnCancelledOrder() {
            // Arrange
            testOrder.setStatus(OrderStatus.CANCELLED);
            UpdateStatusRequest request = UpdateStatusRequest.builder().status("CONFIRMED").build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

            // Act & Assert
            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, request))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("Should handle status cancellation via updateOrderStatus and restore stock")
        void shouldHandleCancellationViaStatusUpdateAndRestoreStock() {
            // Arrange
            int originalStock = testProduct.getStockQuantity();
            int orderedQuantity = testOrder.getOrderItems().get(0).getQuantity();

            testOrder.setStatus(OrderStatus.PENDING);
            UpdateStatusRequest request = UpdateStatusRequest.builder().status("CANCELLED").build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            OrderResponse response = orderService.updateOrderStatus(1L, request);

            // Assert
            assertThat(response.getStatus()).isEqualTo("CANCELLED");
            assertThat(testProduct.getStockQuantity()).isEqualTo(originalStock + orderedQuantity);
            verify(orderEventProducer).sendOrderCancelledEvent(any());
        }
    }

    // ========================
    // Get Order Tests
    // ========================

    @Nested
    @DisplayName("Get Orders")
    class GetOrderTests {

        @Test
        @DisplayName("Should return order by ID")
        void shouldReturnOrderById() {
            // Arrange
            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

            // Act
            OrderResponse response = orderService.getOrderById(1L);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should throw OrderNotFoundException for invalid ID")
        void shouldThrowOrderNotFoundException() {
            // Arrange
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> orderService.getOrderById(999L))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("Should return all orders")
        void shouldReturnAllOrders() {
            // Arrange
            when(orderRepository.findAll()).thenReturn(List.of(testOrder));

            // Act
            List<OrderResponse> responses = orderService.getAllOrders();

            // Assert
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getUsername()).isEqualTo("testuser");
        }
    }
}
