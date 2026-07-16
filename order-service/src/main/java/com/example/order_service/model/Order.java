package com.example.order_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity// JPA-сущность
@Table(name = "orders")// Имя таблицы в БД
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder// Паттерн Builder для создания обьектов
public class Order {// Заказ, представляет таблицу orders

    @Id// Первичный ключ
    @GeneratedValue(strategy = GenerationType.UUID)// Автогенерация айди
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;// Айди покупателя

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;// Почта

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)// Хранится как строка
    private OrderStatus status;// Статус заказа

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;// Общая сумма

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp// Автоматически заполняется при создании
    private LocalDateTime createdAt;// Дата создания заказа

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)// Связь один-ко-многим
    @Builder.Default// Инициализируется пустым списком при использовании билдера
    private List<OrderItem> items = new ArrayList<>();// Части заказа

    @PrePersist// Вызывается перед сохранением
    public void prePersist() {
        if (status == null) {
            status = OrderStatus.PENDING;// Статус по умолчанию
        }
        if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;// Сумма по умолчанию
        }
    }
    // Методы для обратной связи с OrderItem, создание и удаление части заказа
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }
}