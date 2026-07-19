package com.example.order_service.controller;

import com.example.order_service.dto.OrderItemRequest;
import com.example.order_service.dto.OrderItemResponse;
import com.example.order_service.dto.OrderRequest;
import com.example.order_service.dto.OrderResponse;
import com.example.order_service.exception.OrderNotFoundException;
import com.example.order_service.exception.WarehouseConflictException;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)// Загружает слой веб(контроллеры), но не загружает полный контекст Спринг
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;// Для отправки HTTP запросов

    @Autowired
    private ObjectMapper objectMapper;// Для сериализации/десериализации JSON

    @MockitoBean
    private OrderService orderService;// Мок сервиса

    private UUID orderId;// Тестовые данные
    private UUID customerId;
    private UUID productId;
    private OrderRequest orderRequest;
    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {// Заполнение тестовых данных
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        productId = UUID.randomUUID();

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

        OrderItemResponse itemResponse = OrderItemResponse.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .productName("Test Product")
                .quantity(2)
                .price(new BigDecimal("99.99"))
                .build();

        orderResponse = OrderResponse.builder()
                .id(orderId)
                .customerId(customerId)
                .customerEmail("test@example.com")
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("199.98"))
                .createdAt(LocalDateTime.now())
                .items(List.of(itemResponse))
                .build();
    }

    @Test
    void createOrder_Success() throws Exception {// Успешное создание заказа
        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(orderResponse);// Когда вызовут createOrder с любым OrderRequest, верни OrderResponse

        mockMvc.perform(post("/api/v1/orders")// Отправляет POST запрос на "/api/v1/orders" с телом: ...
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())// Статус 201 Created
                .andExpect(jsonPath("$.id").value(orderId.toString()))// ID заказа совпадает
                .andExpect(jsonPath("$.status").value("CONFIRMED"))// Статус = CONFIRMED
                .andExpect(jsonPath("$.customerEmail").value("test@example.com"))// Почта совпадает
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].productName").value("Test Product"));// В items есть название продукта Test Product
    }

    @Test
    void createOrder_ValidationError_ShouldReturnBadRequest() throws Exception {// Ошибка валидации
        OrderRequest invalidRequest = OrderRequest.builder()
                .customerId(customerId)
                .customerEmail("")// Невалидная почта
                .items(List.of())// Пустой список частей заказа
                .build();

        mockMvc.perform(post("/api/v1/orders")// Отправляет запрос
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());// Ожидает ошибку
    }

    @Test
    void createOrder_WarehouseConflict_ShouldReturnConflict() throws Exception {// Конфликт на складе
        when(orderService.createOrder(any(OrderRequest.class)))
                .thenThrow(new WarehouseConflictException("Not enough stock"));// При создании заказа выкидываем ошибку о переполнении

        mockMvc.perform(post("/api/v1/orders")// Отправка запроса
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough stock"));// Ожидаем сообщение о переполнении
    }

    @Test
    void getOrder_Success() throws Exception {// Получение заказа
        when(orderService.getOrderById(orderId)).thenReturn(orderResponse);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())// Должен вернуть 200
                .andExpect(jsonPath("$.id").value(orderId.toString()))// Подтверждение что айди верен
                .andExpect(jsonPath("$.customerEmail").value("test@example.com"))// Эмейл верен
                .andExpect(jsonPath("$.items[0].productName").value("Test Product"));// Название продукта верно
    }

    @Test
    void getOrder_NotFound_ShouldReturnNotFound() throws Exception {// Заказ не найден
        UUID nonExistentId = UUID.randomUUID();// Генерируем рандомный UUID
        when(orderService.getOrderById(nonExistentId))
                .thenThrow(new OrderNotFoundException("Order not found with id: " + nonExistentId));// При неправильном айди выбрасываем сообщение об ошибке

        mockMvc.perform(get("/api/v1/orders/{id}", nonExistentId))
                .andExpect(status().isNotFound())// 404
                .andExpect(jsonPath("$.message").value("Order not found with id: " + nonExistentId));// Ожидаем сообщение об ошибке
    }

    @Test
    void getCustomerOrders_Success() throws Exception {// Заказы клиента
        List<OrderResponse> orders = List.of(orderResponse);// Генерируем лист с заказами
        when(orderService.getOrdersByCustomerId(customerId)).thenReturn(orders);

        mockMvc.perform(get("/api/v1/orders/customer/{customerId}", customerId))
                .andExpect(status().isOk())// 200
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(orderId.toString()))// id верен
                .andExpect(jsonPath("$[0].customerEmail").value("test@example.com"));// почта верна
    }

    @Test
    void getCustomerOrders_EmptyList_ShouldReturnEmptyArray() throws Exception {// Нет заказов
        UUID emptyCustomerId = UUID.randomUUID();// Рандомный UUID
        when(orderService.getOrdersByCustomerId(emptyCustomerId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/customer/{customerId}", emptyCustomerId))
                .andExpect(status().isOk())// 200
                .andExpect(jsonPath("$").isArray())// Это массив
                .andExpect(jsonPath("$").isEmpty());// Пустой
    }
}
