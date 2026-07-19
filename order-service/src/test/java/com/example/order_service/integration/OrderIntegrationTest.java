package com.example.order_service.integration;

import com.example.order_service.dto.OrderItemRequest;
import com.example.order_service.dto.OrderRequest;
import com.example.order_service.dto.OrderResponse;
import com.example.order_service.model.OrderStatus;
import com.example.order_service.repository.OrderRepository;
import com.example.order_service.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest// Загружает полный контекст Spring
@Testcontainers// Запускает поддержку testcontainers (Запускает реальный PostgreSQL в докер контейнере)
@Transactional// После каждого теста данные в БД откатываются
class OrderIntegrationTest {

    @Container// Запуск контейнера в докере
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:12.9")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource// Настройка подключения к БД
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");// Включение ликвибейз
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private UUID customerId;
    private UUID productId;
    private OrderRequest orderRequest;

    @BeforeEach// Заполнение тестовых данных
    void setUp() {
        customerId = UUID.randomUUID();
        productId = UUID.randomUUID();

        OrderItemRequest itemRequest = OrderItemRequest.builder()
                .productId(productId)
                .productName("Test Product")
                .quantity(2)
                .price(new BigDecimal("99.99"))
                .build();

        orderRequest = OrderRequest.builder()
                .customerId(customerId)
                .customerEmail("test@example.com")
                .items(List.of(itemRequest))
                .build();
    }

    @Test
    void createOrder_Success() {// Создание заказа
        // Вызов релаьного сервиса
        OrderResponse response = orderService.createOrder(orderRequest);

        // Проверка ответа от него
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getCustomerEmail()).isEqualTo("test@example.com");
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("199.98"));

        // Чекаем что данные реально в БД
        var savedOrder = orderRepository.findById(response.getId());
        assertThat(savedOrder).isPresent();
        assertThat(savedOrder.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(savedOrder.get().getItems()).hasSize(1);
        assertThat(savedOrder.get().getItems().get(0).getProductName()).isEqualTo("Test Product");
    }

    @Test
    void getOrderById_Success() {// Получние заказа
        // Создание заказа
        OrderResponse created = orderService.createOrder(orderRequest);

        // Получене его по айди
        OrderResponse found = orderService.getOrderById(created.getId());

        // ПРоверка что данные совпадают
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getCustomerEmail()).isEqualTo("test@example.com");
        assertThat(found.getItems()).hasSize(1);
    }

    @Test
    void getOrdersByCustomerId_Success() {// Заказы клиента
        // Создание заказа
        orderService.createOrder(orderRequest);

        // Получение всех заказов клиента
        var orders = orderService.getOrdersByCustomerId(customerId);

        assertThat(orders).isNotEmpty();
        assertThat(orders.get(0).getCustomerId()).isEqualTo(customerId);
        assertThat(orders.get(0).getItems()).hasSize(1);
    }
}
