package com.example.warehouse_service.service;

import com.example.warehouse_service.config.KafkaConfig;
import com.example.warehouse_service.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final WarehouseService warehouseService;

    @KafkaListener(topics = KafkaConfig.TOPIC_ORDER_EVENTS, groupId = "warehouse-service-group")
    public void consumeOrderEvent(@Payload OrderEvent event) {
        log.info("Received order event: {}", event);
        try {
            warehouseService.processOrderEvent(event);
        } catch (Exception e) {
            log.error("Error processing order event: ", e);
        }
    }
}