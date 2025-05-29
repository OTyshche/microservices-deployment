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