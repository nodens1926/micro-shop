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

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderEvents(Order order) {
        log.info("Publishing Kafka events for order: {}", order.getId());

        Map<String, Object> orderEvent = buildOrderEvent(order);
        kafkaTemplate.send(KafkaConfig.TOPIC_ORDER_EVENTS, order.getId().toString(), orderEvent);
        log.info("Sent event to topic: {}", KafkaConfig.TOPIC_ORDER_EVENTS);

        Map<String, Object> notificationEvent = buildNotificationEvent(order);
        kafkaTemplate.send(KafkaConfig.TOPIC_NOTIFICATION_EVENTS, order.getCustomerId().toString(), notificationEvent);
        log.info("Sent event to topic: {}", KafkaConfig.TOPIC_NOTIFICATION_EVENTS);
    }

    private Map<String, Object> buildOrderEvent(Order order) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "ORDER_CONFIRMED");
        event.put("orderId", order.getId());
        event.put("customerEmail", order.getCustomerEmail());
        event.put("totalAmount", order.getTotalAmount());

        List<Map<String, Object>> items = order.getItems().stream()
                .map(this::mapOrderItemToMap)
                .collect(Collectors.toList());
        event.put("items", items);

        return event;
    }

    private Map<String, Object> buildNotificationEvent(Order order) {
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

    private Map<String, Object> mapOrderItemToMap(OrderItem item) {
        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("productId", item.getProductId());
        itemMap.put("productName", item.getProductName());
        itemMap.put("quantity", item.getQuantity());
        itemMap.put("price", item.getPrice());
        return itemMap;
    }
}
