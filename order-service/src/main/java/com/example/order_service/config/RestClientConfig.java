package com.example.order_service.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.example.order_service.service")
public class RestClientConfig {
    // FeignClients автоматически сканируются в пакете service
    // Дополнительные настройки можно добавить при необходимости
}
