package com.example.warehouse_service.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class OrderEvent {
    private String eventType;
    private UUID orderId;
    private UUID customerId;
    private String customerEmail;
    private BigDecimal totalAmount;
    private List<Map<String, Object>> items;
}