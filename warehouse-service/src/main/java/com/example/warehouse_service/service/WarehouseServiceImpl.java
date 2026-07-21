package com.example.warehouse_service.service;

import com.example.warehouse_service.dto.OrderEvent;
import com.example.warehouse_service.dto.ReserveRequest;
import com.example.warehouse_service.dto.ReserveResponse;
import com.example.warehouse_service.dto.StockRequest;
import com.example.warehouse_service.exception.InsufficientStockException;
import com.example.warehouse_service.exception.ProductNotFoundException;
import com.example.warehouse_service.model.Reservation;
import com.example.warehouse_service.model.Stock;
import com.example.warehouse_service.repository.ReservationRepository;
import com.example.warehouse_service.repository.StockRepository;
import com.example.warehouse_service.util.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseServiceImpl implements WarehouseService {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;

    @Override
    @Transactional
    public ReserveResponse reserve(ReserveRequest request) {
        log.info("Processing reserve request with {} items", request.getItems().size());

        List<ReserveResponse.MissingItem> missingItems = new ArrayList<>();
        List<Reservation> reservations = new ArrayList<>();

        // Проверяем наличие всех товаров
        for (ReserveRequest.ReserveItem item : request.getItems()) {
            Stock stock = stockRepository.findByProductIdWithLock(item.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found: " + item.getProductId()
                    ));

            if (stock.getAvailableQuantity() < item.getQuantity()) {
                missingItems.add(ReserveResponse.MissingItem.builder()
                        .productId(stock.getProductId())
                        .productName(stock.getProductName())
                        .requestedQuantity(item.getQuantity())
                        .availableQuantity(stock.getAvailableQuantity())
                        .build());
            }
        }

        // Если есть недостающие товары, возвращаем ошибку
        if (!missingItems.isEmpty()) {
            ReserveResponse response = ReserveResponse.builder()
                    .success(false)
                    .missingItems(missingItems)
                    .message("Insufficient stock for some items")
                    .build();
            throw new InsufficientStockException(response);
        }

        // Резервируем товары
        UUID orderId = UUID.randomUUID(); // В реальном приложении orderId будет из запроса

        for (ReserveRequest.ReserveItem item : request.getItems()) {
            Stock stock = stockRepository.findByProductIdWithLock(item.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found: " + item.getProductId()
                    ));

            // Уменьшаем доступное количество
            stock.setAvailableQuantity(stock.getAvailableQuantity() - item.getQuantity());
            stockRepository.save(stock);

            // Создаем запись резерва
            Reservation reservation = Reservation.builder()
                    .orderId(orderId)
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .status(ReservationStatus.PENDING.getValue())
                    .reservedAt(LocalDateTime.now())
                    .build();
            reservations.add(reservation);
        }

        reservationRepository.saveAll(reservations);
        log.info("Successfully reserved {} items for order: {}", request.getItems().size(), orderId);

        return ReserveResponse.builder()
                .orderId(orderId)
                .success(true)
                .message("Reservation successful")
                .build();
    }

    @Override
    @Transactional
    public void addStock(StockRequest request) {
        log.info("Adding stock for product: {}", request.getProductId());

        Stock stock = stockRepository.findByProductId(request.getProductId())
                .orElse(Stock.builder()
                        .productId(request.getProductId())
                        .productName(request.getProductName())
                        .availableQuantity(0)
                        .build());

        stock.setAvailableQuantity(stock.getAvailableQuantity() + request.getQuantity());
        stock.setProductName(request.getProductName());

        stockRepository.save(stock);
        log.info("Stock updated. Product: {}, New quantity: {}",
                request.getProductId(), stock.getAvailableQuantity());
    }

    @Override
    @Transactional
    public void processOrderEvent(OrderEvent event) {
        log.info("Processing order event: {}", event);

        if (!"ORDER_CONFIRMED".equals(event.getEventType())) {
            log.info("Ignoring event type: {}", event.getEventType());
            return;
        }

        // Находим все резервы для заказа
        List<Reservation> reservations = reservationRepository.findByOrderIdAndStatus(
                event.getOrderId(), ReservationStatus.PENDING.getValue());

        if (reservations.isEmpty()) {
            log.warn("No pending reservations found for order: {}", event.getOrderId());
            return;
        }

        // Подтверждаем резервы (или списываем товары)
        for (Reservation reservation : reservations) {
            reservation.setStatus(ReservationStatus.CONFIRMED.getValue());
            // Здесь можно также списать товары или просто подтвердить резерв
        }

        reservationRepository.saveAll(reservations);
        log.info("Confirmed {} reservations for order: {}", reservations.size(), event.getOrderId());
    }
}