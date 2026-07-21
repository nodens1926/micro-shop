package com.example.warehouse_service.service;

import com.example.warehouse_service.dto.OrderEvent;
import com.example.warehouse_service.dto.ReserveRequest;
import com.example.warehouse_service.dto.ReserveResponse;
import com.example.warehouse_service.dto.StockRequest;

public interface WarehouseService {
    ReserveResponse reserve(ReserveRequest request);
    void addStock(StockRequest request);
    void processOrderEvent(OrderEvent event);
}