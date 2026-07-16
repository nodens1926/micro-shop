package com.example.order_service.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration// Класс конфигурации
@EnableFeignClients(basePackages = "com.example.order_service.service")// Включает поддержку Feign клиентов, указывает в каком пакете искать фейн-клиенты
public class RestClientConfig {
    // FeignClients автоматически сканируются в пакете service
    // Дополнительные настройки можно добавить при необходимости
}
// Feign - HTTP клиент для упрощения общения между микросервисами, делает хорошо