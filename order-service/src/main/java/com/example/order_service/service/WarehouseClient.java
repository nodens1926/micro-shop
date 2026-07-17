package com.example.order_service.service;

import com.example.order_service.dto.WarehouseReserveRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "warehouse-service",// Имя сервиса для идентификации
        url = "${warehouse.service.url:http://localhost:8082}"// Базовый URL сервиса
)
public interface WarehouseClient {// Класс - это Feign клиент, позволяет вызывать REST API WarehouseService-а

    @PostMapping("/api/v1/warehouse/reserve")// ОТправляет пост-запрос на указанный URL
    ResponseEntity<Void>/* Возвращает статус-код, но без тела запроса */ reserve(@RequestBody /* Преобразует WarehouseReserveRequest и кладет его в в JSON */ WarehouseReserveRequest request);
}
