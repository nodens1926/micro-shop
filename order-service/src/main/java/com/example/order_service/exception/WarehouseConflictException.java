package com.example.order_service.exception;

public class WarehouseConflictException extends RuntimeException {

    public WarehouseConflictException(String message) {
        super(message);
    }

    public WarehouseConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}