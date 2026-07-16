package com.example.order_service.repository;

import com.example.order_service.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// Интерфейс для работы с таблицей orders_items в БД
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {// OrderItem - тип сущности с которой работаем, UUID - тип первичного ключа
    List<OrderItem> findByOrderId(UUID orderId);// Кастомный метод который JPA реализует автоматически благодаря названию самого метода
}