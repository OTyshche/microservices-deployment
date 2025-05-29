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