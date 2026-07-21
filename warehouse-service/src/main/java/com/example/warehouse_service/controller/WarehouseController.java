package com.example.warehouse_service.controller;

import com.example.warehouse_service.dto.ReserveRequest;
import com.example.warehouse_service.dto.ReserveResponse;
import com.example.warehouse_service.dto.StockRequest;
import com.example.warehouse_service.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
@Slf4j
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping("/reserve")
    public ResponseEntity<ReserveResponse> reserve(@Valid @RequestBody ReserveRequest request) {
        log.info("Received reserve request with {} items", request.getItems().size());
        ReserveResponse response = warehouseService.reserve(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stock")
    public ResponseEntity<Void> addStock(@Valid @RequestBody StockRequest request) {
        log.info("Received add stock request for product: {}", request.getProductId());
        warehouseService.addStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}