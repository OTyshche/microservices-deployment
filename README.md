Архитектура микросервисного приложения: Онлайн-магазин "KubeShop"
Предлагаю создать упрощенный онлайн-магазин, который будет состоять из следующих микросервисов:

Сервис каталога товаров (Catalog Service): Отвечает за хранение и предоставление информации о товарах.
Сервис корзины (Cart Service): Управляет пользовательскими корзинами.
Сервис заказов (Order Service): Обрабатывает создание и управление заказами.
Сервис уведомлений (Notification Service): Отправляет уведомления (например, о статусе заказа).
Сервис платежей (Payment Service): Имитирует процесс оплаты.
База данных (PostgreSQL): Для хранения данных каталога, корзин и заказов.
Фронтенд (Frontend): Пользовательский интерфейс магазина.
Диаграмма архитектуры
+----------------+      +------------------+      +-----------------+      +---------------------+
|    Frontend    |<---->|   Catalog Service|      |   Cart Service  |      |  Notification Service |
| (React/Vue/Angular)|    | (Python/FastAPI) |<---->| (Node.js/Express)|      | (Python/FastAPI)    |
+----------------+      +------------------+      +-----------------+      +---------------------+
       |                         ^                        ^                          ^
       |                         |                        |                          |
       |                         |                        |                          |
       V                         |                        |                          |
+-----------------+              |                        |                          |
|   Order Service |<-------------+------------------------+--------------------------+
| (Java/Spring Boot)|
+-----------------+
       |
       |
       V
+-----------------+
| Payment Service |
| (Python/Flask)  |
+-----------------+
       |
       |
       V
+-----------------+
|    PostgreSQL   |
| (Existing Image)|
+-----------------+
Описание файлов микросервисов
Для каждого микросервиса я предоставлю примерный код, который вы можете использовать для создания своих Dockerfile.

1. Сервис каталога товаров (Catalog Service)
Технология: Python (FastAPI)

Порт по умолчанию: 8001

Описание: Предоставляет API для работы с товарами: получение списка, получение информации об одном товаре, добавление/обновление/удаление товаров (для простоты можно реализовать без аутентификации). Данные хранятся в PostgreSQL.

catalog_service.py

Python

# catalog_service.py
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import psycopg2
import os

app = FastAPI()

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://user:password@localhost:5432/kube_shop_db")

def get_db_connection():
    conn = psycopg2.connect(DATABASE_URL)
    return conn

class Product(BaseModel):
    id: int
    name: str
    description: str
    price: float
    stock: int

@app.on_event("startup")
async def startup_event():
    conn = None
    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS products (
                id SERIAL PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                price FLOAT NOT NULL,
                stock INT NOT NULL
            );
        """)
        conn.commit()
        print("Таблица products успешно создана или уже существует.")
    except Exception as e:
        print(f"Ошибка при создании таблицы products: {e}")
    finally:
        if conn:
            conn.close()

@app.get("/products")
async def get_products():
    conn = None
    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute("SELECT id, name, description, price, stock FROM products;")
        products = []
        for row in cur.fetchall():
            products.append(Product(id=row[0], name=row[1], description=row[2], price=row[3], stock=row[4]))
        return products
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Ошибка сервера: {e}")
    finally:
        if conn:
            conn.close()

@app.get("/products/{product_id}")
async def get_product(product_id: int):
    conn = None
    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute("SELECT id, name, description, price, stock FROM products WHERE id = %s;", (product_id,))
        product_data = cur.fetchone()
        if product_data:
            return Product(id=product_data[0], name=product_data[1], description=product_data[2], price=product_data[3], stock=product_data[4])
        raise HTTPException(status_code=404, detail="Товар не найден")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Ошибка сервера: {e}")
    finally:
        if conn:
            conn.close()

@app.post("/products")
async def create_product(product: Product):
    conn = None
    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO products (name, description, price, stock) VALUES (%s, %s, %s, %s) RETURNING id;",
            (product.name, product.description, product.price, product.stock)
        )
        new_product_id = cur.fetchone()[0]
        conn.commit()
        return {"id": new_product_id, "message": "Товар успешно создан"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Ошибка сервера: {e}")
    finally:
        if conn:
            conn.close()

# Для локального запуска: uvicorn catalog_service:app --host 0.0.0.0 --port 8001
requirements.txt

fastapi
uvicorn
psycopg2-binary
2. Сервис корзины (Cart Service)
Технология: Node.js (Express)

Порт по умолчанию: 8002

Описание: Управляет пользовательскими корзинами. Каждая корзина ассоциирована с userId. Позволяет добавлять/удалять товары из корзины, получать содержимое корзины. Данные хранятся в PostgreSQL.

cart_service.js

JavaScript

// cart_service.js
const express = require('express');
const { Pool } = require('pg');
const app = express();
const port = 8002;

app.use(express.json());

const pool = new Pool({
    connectionString: process.env.DATABASE_URL || 'postgresql://user:password@localhost:5432/kube_shop_db',
});

pool.on('connect', () => {
    console.log('Подключено к базе данных PostgreSQL.');
});

pool.on('error', (err) => {
    console.error('Неожиданная ошибка подключения к базе данных:', err);
});

// Создание таблицы cart_items при запуске
pool.query(`
    CREATE TABLE IF NOT EXISTS cart_items (
        user_id VARCHAR(255) NOT NULL,
        product_id INT NOT NULL,
        quantity INT NOT NULL,
        PRIMARY KEY (user_id, product_id)
    );
`)
.then(() => console.log('Таблица cart_items успешно создана или уже существует.'))
.catch(err => console.error('Ошибка при создании таблицы cart_items:', err));

// Получить содержимое корзины пользователя
app.get('/cart/:userId', async (req, res) => {
    const { userId } = req.params;
    try {
        const result = await pool.query('SELECT product_id, quantity FROM cart_items WHERE user_id = $1', [userId]);
        res.json(result.rows);
    } catch (err) {
        console.error('Ошибка при получении корзины:', err);
        res.status(500).send('Ошибка сервера');
    }
});

// Добавить товар в корзину
app.post('/cart/:userId/add', async (req, res) => {
    const { userId } = req.params;
    const { productId, quantity } = req.body;
    if (!productId || !quantity || quantity <= 0) {
        return res.status(400).send('Требуются productId и положительное количество.');
    }

    try {
        await pool.query(
            `INSERT INTO cart_items (user_id, product_id, quantity)
             VALUES ($1, $2, $3)
             ON CONFLICT (user_id, product_id) DO UPDATE SET quantity = cart_items.quantity + $3`,
            [userId, productId, quantity]
        );
        res.status(200).send('Товар добавлен в корзину');
    } catch (err) {
        console.error('Ошибка при добавлении товара в корзину:', err);
        res.status(500).send('Ошибка сервера');
    }
});

// Удалить товар из корзины
app.delete('/cart/:userId/remove', async (req, res) => {
    const { userId } = req.params;
    const { productId } = req.body;
    if (!productId) {
        return res.status(400).send('Требуется productId.');
    }

    try {
        await pool.query('DELETE FROM cart_items WHERE user_id = $1 AND product_id = $2', [userId, productId]);
        res.status(200).send('Товар удален из корзины');
    } catch (err) {
        console.error('Ошибка при удалении товара из корзины:', err);
        res.status(500).send('Ошибка сервера');
    }
});

app.listen(port, () => {
    console.log(`Сервис корзины запущен на http://localhost:${port}`);
});
package.json

JSON

{
  "name": "cart-service",
  "version": "1.0.0",
  "description": "Microservice for managing user carts",
  "main": "cart_service.js",
  "scripts": {
    "start": "node cart_service.js"
  },
  "dependencies": {
    "express": "^4.18.2",
    "pg": "^8.11.3"
  }
}
3. Сервис заказов (Order Service)
Технология: Java (Spring Boot)

Порт по умолчанию: 8003

Описание: Отвечает за создание и управление заказами. При создании заказа обращается к Catalog Service для проверки наличия товаров и к Cart Service для очистки корзины. Также может отправлять уведомления через Notification Service и инициировать оплату через Payment Service. Данные хранятся в PostgreSQL.

OrderServiceApplication.java

Java

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
pom.xml (важные зависимости)

    <dependencies>
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
<groupId>org.postgresql</groupId>
<artifactId>postgresql</artifactId>
<scope>runtime</scope>
</dependency>
</dependencies>
```

4. Сервис уведомлений (Notification Service)
Технология: Python (FastAPI)

Порт по умолчанию: 8005

Описание: Простой сервис, который имитирует отправку уведомлений. Может получать сообщения и выводить их в консоль или сохранять в "уведомления" (для простоты будем выводить в консоль).

notification_service.py

Python

# notification_service.py
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class Notification(BaseModel):
    userId: str
    message: str

@app.post("/notify")
async def notify_user(notification: Notification):
    print(f"Отправлено уведомление для пользователя {notification.userId}: {notification.message}")
    return {"status": "Уведомление отправлено", "userId": notification.userId, "message": notification.message}

# Для локального запуска: uvicorn notification_service:app --host 0.0.0.0 --port 8005
requirements.txt

fastapi
uvicorn
5. Сервис платежей (Payment Service)
Технология: Python (Flask)

Порт по умолчанию: 8004

Описание: Имитирует процесс оплаты. Принимает сумму и userId, всегда отвечает "успешно" для упрощения.

payment_service.py

Python

# payment_service.py
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/process', methods=['POST'])
def process_payment():
    data = request.json
    user_id = data.get('userId')
    amount = data.get('amount')

    if not user_id or not amount:
        return jsonify({"error": "Требуются userId и amount"}), 400

    print(f"Имитация оплаты для пользователя {user_id}: {amount} USD")
    # В реальном приложении здесь была бы логика взаимодействия с платежным шлюзом
    return jsonify({"status": "Оплата успешна", "userId": user_id, "amount": amount}), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8004)
6. Фронтенд (Frontend)
Технология: Например, React (или любой другой фреймворк)

Порт по умолчанию: 3000 (для React Dev Server), 80 (для статики в контейнере)

Описание: Простое одностраничное приложение, которое взаимодействует с бэкенд-сервисами (в основном с Catalog Service, Cart Service и Order Service). Может отображать список товаров, позволять добавлять их в корзину, просматривать корзину и оформлять заказ.

frontend/public/index.html

HTML

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>KubeShop</title>
    <style>
        body { font-family: sans-serif; margin: 20px; }
        .product-list, .cart, .order-details { margin-top: 30px; border: 1px solid #ccc; padding: 20px; }
        .product-item, .cart-item { margin-bottom: 10px; padding: 5px; border-bottom: 1px dashed #eee; }
        button { margin-left: 10px; cursor: pointer; }
    </style>
</head>
<body>
    <h1>Добро пожаловать в KubeShop!</h1>

    <div class="product-list">
        <h2>Товары</h2>
        <div id="products">Загрузка товаров...</div>
    </div>

    <div class="cart">
        <h2>Ваша корзина (User: <span id="userIdDisplay">test_user</span>)</h2>
        <div id="cart-items">Корзина пуста.</div>
        <button onclick="checkout()">Оформить заказ</button>
    </div>

    <div class="order-details">
        <h2>Последний заказ</h2>
        <div id="last-order">Нет оформленных заказов.</div>
    </div>

    <script>
        const userId = "test_user"; // Имитация ID пользователя
        document.getElementById('userIdDisplay').innerText = userId;

        const CATALOG_SERVICE_URL = "http://localhost:8001"; // Будет заменено на Service IP/DNS в K8s
        const CART_SERVICE_URL = "http://localhost:8002"; // Будет заменено на Service IP/DNS в K8s
        const ORDER_SERVICE_URL = "http://localhost:8003"; // Будет заменено на Service IP/DNS в K8s

        async function fetchProducts() {
            try {
                const response = await fetch(`${CATALOG_SERVICE_URL}/products`);
                const products = await response.json();
                const productsDiv = document.getElementById('products');
                productsDiv.innerHTML = '';
                if (products.length === 0) {
                    productsDiv.innerText = 'Нет товаров в каталоге.';
                    return;
                }
                products.forEach(product => {
                    const div = document.createElement('div');
                    div.className = 'product-item';
                    div.innerHTML = `
                        ${product.name} - $${product.price} (В наличии: <span class="math-inline">\{product\.stock\}\)
<button onclick="addToCart({product.id}, 1)">Добавить в корзину</button>
`;
productsDiv.appendChild(div);
});
} catch (error) {
console.error('Ошибка при загрузке товаров:', error);
document.getElementById('products').innerText = 'Ошибка при загрузке товаров.';
}
}

        async function fetchCart() {
            try {
                const response = await fetch(`<span class="math-inline">\{CART\_SERVICE\_URL\}/cart/</span>{userId}`);
                const cartItems = await response.json();
                const cartItemsDiv = document.getElementById('cart-items');
                cartItemsDiv.innerHTML = '';
                if (cartItems.length === 0) {
                    cartItemsDiv.innerText = 'Корзина пуста.';
                    return;
                }
                cartItems.forEach(item => {
                    const div = document.createElement('div');
                    div.className = 'cart-item';
                    div.innerHTML = `
                        Продукт ID: ${item.product_id}, Количество: <span class="math-inline">\{item\.quantity\}
<button onclick="removeFromCart({item.product_id})">Удалить</button>
`;
cartItemsDiv.appendChild(div);
});
} catch (error) {
console.error('Ошибка при загрузке корзины:', error);
document.getElementById('cart-items').innerText = 'Ошибка при загрузке корзины.';
}
}

        async function addToCart(productId, quantity) {
            try {
                await fetch(`<span class="math-inline">\{CART\_SERVICE\_URL\}/cart/</span>{userId}/add`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ productId, quantity })
                });
                alert('Товар добавлен в корзину!');
                fetchCart();
            } catch (error) {
                console.error('Ошибка при добавлении в корзину:', error);
                alert('Ошибка при добавлении в корзину.');
            }
        }

        async function removeFromCart(productId) {
            try {
                await fetch(`<span class="math-inline">\{CART\_SERVICE\_URL\}/cart/</span>{userId}/remove`, {
                    method: 'DELETE',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ productId })
                });
                alert('Товар удален из корзины!');
                fetchCart();
            } catch (error) {
                console.error('Ошибка при удалении из корзины:', error);
                alert('Ошибка при удалении из корзины.');
            }
        }

        async function checkout() {
            try {
                const response = await fetch(`${ORDER_SERVICE_URL}/orders/create`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ userId })
                });
                const result = await response.text(); // Может быть JSON, может быть plain text
                if (response.ok) {
                    alert('Заказ успешно оформлен: ' + result);
                    document.getElementById('last-order').innerText = 'Заказ успешно оформлен: ' + result;
                    fetchCart(); // Обновить корзину после оформления заказа
                } else {
                    alert('Ошибка при оформлении заказа: ' + result);
                }
            } catch (error) {
                console.error('Ошибка при оформлении заказа:', error);
                alert('Ошибка при оформлении заказа. Проверьте консоль.');
            }
        }

        // Инициализация при загрузке страницы
        fetchProducts();
        fetchCart();
    </script>
</body>
</html>
```
frontend/nginx.conf
Nginx

# frontend/nginx.conf
events {
    worker_connections 1024;
}

http {
    include mime.types;
    default_type application/octet-stream;

    sendfile on;
    keepalive_timeout 65;

    server {
        listen 80;
        server_name localhost;

        root /usr/share/nginx/html;
        index index.html index.htm;

        location / {
            try_files $uri $uri/ /index.html;
        }
    }
}
Что нужно сделать (задачи)
Это список задач, которые вы должны выполнить для развертывания и тестирования приложения.

Настройка локальной среды:

Установить Docker Desktop (или Docker Engine + Compose).
Установить kubectl.
Установить Minikube или Kind (или иметь доступ к K8s кластеру).
Создание Dockerfile для каждого микросервиса:

Для Catalog Service (Python/FastAPI)
Для Cart Service (Node.js/Express)
Для Order Service (Java/Spring Boot)
Для Notification Service (Python/FastAPI)
Для Payment Service (Python/Flask)
Для Frontend (Nginx для статики)
Сборка Docker образов:

Собрать образы для всех ваших микросервисов.
Опубликовать их в Docker Hub или локальном репозитории, если используете Minikube/Kind.
Развертывание PostgreSQL в Kubernetes:

Создать PersistentVolume и PersistentVolumeClaim для хранения данных PostgreSQL.
Создать Deployment для PostgreSQL.
Создать Service для PostgreSQL.
Развертывание микросервисов в Kubernetes:

Для каждого микросервиса:
Создать Deployment.
Настроить переменные окружения (DATABASE_URL, URL-ы других микросервисов) для каждого микросервиса. Используйте Service Discovery в Kubernetes.
Создать Service (ClusterIP для внутренних, NodePort или LoadBalancer для фронтенда).
Убедиться, что микросервисы могут взаимодействовать друг с другом по именам сервисов (например, http://catalog-service:8001).
Развертывание фронтенда в Kubernetes:

Создать Deployment для фронтенда (используя Nginx для обслуживания статики).
Создать Service для фронтенда (например, NodePort или LoadBalancer), чтобы к нему можно было получить доступ извне кластера.
Использование Kubernetes ConfigMaps и Secrets:

Создать ConfigMap для хранения общих настроек, например, URL-ов сервисов (хотя для Service Discovery это не всегда нужно, но как практика полезно).
Создать Secret для учетных данных базы данных PostgreSQL (пароль).
Масштабирование (HPA - Horizontal Pod Autoscaler):

Установить Metric Server в кластер (если не установлен по умолчанию).
Создать HorizontalPodAutoscaler для одного или нескольких микросервисов (например, Catalog Service или Order Service), чтобы они автоматически масштабировались по загрузке CPU.
Использование Readiness и Liveness Probes:

Добавить livenessProbe и readinessProbe к манифестам Deployment для всех микросервисов. Это позволит Kubernetes правильно управлять жизненным циклом подов.
Проверка работоспособности:

Проверить доступность фронтенда через браузер.
Через фронтенд добавить товары в корзину.
Оформить заказ.
Проверить логи Order Service и Notification Service, чтобы убедиться, что взаимодействие между микросервисами происходит корректно.
Посмотреть, как поды масштабируются при нагрузке (можно имитировать нагрузку, например, с помощью hey или ApacheBench).
Как запускаются микросервисы нативно
Catalog Service (Python/FastAPI): uvicorn catalog_service:app --host 0.0.0.0 --port 8001
Cart Service (Node.js/Express): node cart_service.js
Order Service (Java/Spring Boot): ./mvnw spring-boot:run (если используете Maven Wrapper) или java -jar target/order-service-0.0.1-SNAPSHOT.jar (после сборки jar)
Notification Service (Python/FastAPI): uvicorn notification_service:app --host 0.0.0.0 --port 8005
Payment Service (Python/Flask): python payment_service.py
PostgreSQL: docker run --name some-postgres -e POSTGRES_PASSWORD=password -e POSTGRES_USER=user -e POSTGRES_DB=kube_shop_db -p 5432:5432 postgres
Frontend (Nginx): docker run -p 80:80 -v ./frontend/public:/usr/share/nginx/html -v ./frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro nginx (для локальной разработки)