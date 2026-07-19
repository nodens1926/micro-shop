package com.example.order_service.service;

import com.example.order_service.dto.OrderItemRequest;
import com.example.order_service.dto.OrderRequest;
import com.example.order_service.dto.OrderResponse;
import com.example.order_service.dto.WarehouseReserveRequest;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)// Включает @Mock и @InjectMocks
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;// Мок репозитория

    @Mock
    private WarehouseClient warehouseClient;// Мок фейн-клиента

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;// Мок кафки

    @InjectMocks
    private OrderServiceImpl orderService;// Реальный сервис с фальшивыми зависимостями

    private UUID customerId;
    private UUID productId;
    private UUID orderId;
    private OrderRequest orderRequest;
    private Order order;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {// Заполнение тестовых данных
        customerId = UUID.randomUUID();
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        OrderItemRequest itemRequest = OrderItemRequest.builder()
                .productId(productId)
                .productName("Test Product")
                .quantity(2)
                .price(new BigDecimal("99.99"))
                .build();

        orderRequest = OrderRequest.builder()
                .customerId(customerId)
                .customerEmail("test@example.com")
                .items(List.of(itemRequest))
                .build();

        orderItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .productName("Test Product")
                .quantity(2)
                .price(new BigDecimal("99.99"))
                .build();

        order = Order.builder()
                .id(orderId)
                .customerId(customerId)
                .customerEmail("test@example.com")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("199.98"))
                .createdAt(LocalDateTime.now())
                .build();
        order.addItem(orderItem);
    }

    @Test
    void createOrder_Success() {// Успешное создание заказа
        // Настройка моков
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        doNothing().when(warehouseClient).reserve(any(WarehouseReserveRequest.class));// Если будет вызов фейн - ничего не делать
        doNothing().when(kafkaEventPublisher).publishOrderEvents(any(Order.class));// Если будет вызов кафки - ничгео не делать

        // Вызов тестируемого метода
        OrderResponse response = orderService.createOrder(orderRequest);

        // Проверка что все создалось правильно
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(orderId);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getCustomerEmail()).isEqualTo("test@example.com");
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("199.98"));
        assertThat(response.getItems()).hasSize(1);

        verify(orderRepository, times(2)).save(any(Order.class));// Проверка что репозиторий был вызван 2 раза
        verify(warehouseClient, times(1)).reserve(any(WarehouseReserveRequest.class));// Проверка что WarehouseClient был вызван 1 раз
        verify(kafkaEventPublisher, times(1)).publishOrderEvents(any(Order.class));// Проверка что кафка была вызвана 1 раз
    }

    @Test
    void createOrder_WarehouseConflict_ShouldCancelOrder() {// Ошибка склада (400)
        when(orderRepository.save(any(Order.class))).thenReturn(order);// Сохранение order
        FeignException feignException = mock(FeignException.class);// Создание фальшивого исключение
        when(feignException.status()).thenReturn(400);// Фальшивый статус код
        doThrow(feignException).when(warehouseClient).reserve(any(WarehouseReserveRequest.class));// Когда будет вызов WarehouseService выбрось исключение


        assertThatThrownBy(() -> orderService.createOrder(orderRequest))// Проверка что исключение выбросилось
                .isInstanceOf(WarehouseConflictException.class)
                .hasMessageContaining("Not enough stock");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);// Ловушка для класса
        verify(orderRepository, times(2)).save(orderCaptor.capture());// Проверяем что save вызвался 2 раза
        List<Order> savedOrders = orderCaptor.getAllValues();// Получаем все сохраненные заказы
        assertThat(savedOrders.get(1).getStatus()).isEqualTo(OrderStatus.CANCELED);// Чек что заказ отменился

        verify(kafkaEventPublisher, never()).publishOrderEvents(any(Order.class));// Чек что кафка никогда не вызывалась благодаря
    }

    @Test
    void createOrder_WarehouseUnavailable_ShouldThrowException() {// Ошибка сервера 500
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        FeignException feignException = mock(FeignException.class);
        when(feignException.status()).thenReturn(500);// Код 500 вместо 400
        doThrow(feignException).when(warehouseClient).reserve(any(WarehouseReserveRequest.class));// Выбросить исключение когда будет образение к WarehouseClient

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))
                .isInstanceOf(RuntimeException.class)// Чек что выброщено 500
                .hasMessage("Warehouse service unavailable");

        verify(kafkaEventPublisher, never()).publishOrderEvents(any(Order.class));// Кафка не вызывается
    }

    @Test
    void getOrderById_Success() {// Получение заказа
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));// Когда будет вызов по айди, верни Optional с order

        OrderResponse response = orderService.getOrderById(orderId);// Вызов

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(orderId);
        assertThat(response.getCustomerEmail()).isEqualTo("test@example.com");
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    void getOrderById_NotFound_ShouldThrowException() {// Заказ не найден
        UUID nonExistentId = UUID.randomUUID();
        when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());// Если будет вызов, возвращаем Optional с заказом

        assertThatThrownBy(() -> orderService.getOrderById(nonExistentId))// Чек что выкидывает ошибку
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found with id: " + nonExistentId);
    }

    @Test
    void getOrdersByCustomerId_Success() {// Заказы клиента
        List<Order> orders = List.of(order);
        when(orderRepository.findByCustomerId(customerId)).thenReturn(orders);

        List<OrderResponse> responses = orderService.getOrdersByCustomerId(customerId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCustomerId()).isEqualTo(customerId);
        assertThat(responses.get(0).getItems()).hasSize(1);
    }

    @Test
    void getOrdersByCustomerId_EmptyList_ShouldReturnEmptyList() {// Нет заказов
        UUID emptyCustomerId = UUID.randomUUID();
        when(orderRepository.findByCustomerId(emptyCustomerId)).thenReturn(List.of());

        List<OrderResponse> responses = orderService.getOrdersByCustomerId(emptyCustomerId);

        assertThat(responses).isEmpty();
    }
}