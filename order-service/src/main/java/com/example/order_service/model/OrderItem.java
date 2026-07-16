package com.example.order_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity// JPA-сущность
@Table(name = "orders_items")// Связь с БД
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder// Паттерн билдер
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;// Айдишник

    @ManyToOne(fetch = FetchType.LAZY)// Связь один-ко-многим
    @JoinColumn(name = "order_id", nullable = false)// Внешний ключ
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;// Ссылка на товар из WarehouseService

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;// Дублирование названия чтобы не ходить второй раз в WarehouseService

    @Column(name = "quantity", nullable = false)
    private Integer quantity;// Количество

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;// Цена
}
