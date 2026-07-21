package com.example.warehouse_service.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReserveResponse {
    private UUID orderId;
    private boolean success;
    private List<MissingItem> missingItems;
    private String message;

    @Data
    @Builder
    public static class MissingItem {
        private UUID productId;
        private String productName;
        private Integer requestedQuantity;
        private Integer availableQuantity;
    }
}