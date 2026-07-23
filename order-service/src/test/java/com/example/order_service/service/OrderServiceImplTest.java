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

// включение поддержки Мокито
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;// создает мок БД

    @Mock
    private WarehouseClient warehouseClient;// мок фейн-клиента

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;// мок кафки

    @InjectMocks
    private OrderServiceImpl orderService;// реальный обьект, с внедрением фальшивых зависимостей сверху

    private UUID customerId;
    private UUID productId1;
    private UUID productId2;
    private OrderRequest orderRequest;
    private Order order;

    @BeforeEach
    void setUp() {// заполнение тестовых данных
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
    void createOrder_Success() {// создние заказа
        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))// когда вызываем фейн-клиент - отвечаем 200
                .thenReturn(ResponseEntity.ok().build());// thenReturn - возвращаем обьект

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {// вместо возврата ответа - выполняем следующую логику
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(UUID.randomUUID());
                    savedOrder.setStatus(OrderStatus.CONFIRMED);
                    return savedOrder;
                });

        doNothing().when(kafkaEventPublisher).publishOrderEvents(any(Order.class));// не делаем ничего когда вызываем кафку

        OrderResponse result = orderService.createOrder(orderRequest);// вызов самого тестируемого метода

        assertThat(result).isNotNull();// проверка что ответ существует
        assertThat(result.getCustomerId()).isEqualTo(customerId);// проверка что result содержит customerId который мы используем в orderRequest
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);// CONFIRMED мы ставим в when сверху
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("151.00"));// проверка суммы частей заказа
        assertThat(result.getItems()).hasSize(2);// проверка что содержит item1 и item2 которые мы добавили в orderRequest

        verify(warehouseClient).reserve(any(WarehouseReserveRequest.class));// проверяем что метод reserve был вызван ровно 1 раз
        verify(orderRepository).save(any(Order.class));// тож самое
        verify(kafkaEventPublisher).publishOrderEvents(any(Order.class));// тож самое
    }

    @Test
    void createOrder_WarehouseConflict_ThrowsException() {// ошибка склада 400(чекает что будет если склад отвечает Bad Request), типо переполнен и тп
        FeignException feignException = mock(FeignException.class);// мок фейн-ошибки
        when(feignException.status()).thenReturn(400);// настраиваем чтобы метод status() ошибки возвращал 400

        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))// когда вызовут метод reserve у WarehouseClient - выбрасываем мок ошибки
                .thenThrow(feignException);

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))// проверка что ошибка выбрасывается при попытке вызвать метод createOrder
                .isInstanceOf(WarehouseConflictException.class)
                .hasMessage("Not enough stock");

        verify(warehouseClient).reserve(any(WarehouseReserveRequest.class));// чекаем чтобы был вызван метод фейн клиента
        verify(orderRepository, never()).save(any(Order.class));// чекаем что метод сохранения в БД никогде не был вызван
        verify(kafkaEventPublisher, never()).publishOrderEvents(any(Order.class));// чекаем что кафка ниче не делала тоже
    }

    @Test
    void createOrder_WarehouseServiceUnavailable_ThrowsException() {// склад недоступен (500), метод почти аналогичем предыдущему
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
    void createOrder_WarehouseServiceUnavailable_WithNullStatus() {// проверка обработки null методом createOrder
        FeignException feignException = mock(FeignException.class);
        doThrow(feignException).when(warehouseClient).reserve(any(WarehouseReserveRequest.class));// бросаем исключение если нет обработки статуса

        assertThatThrownBy(() -> orderService.createOrder(orderRequest))// ожидаем RuntimeException с тем же сообщением
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Warehouse service unavailable");

        verify(warehouseClient).reserve(any(WarehouseReserveRequest.class));// проверяем что метод фейн-клиента был вызван 1 раз
        verify(orderRepository, never()).save(any(Order.class));// проверяем что метод БД не был вызван
        verify(kafkaEventPublisher, never()).publishOrderEvents(any(Order.class));// проверяем что кафка не была вызвана
    }

    @Test
    void createOrder_SavesOrderWithCorrectStatus() {// проверка статуса
        when(warehouseClient.reserve(any(WarehouseReserveRequest.class)))
                .thenReturn(ResponseEntity.ok().build());// когда вызываем фейн-клиент возвращаем 200

        when(orderRepository.save(any(Order.class)))// когда вызываем save в БД -> проставляем ID и статус
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setId(UUID.randomUUID());
                    savedOrder.setStatus(OrderStatus.CONFIRMED);
                    return savedOrder;
                });

        doNothing().when(kafkaEventPublisher).publishOrderEvents(any(Order.class));// ничего не делаем когда вызываем кафку

        orderService.createOrder(orderRequest);// вызываем createOrder

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);// ловушка для аргументов - захватываем заказ
        verify(orderRepository).save(orderCaptor.capture());// проверяем что тот же заказ был передан в методе save()

        Order savedOrder = orderCaptor.getValue();//  getValue() - достает последний перехваченный обьект
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);// сравниваем статусы
        assertThat(savedOrder.getCustomerId()).isEqualTo(customerId);// сравниваем айди
        assertThat(savedOrder.getCustomerEmail()).isEqualTo("customer@example.com");// сравниваем почту
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("151.00"));// сравниваем сумму частей заказа
        assertThat(savedOrder.getItems()).hasSize(2);// сравниваем количество частей заказа
    }

    @Test
    void getOrderById_Success() {// получение заказа
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
    void getOrderById_OrderNotFound_ThrowsException() {// заказ не найден
        UUID nonExistentId = UUID.randomUUID();
        when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(nonExistentId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found with id: " + nonExistentId);

        verify(orderRepository).findById(nonExistentId);
    }

    @Test
    void getOrdersByCustomerId_Success() {// поиск заказов по айди пользователя
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
    void getOrdersByCustomerId_EmptyList_ReturnsEmptyList() {// нет заказов
        UUID customerId = UUID.randomUUID();
        when(orderRepository.findByCustomerId(customerId)).thenReturn(List.of());

        List<OrderResponse> results = orderService.getOrdersByCustomerId(customerId);

        assertThat(results).isEmpty();
        verify(orderRepository).findByCustomerId(customerId);
    }

    @Test
    void getOrdersByCustomerId_MultipleOrders() {// поиск нескольких закзов по айди пользователя
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
    void createOrder_WithEmptyItems_ShouldCalculateTotalZero() {// создание заказа с пустым списком частей заказа
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
    void createOrder_SavesOrderWithCorrectTotalAmount() {// создание заказа, проверка на корректность расчета суммы
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
    void createOrder_PublishesKafkaEventsWithCorrectData() {// проверка кафки при создании заказа
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
    void saveOrderInTransaction_ShouldBeCalledWithinTransaction() {// проверка транзакции???
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