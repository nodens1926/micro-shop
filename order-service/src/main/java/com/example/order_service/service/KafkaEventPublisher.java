package com.example.order_service.service;

import com.example.order_service.config.KafkaConfig;
import com.example.order_service.model.Order;
import com.example.order_service.model.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service// Помечает класс как бин с бизнес-логикой
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate/* Основной класс для отправки сообщений в кафку */<String, Object> kafkaTemplate;// String - тип ключа сообщения, Object - тип значения сообщения

    public void publishOrderEvents(Order order) {
        log.info("Publishing Kafka events for order: {}", order.getId());

        Map<String, Object> orderEvent = buildOrderEvent(order);// Построение события для топика order-events
        kafkaTemplate.send(KafkaConfig.TOPIC_ORDER_EVENTS, order.getId().toString(), orderEvent);// Кафка берет ключ(айди), вычисляет хеш ключа, и отправляет событие в партицию к топику
        log.info("Sent event to topic: {}", KafkaConfig.TOPIC_ORDER_EVENTS);

        Map<String, Object> notificationEvent = buildNotificationEvent(order);// Событие для топика notification-event
        kafkaTemplate.send(KafkaConfig.TOPIC_NOTIFICATION_EVENTS, order.getCustomerId().toString(), notificationEvent);// Отправляем в топик
        log.info("Sent event to topic: {}", KafkaConfig.TOPIC_NOTIFICATION_EVENTS);
    }

    private Map<String, Object> buildOrderEvent(Order order) {// Событие заказа
        Map<String, Object> event = new HashMap<>();// Создание пустой мапы
        event.put("eventType", "ORDER_CONFIRMED");
        event.put("orderId", order.getId());
        event.put("customerEmail", order.getCustomerEmail());
        event.put("totalAmount", order.getTotalAmount());

        List<Map<String, Object>> items = order.getItems().stream()// Кладем части заказа в мапу
                .map(this::mapOrderItemToMap)
                .collect(Collectors.toList());
        event.put("items", items);

        return event;
    }

    private Map<String, Object> buildNotificationEvent(Order order) {// Событие уведомления
        Map<String, Object> event = new HashMap<>();
        event.put("customerId", order.getCustomerId());
        event.put("customerEmail", order.getCustomerEmail());
        event.put("totalAmount", order.getTotalAmount());

        List<Map<String, Object>> items = order.getItems().stream()
                .map(this::mapOrderItemToMap)
                .collect(Collectors.toList());
        event.put("items", items);

        return event;
    }

    private Map<String, Object> mapOrderItemToMap(OrderItem item) {// Преобразование позиции
        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("productId", item.getProductId());
        itemMap.put("productName", item.getProductName());
        itemMap.put("quantity", item.getQuantity());
        itemMap.put("price", item.getPrice());
        return itemMap;
    }
}
