package com.example.order_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration// Помечает как источник конфигурации, включает в себя бины которые добавятся в спринг
public class KafkaConfig {

    // Имена топиков в кафке
    public static final String TOPIC_ORDER_EVENTS = "order-events";
    public static final String TOPIC_NOTIFICATION_EVENTS = "notification-event";


    @Bean// Обьект спринг
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(TOPIC_ORDER_EVENTS)// TopicBuilder - удобная тема для создания топика
                .partitions(3)// Количество партиций
                .replicas(1)// Количество реплик
                .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(TOPIC_NOTIFICATION_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}