// OrderServiceApplication.java
package com.kubeshop.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@SpringBootApplication
@RestController
@RequestMapping("/orders")
public class OrderServiceApplication {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String CATALOG_SERVICE_URL = System.getenv("CATALOG_SERVICE_URL") != null ? System.getenv("CATALOG_SERVICE_URL") : "http://localhost:8001";
    private final String CART_SERVICE_URL = System.getenv("CART_SERVICE_URL") != null ? System.getenv("CART_SERVICE_URL") : "http://localhost:8002";
    private final String PAYMENT_SERVICE_URL = System.getenv("PAYMENT_SERVICE_URL") != null ? System.getenv("PAYMENT_SERVICE_URL") : "http://localhost:8004";
    private final String NOTIFICATION_SERVICE_URL = System.getenv("NOTIFICATION_SERVICE_URL") != null ? System.getenv("NOTIFICATION_SERVICE_URL") : "http://localhost:8005";
    private final String DATABASE_URL = System.getenv("DATABASE_URL") != null ? System.getenv("DATABASE_URL") : "jdbc:postgresql://localhost:5432/kube_shop_db";
    private final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "user";
    private final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "password";

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (" +
                    "order_id VARCHAR(255) PRIMARY KEY," +
                    "user_id VARCHAR(255) NOT NULL," +
                    "total_amount DOUBLE PRECISION NOT NULL," +
                    "status VARCHAR(255) NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");
            stmt.execute("CREATE TABLE IF NOT EXISTS order_items (" +
                    "order_item_id SERIAL PRIMARY KEY," +
                    "order_id VARCHAR(255) NOT NULL," +
                    "product_id INT NOT NULL," +
                    "quantity INT NOT NULL," +
                    "price DOUBLE PRECISION NOT NULL," +
                    "FOREIGN KEY (order_id) REFERENCES orders(order_id)" +
                    ");");
            System.out.println("Таблицы orders и order_items успешно созданы или уже существуют.");
        } catch (SQLException e) {
            System.err.println("Ошибка при создании таблиц: " + e.getMessage());
        }
    }


    @PostMapping("/create")
    public ResponseEntity<String> createOrder(@RequestBody Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        if (userId == null) {
            return ResponseEntity.badRequest().body("Требуется userId.");
        }

        try {
            // 1. Получить товары из корзины пользователя
            List<Map<String, Object>> cartItems = restTemplate.getForObject(CART_SERVICE_URL + "/cart/" + userId, List.class);
            if (cartItems == null || cartItems.isEmpty()) {
                return ResponseEntity.badRequest().body("Корзина пользователя пуста.");
            }

            double totalAmount = 0.0;
            List<Map<String, Object>> orderItems = new ArrayList<>();

            // 2. Проверить наличие товаров в Catalog Service
            for (Map<String, Object> item : cartItems) {
                Integer productId = (Integer) item.get("product_id");
                Integer quantity = (Integer) item.get("quantity");

                Map<String, Object> product = restTemplate.getForObject(CATALOG_SERVICE_URL + "/products/" + productId, Map.class);
                if (product == null || (Integer) product.get("stock") < quantity) {
                    return ResponseEntity.badRequest().body("Недостаточно товара " + productId + " на складе.");
                }
                double price = (Double) product.get("price");
                totalAmount += price * quantity;

                Map<String, Object> orderItem = new HashMap<>();
                orderItem.put("productId", productId);
                orderItem.put("quantity", quantity);
                orderItem.put("price", price);
                orderItems.add(orderItem);
            }

            // 3. Имитация оплаты
            Map<String, Object> paymentRequest = new HashMap<>();
            paymentRequest.put("userId", userId);
            paymentRequest.put("amount", totalAmount);
            ResponseEntity<String> paymentResponse = restTemplate.postForEntity(PAYMENT_SERVICE_URL + "/process", paymentRequest, String.class);

            if (paymentResponse.getStatusCode().is2xxSuccessful()) {
                // 4. Создать заказ в БД
                String orderId = UUID.randomUUID().toString();
                try (Connection conn = DriverManager.getConnection(DATABASE_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement stmt = conn.prepareStatement("INSERT INTO orders (order_id, user_id, total_amount, status) VALUES (?, ?, ?, ?)")) {
                    stmt.setString(1, orderId);
                    stmt.setString(2, userId);
                    stmt.setDouble(3, totalAmount);
                    stmt.setString(4, "PENDING"); // Или "COMPLETED" сразу
                    stmt.executeUpdate();
                }

                // 5. Добавить элементы заказа в БД
                try (Connection conn = DriverManager.getConnection(DATABASE_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement stmt = conn.prepareStatement("INSERT INTO order_items (order_id, product_id, quantity, price) VALUES (?, ?, ?, ?)")) {
                    for (Map<String, Object> item : orderItems) {
                        stmt.setString(1, orderId);
                        stmt.setInt(2, (Integer) item.get("productId"));
                        stmt.setInt(3, (Integer) item.get("quantity"));
                        stmt.setDouble(4, (Double) item.get("price"));
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }

                // 6. Очистить корзину пользователя (для простоты - удаление всех товаров из корзины)
                // В реальном приложении это может быть более сложный процесс (уменьшение количества, если товар не был весь куплен)
                restTemplate.delete(CART_SERVICE_URL + "/cart/" + userId + "/clear"); // Предполагаем наличие такого эндпоинта

                // 7. Отправить уведомление
                Map<String, String> notification = new HashMap<>();
                notification.put("userId", userId);
                notification.put("message", "Ваш заказ " + orderId + " успешно создан!");
                restTemplate.postForEntity(NOTIFICATION_SERVICE_URL + "/notify", notification, String.class);

                return ResponseEntity.ok("Заказ " + orderId + " успешно создан и оплачен.");
            } else {
                return ResponseEntity.status(paymentResponse.getStatusCode()).body("Ошибка оплаты: " + paymentResponse.getBody());
            }

        } catch (Exception e) {
            System.err.println("Ошибка при создании заказа: " + e.getMessage());
            return ResponseEntity.status(500).body("Ошибка сервера: " + e.getMessage());
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderId) {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM orders WHERE order_id = ?")) {
            stmt.setString(1, orderId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> order = new HashMap<>();
                order.put("orderId", rs.getString("order_id"));
                order.put("userId", rs.getString("user_id"));
                order.put("totalAmount", rs.getDouble("total_amount"));
                order.put("status", rs.getString("status"));
                order.put("createdAt", rs.getTimestamp("created_at"));

                // Получить элементы заказа
                try (PreparedStatement itemStmt = conn.prepareStatement("SELECT product_id, quantity, price FROM order_items WHERE order_id = ?")) {
                    itemStmt.setString(1, orderId);
                    ResultSet itemRs = itemStmt.executeQuery();
                    List<Map<String, Object>> items = new ArrayList<>();
                    while (itemRs.next()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("productId", itemRs.getInt("product_id"));
                        item.put("quantity", itemRs.getInt("quantity"));
                        item.put("price", itemRs.getDouble("price"));
                        items.add(item);
                    }
                    order.put("items", items);
                }
                return ResponseEntity.ok(order);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при получении заказа: " + e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }
}