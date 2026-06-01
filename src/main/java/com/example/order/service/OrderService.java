package com.example.order.service;

import com.example.order.dto.request.OrderRequest;
import com.example.order.dto.request.UpdateStatusRequest;
import com.example.order.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {
    List<OrderResponse> getAllOrders();
    OrderResponse getOrderById(Long id);
    OrderResponse placeOrder(OrderRequest request, String username);
    OrderResponse updateOrderStatus(Long id, UpdateStatusRequest request);
    OrderResponse cancelOrder(Long id);
}
