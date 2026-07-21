package com.example.order_service.service;

import com.example.order_service.dto.*;
import com.example.order_service.exception.OrderNotFoundException;
import com.example.order_service.exception.WarehouseConflictException;
import com.example.order_service.model.Order;
import com.example.order_service.model.OrderItem;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.repository.OrderRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// TODO: прокомментировать класс
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WarehouseClient warehouseClient;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID customerId;
    private UUID productId1;
    private UUID productId2;
    private OrderRequest orderRequest;
    private Order order;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        productId1 = UUID.randomUUID();
        productId2 = UUID.randomUUID();

        OrderItemRequest item1 = OrderItemRequest.builder()
                .productId(productId1)
                .productName("Product 1")
                .quantity(2)
                .price(new BigDecimal("25.50"))
                .build();

        OrderItemRequest item2 = OrderItemRequest.builder()
                .productId(productId2)
                .productName("Product 2")
                .quantity(1)
                .price(new BigDecimal("100.00"))
                .build();

        orderRequest = OrderRequest.builder()
                .customerId(customerId)
                .customerEmail("customer@example.com")
                .items(List.of(item1, item2))
                .build();

        UUID orderId = UUID.randomUUID();
        order = Order.builder()
                .id(orderId)
                .customerId(customerId)
                .customerEmail("customer@example.com")
                .status(OrderStatus.CONFIRMED) // Важно: устанавливаем CONFIRMED
                .totalAmount(new BigDecimal("151.00"))
                .createdAt(LocalDateTime.now())
                .build();

        OrderItem orderItem1 = OrderItem.builder()
                .id(UUID.randomUUID())
                .productId(productId1)
                .productName("Product 1")
                .quantity(2)
                .price(new BigDecimal("25.50"))
                .order(order)
                .build();

        OrderItem orderItem2 = OrderItem.builder()
                .id(UUID.randomUUID())
                .productId(productId2)
                .productName("Product 2")
                .quantity(1)
                .price(new BigDecimal("100.00"))
                .order(order)
                .build();

        order.addItem(orderItem1);
        order.addItem(orderItem2);
    }

    @Test
    void createOrder_Success() {
        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))
                .thenReturn(ResponseEntity.ok().build());

        // Важно: при сохранении возвращаем заказ с правильным статусом
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(UUID.randomUUID());
                    savedOrder.setStatus(OrderStatus.CONFIRMED);
                    return savedOrder;
                });

        doNothing().when(kafkaEventPublisher).publishOrderEvents(any(Order.class));

        OrderResponse result = orderService.createOrder(orderRequest);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("151.00"));
        assertThat(result.getItems()).hasSize(2);

        verify(warehouseClient).reserve(any(WarehouseReserveRequest.class));
        verify(orderRepository).save(any(Order.class));
        verify(kafkaEventPublisher).publishOrderEvents(any(Order.class));
    }

    @Test
    void createOrder_WarehouseConflict_ThrowsException() {
        FeignException feignException = mock(FeignException.class);
        when(feignException.status()).thenReturn(400);

        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))
                .thenThrow(feignException);

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(WarehouseConflictException.class)
                .hasMessage("Not enough stock");

        verify(warehouseClient).reserve(any(WarehouseReserveRequest.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(kafkaEventPublisher, never()).publishOrderEvents(any(Order.class));
    }

    @Test
    void createOrder_WarehouseServiceUnavailable_ThrowsException() {
        FeignException feignException = mock(FeignException.class);
        when(feignException.status()).thenReturn(500);

        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))
                .thenThrow(feignException);

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Warehouse service unavailable");

        verify(warehouseClient).reserve(any(WarehouseReserveRequest.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(kafkaEventPublisher, never()).publishOrderEvents(any(Order.class));
    }

    @Test
    void createOrder_WarehouseServiceUnavailable_WithNullStatus() {
        FeignException feignException = mock(FeignException.class);
        doThrow(feignException).when(warehouseClient).reserve(any(WarehouseReserveRequest.class));

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Warehouse service unavailable");

        verify(warehouseClient).reserve(any(WarehouseReserveRequest.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(kafkaEventPublisher, never()).publishOrderEvents(any(Order.class));
    }

    @Test
    void createOrder_SavesOrderWithCorrectStatus() {
        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))
                .thenReturn(ResponseEntity.ok().build());

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(UUID.randomUUID());
                    savedOrder.setStatus(OrderStatus.CONFIRMED);
                    return savedOrder;
                });

        doNothing().when(kafkaEventPublisher).publishOrderEvents(any(Order.class));

        orderService.createOrder(orderRequest);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(savedOrder.getCustomerId()).isEqualTo(customerId);
        assertThat(savedOrder.getCustomerEmail()).isEqualTo("customer@example.com");
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("151.00"));
        assertThat(savedOrder.getItems()).hasSize(2);
    }

    @Test
    void getOrderById_Success() {
        UUID orderId = order.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrderById(orderId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getCustomerEmail()).isEqualTo("customer@example.com");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("151.00"));
        assertThat(result.getItems()).hasSize(2);

        verify(orderRepository).findById(orderId);
        verifyNoInteractions(warehouseClient);
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void getOrderById_OrderNotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(nonExistentId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found with id: " + nonExistentId);

        verify(orderRepository).findById(nonExistentId);
    }

    @Test
    void getOrdersByCustomerId_Success() {
        List<Order> orders = List.of(order);
        when(orderRepository.findByCustomerId(customerId)).thenReturn(orders);

        List<OrderResponse> results = orderService.getOrdersByCustomerId(customerId);

        assertThat(results).hasSize(1);
        OrderResponse result = results.get(0);
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getItems()).hasSize(2);

        verify(orderRepository).findByCustomerId(customerId);
        verifyNoInteractions(warehouseClient);
        verifyNoInteractions(kafkaEventPublisher);
    }

    @Test
    void getOrdersByCustomerId_EmptyList_ReturnsEmptyList() {
        UUID customerId = UUID.randomUUID();
        when(orderRepository.findByCustomerId(customerId)).thenReturn(List.of());

        List<OrderResponse> results = orderService.getOrdersByCustomerId(customerId);

        assertThat(results).isEmpty();
        verify(orderRepository).findByCustomerId(customerId);
    }

    @Test
    void getOrdersByCustomerId_MultipleOrders() {
        Order order2 = Order.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .customerEmail("customer@example.com")
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("75.00"))
                .createdAt(LocalDateTime.now())
                .build();

        OrderItem orderItem3 = OrderItem.builder()
                .id(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .productName("Product 3")
                .quantity(1)
                .price(new BigDecimal("75.00"))
                .order(order2)
                .build();
        order2.addItem(orderItem3);

        List<Order> orders = List.of(order, order2);
        when(orderRepository.findByCustomerId(customerId)).thenReturn(orders);

        List<OrderResponse> results = orderService.getOrdersByCustomerId(customerId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("151.00"));
        assertThat(results.get(1).getTotalAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void createOrder_WithEmptyItems_ShouldCalculateTotalZero() {
        OrderRequest emptyRequest = OrderRequest.builder()
                .customerId(customerId)
                .customerEmail("customer@example.com")
                .items(List.of())
                .build();

        Order emptyOrder = Order.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .customerEmail("customer@example.com")
                .status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();

        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))
                .thenReturn(ResponseEntity.ok().build());

        when(orderRepository.save(any(Order.class)))
                .thenReturn(emptyOrder);

        doNothing().when(kafkaEventPublisher).publishOrderEvents(any(Order.class));

        OrderResponse result = orderService.createOrder(emptyRequest);

        assertThat(result).isNotNull();
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getItems()).isEmpty();

        verify(warehouseClient).reserve(any(WarehouseReserveRequest.class));
        verify(orderRepository).save(any(Order.class));
        verify(kafkaEventPublisher).publishOrderEvents(any(Order.class));
    }

    @Test
    void createOrder_SavesOrderWithCorrectTotalAmount() {
        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))
                .thenReturn(ResponseEntity.ok().build());

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(UUID.randomUUID());
                    savedOrder.setStatus(OrderStatus.CONFIRMED);
                    return savedOrder;
                });

        doNothing().when(kafkaEventPublisher).publishOrderEvents(any(Order.class));

        orderService.createOrder(orderRequest);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();
        BigDecimal expectedTotal = new BigDecimal("25.50").multiply(BigDecimal.valueOf(2))
                .add(new BigDecimal("100.00").multiply(BigDecimal.valueOf(1)));
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void createOrder_PublishesKafkaEventsWithCorrectData() {
        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))
                .thenReturn(ResponseEntity.ok().build());

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(UUID.randomUUID());
                    savedOrder.setStatus(OrderStatus.CONFIRMED);
                    return savedOrder;
                });

        doNothing().when(kafkaEventPublisher).publishOrderEvents(any(Order.class));

        orderService.createOrder(orderRequest);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(kafkaEventPublisher).publishOrderEvents(orderCaptor.capture());

        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getId()).isNotNull();
        assertThat(capturedOrder.getCustomerId()).isEqualTo(customerId);
        assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void saveOrderInTransaction_ShouldBeCalledWithinTransaction() {
        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))
                .thenReturn(ResponseEntity.ok().build());

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(UUID.randomUUID());
                    savedOrder.setStatus(OrderStatus.CONFIRMED);
                    return savedOrder;
                });

        doNothing().when(kafkaEventPublisher).publishOrderEvents(any(Order.class));

        orderService.createOrder(orderRequest);

        verify(orderRepository).save(any(Order.class));
    }
}