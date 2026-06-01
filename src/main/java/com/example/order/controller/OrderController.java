package com.example.order.controller;

import com.example.order.dto.request.OrderRequest;
import com.example.order.dto.request.UpdateStatusRequest;
import com.example.order.dto.response.ApiResponse;
import com.example.order.dto.response.OrderResponse;
import com.example.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order Controller", description = "Endpoints for placing, retrieving, transitioning status, and cancelling orders (JWT Protected)")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Get all orders", description = "Fetches all system orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getAllOrders() {
        List<OrderResponse> response = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success("Fetched all orders successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Fetches a specific order by ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success("Fetched order successfully", response));
    }

    @PostMapping
    @Operation(summary = "Place a new order", description = "Places a new order with stock validation and inventory deduction")
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @Valid @RequestBody OrderRequest request, 
            Authentication authentication) {
        String username = authentication.getName();
        OrderResponse response = orderService.placeOrder(request, username);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", response));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update order status", description = "Updates order state (PENDING -> CONFIRMED -> SHIPPED -> DELIVERED -> CANCELLED)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id, 
            @Valid @RequestBody UpdateStatusRequest request) {
        OrderResponse response = orderService.updateOrderStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel order", description = "Cancels an order and restores product stock levels")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long id) {
        OrderResponse response = orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", response));
    }
}
