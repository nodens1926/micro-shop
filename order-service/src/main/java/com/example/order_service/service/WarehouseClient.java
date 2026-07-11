package com.example.order_service.service;

import com.example.order_service.dto.WarehouseReserveRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "warehouse-service",
        url = "${warehouse.service.url:http://localhost:8082}"
)
public interface WarehouseClient {

    @PostMapping("/api/v1/warehouse/reserve")
    ResponseEntity<Void> reserve(@RequestBody WarehouseReserveRequest request);
}
