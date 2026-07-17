package com.example.order_service.service;

import com.example.order_service.dto.OrderRequest;
import com.example.order_service.dto.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {// Это контракт для OrderServiceImpl

    OrderResponse createOrder(OrderRequest request);

    OrderResponse getOrderById(UUID id);

    List<OrderResponse> getOrdersByCustomerId(UUID customerId);
}