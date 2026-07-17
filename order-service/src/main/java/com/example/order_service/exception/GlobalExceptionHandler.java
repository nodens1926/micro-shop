package com.example.order_service.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice// Глобальный перехватчик исключений для всех контроллеров
@Slf4j// Логгер
public class GlobalExceptionHandler {

    @ExceptionHandler(WarehouseConflictException.class)// Если в любом контроллере выбросится WarehouseConflictException -> вызов метода
    public ResponseEntity<ErrorResponse> handleWarehouseConflict(WarehouseConflictException ex) {
        log.error("Warehouse conflict: {}", ex.getMessage());// Пишет в лог сообщение об ошибке
        ErrorResponse errorResponse = ErrorResponse.builder()// Строит обьект ErrorResponse в билдере
                .timestamp(LocalDateTime.now())// Запись текущего времени об ошибке
                .status(HttpStatus.CONFLICT.value())// Берет HTTP статус 409
                .error(HttpStatus.CONFLICT.getReasonPhrase())// Берет текстовое описание статуса Conflict
                .message(ex.getMessage())// Берет сообщение из исключения
                .build();// Создает обьект
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);// Возвращает HTTP ответ с статусом 409 и телом (JSON)
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        log.error("Order not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())// Статус 404
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())// Текст Not Found
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.error("Validation error: {}", ex.getMessage());

        Map<String, String> errors = ex.getBindingResult()// Получает контейнер со всеми ошибками валидации
                .getFieldErrors()// Достает все ошибки по полям ДТО
                .stream()// Превращает список ошибок в поток для обработки
                .collect(Collectors.toMap(// Собирает все ошибки в мап
                        FieldError::getField,// Ключ - имя поля
                        FieldError::getDefaultMessage,// Значнеие - сообщение об ошибке
                        (existing, replacement) -> existing + "; " + replacement// Если на 1 поле много ошибок, собирает их через ;
                ));

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())// Код ошибки 400
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())// Текст Bad Request
                .message("Validation failed")// Общее сообщение
                .validationErrors(errors)// Детали конкретно по полям
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)// Исключения, связанные с нарушением ограничений БД, нарпимер UNIQUE или NOT NULL
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.error("Constraint violation: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)// Перехватывает любое другое исключнеие, которое не обработано другими методами
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())// Код ошибки 500
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())// Internal Server Error
                .message("An unexpected error occurred")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
