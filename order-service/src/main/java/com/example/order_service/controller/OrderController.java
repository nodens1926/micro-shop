package com.example.order_service.controller;

import com.example.order_service.dto.OrderRequest;
import com.example.order_service.dto.OrderResponse;
import com.example.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController// Помечает класс как REST-контроллер, тоже бин
@RequestMapping("/api/v1/orders")// URL путь для методов-эндпоинтов
@RequiredArgsConstructor
@Slf4j// Добавляет log для логирования
public class OrderController {

    private final OrderService orderService;

    @PostMapping// Обрабатывает POST запрос
    @ResponseStatus(HttpStatus.CREATED)// Возвращает код 201
    public OrderResponse createOrder(@RequestBody /* Преобразует OrderRequest в JSON */ @Valid /* Включает валидацию из ДТО */ OrderRequest request) {
        log.info("Received request to create order for customer: {}", request.getCustomerId());// Пишет в лог сообщение с айди пользователя
        return orderService.createOrder(request);// Создает заказ
    }

    @GetMapping("/{id}")// Обрабатывает GET запрос с QUERY параметрами
    public OrderResponse getOrder(@PathVariable /* Извлекает знанеие из URL */ UUID id) {
        log.info("Received request to get order by id: {}", id);// Пишет в лог сообщение с айди заказа
        return orderService.getOrderById(id);// Гет зарпос у сервиса
    }

    @GetMapping("/customer/{customerId}")// GET запрос с айдишником пользователя
    public List<OrderResponse> getCustomerOrders(@PathVariable /* Извлекает айди из URL */ UUID customerId) {
        log.info("Received request to get orders for customer: {}", customerId);// Пишет в лог сообщение с айди покупателя
        return orderService.getOrdersByCustomerId(customerId);// Возвращает список заказов
    }
}