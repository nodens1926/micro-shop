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
public class OrderServiceImpl implements OrderService {// Бизнес-логика, работа с БД, вызов склада через Feign, отправка событий в Kafka и обработка ошибок через ExceptionHandler

    private final OrderRepository orderRepository;// Работа с БД
    private final WarehouseClient warehouseClient;// Feign клиент для вызова АПИ микросервиса
    private final KafkaEventPublisher kafkaEventPublisher;// Отправка событий в кафка

    @Override
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());

        // Сначала резервируем товары (синхронный HTTP-вызов)
        WarehouseReserveRequest reserveRequest = buildWarehouseRequest(request);
        try {
            warehouseClient.reserve(reserveRequest);
            log.info("Warehouse reservation successful");
        } catch (FeignException e) {
            if (e.status() == 400) {
                log.error("Warehouse conflict: not enough stock");
                throw new WarehouseConflictException("Not enough stock");
            }
            log.error("Warehouse service unavailable", e);
            throw new RuntimeException("Warehouse service unavailable", e);
        }

        // Если резервирование успешно, создаём и сохраняем заказ (в отдельной транзакции)
        Order order = mapToOrder(request);
        order.setStatus(OrderStatus.CONFIRMED); // сразу CONFIRMED, т.к. резерв уже есть

        Order savedOrder = saveOrderInTransaction(order);
        log.info("Order saved with status CONFIRMED, id: {}", savedOrder.getId());

        // Отправляем события в Kafka
        kafkaEventPublisher.publishOrderEvents(savedOrder);
        log.info("Kafka events published for order: {}", savedOrder.getId());

        return mapToOrderResponse(savedOrder);
    }

    @Transactional
    protected Order saveOrderInTransaction(Order order) {
        return orderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {// Получнеие заказа по айди
        log.info("Fetching order by id: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + id));
        return mapToOrderResponse(order);// Преобразование  Order в OrderResponse
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId) {// Поиск заказов покупателя по айди
        log.info("Fetching orders for customer: {}", customerId);
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    private Order mapToOrder(OrderRequest request) {// ДТО - Энтити
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

    private void calculateTotalAmount(Order order) {// Подсчет общей суммы заказа
        BigDecimal total = order.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
    }

    private WarehouseReserveRequest buildWarehouseRequest(OrderRequest request) {// Запрос к складу
        // Для каждой части заказа из OrderRequest создает ReserveItem(productId + quantity), собирает в ДТО и возвращает
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

    private OrderResponse mapToOrderResponse(Order order) {// Энтити в ДТО
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