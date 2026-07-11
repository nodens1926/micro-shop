package com.example.order_service.service;

import com.example.order_service.dto.*;
import com.example.order_service.exception.OrderNotFoundException;
import com.example.order_service.exception.WarehouseConflictException;
import com.example.order_service.model.Order;
import com.example.order_service.model.OrderItem;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.repository.OrderRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final WarehouseClient warehouseClient;
    private final KafkaEventPublisher kafkaEventPublisher;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());

        Order order = mapToOrder(request);

        Order savedOrder = orderRepository.save(order);
        log.info("Order saved with status PENDING, id: {}", savedOrder.getId());

        WarehouseReserveRequest reserveRequest = buildWarehouseRequest(request);

        try {
            log.info("Calling Warehouse service to reserve products for order: {}", savedOrder.getId());
            warehouseClient.reserve(reserveRequest);

            savedOrder.setStatus(OrderStatus.CONFIRMED);
            savedOrder = orderRepository.save(savedOrder);
            log.info("Order confirmed, id: {}", savedOrder.getId());

            kafkaEventPublisher.publishOrderEvents(savedOrder);
            log.info("Kafka events published for order: {}", savedOrder.getId());

        } catch (FeignException e) {
            if (e.status() == 400) {
                log.error("Warehouse conflict for order: {}, status: {}", savedOrder.getId(), e.status());
                savedOrder.setStatus(OrderStatus.CANCELED);
                orderRepository.save(savedOrder);
                throw new WarehouseConflictException("Not enough stock for order: " + savedOrder.getId());
            }
            log.error("Error calling Warehouse service for order: {}", savedOrder.getId(), e);
            throw new RuntimeException("Warehouse service unavailable", e);
        }

        return mapToOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        log.info("Fetching order by id: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId) {
        log.info("Fetching orders for customer: {}", customerId);
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }


    private Order mapToOrder(OrderRequest request) {
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        request.getItems().forEach(itemRequest -> {
            OrderItem item = OrderItem.builder()
                    .productId(itemRequest.getProductId())
                    .productName(itemRequest.getProductName())
                    .quantity(itemRequest.getQuantity())
                    .price(itemRequest.getPrice())
                    .build();
            order.addItem(item);
        });

        calculateTotalAmount(order);
        return order;
    }

    private void calculateTotalAmount(Order order) {
        BigDecimal total = order.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
    }

    private WarehouseReserveRequest buildWarehouseRequest(OrderRequest request) {
        List<WarehouseReserveRequest.ReserveItem> reserveItems = request.getItems().stream()
                .map(item -> WarehouseReserveRequest.ReserveItem.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());
        return WarehouseReserveRequest.builder()
                .items(reserveItems)
                .build();
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}