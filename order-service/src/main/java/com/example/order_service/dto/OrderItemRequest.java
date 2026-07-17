package com.example.order_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data// Генерирует гетеры, сетеры, туСтринг, иквелс, хешкод
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequest {// ДТО для приема данных от клиента при создании заказа

    @NotNull(message = "Product ID is required")// Не должно быть null
    private UUID productId;

    @NotBlank(message = "Product name is required")// Строка не null, не пустая, нету пробелов
    private String productName;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")// >= 1
    private Integer quantity;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")// > 0
    private BigDecimal price;
}
