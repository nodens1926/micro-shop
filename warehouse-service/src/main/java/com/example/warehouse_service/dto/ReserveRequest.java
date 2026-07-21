package com.example.warehouse_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReserveRequest {

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<ReserveItem> items;

    @Data
    @Builder
    public static class ReserveItem {
        @NotNull(message = "Product ID is required")
        private UUID productId;

        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }
}