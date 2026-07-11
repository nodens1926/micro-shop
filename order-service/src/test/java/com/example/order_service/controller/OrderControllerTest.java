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

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private UUID orderId;
    private UUID customerId;
    private UUID productId;
    private OrderRequest orderRequest;
    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {
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
    void createOrder_Success() throws Exception {
        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(orderResponse);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerEmail").value("test@example.com"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].productName").value("Test Product"));
    }

    @Test
    void createOrder_ValidationError_ShouldReturnBadRequest() throws Exception {
        OrderRequest invalidRequest = OrderRequest.builder()
                .customerId(customerId)
                .customerEmail("")
                .items(List.of())
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_WarehouseConflict_ShouldReturnConflict() throws Exception {
        when(orderService.createOrder(any(OrderRequest.class)))
                .thenThrow(new WarehouseConflictException("Not enough stock"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough stock"));
    }

    @Test
    void getOrder_Success() throws Exception {
        when(orderService.getOrderById(orderId)).thenReturn(orderResponse);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.customerEmail").value("test@example.com"))
                .andExpect(jsonPath("$.items[0].productName").value("Test Product"));
    }

    @Test
    void getOrder_NotFound_ShouldReturnNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(orderService.getOrderById(nonExistentId))
                .thenThrow(new OrderNotFoundException("Order not found with id: " + nonExistentId));

        mockMvc.perform(get("/api/v1/orders/{id}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found with id: " + nonExistentId));
    }

    @Test
    void getCustomerOrders_Success() throws Exception {
        List<OrderResponse> orders = List.of(orderResponse);
        when(orderService.getOrdersByCustomerId(customerId)).thenReturn(orders);

        mockMvc.perform(get("/api/v1/orders/customer/{customerId}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$[0].customerEmail").value("test@example.com"));
    }

    @Test
    void getCustomerOrders_EmptyList_ShouldReturnEmptyArray() throws Exception {
        UUID emptyCustomerId = UUID.randomUUID();
        when(orderService.getOrdersByCustomerId(emptyCustomerId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/customer/{customerId}", emptyCustomerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
