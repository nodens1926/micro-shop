package com.example.warehouse_service.exception;

import lombok.Getter;
import com.example.warehouse_service.dto.ReserveResponse;

@Getter
public class InsufficientStockException extends RuntimeException {
    private final ReserveResponse response;

    public InsufficientStockException(ReserveResponse response) {
        super("Insufficient stock");
        this.response = response;
    }
}