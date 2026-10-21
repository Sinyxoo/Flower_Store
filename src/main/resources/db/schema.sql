-- ============================================================================
--  Структура базы данных «Электронный магазин цветов» (PostgreSQL).
--
--  Скрипт создаёт все таблицы приложения с нуля.
--  Запуск:
--      psql -U postgres -d flowerstore -f src/main/resources/db/schema.sql
--
--  Внимание: скрипт удаляет существующие таблицы. Если база уже наполнена
--  данными, используйте seed.sql (он только добавляет записи).
-- ============================================================================

DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS users;

-- ---------------------------------------------------------------------------
-- Категории каталога
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

-- ---------------------------------------------------------------------------
-- Пользователи: покупатели и администратор
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    phone         VARCHAR(255),
    address       VARCHAR(255),
    role          VARCHAR(255) NOT NULL DEFAULT 'USER'
);

-- ---------------------------------------------------------------------------
-- Товары (цветы и букеты)
-- ---------------------------------------------------------------------------
CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    category_id BIGINT REFERENCES categories (id) ON DELETE RESTRICT,
    price       NUMERIC(10, 2) NOT NULL,
    description VARCHAR(2000),
    image_url   VARCHAR(255),
    stock       INTEGER NOT NULL DEFAULT 0,
    visible     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_products_category ON products (category_id);

-- ---------------------------------------------------------------------------
-- Заказы. user_id — NULL для заказов гостей.
-- ---------------------------------------------------------------------------
CREATE TABLE orders (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT,
    customer_name VARCHAR(255),
    email         VARCHAR(255),
    phone         VARCHAR(255),
    address       VARCHAR(255),
    payment       VARCHAR(255),
    status        VARCHAR(255) NOT NULL DEFAULT 'NEW',
    total         NUMERIC(12, 2),
    created_at    TIMESTAMP
);

CREATE INDEX idx_orders_user ON orders (user_id);

-- ---------------------------------------------------------------------------
-- Позиции заказа: копия названия и цены на момент оформления
-- ---------------------------------------------------------------------------
CREATE TABLE order_item (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id BIGINT,
    name       VARCHAR(255),
    qty        INTEGER NOT NULL DEFAULT 0,
    price      NUMERIC(10, 2)
);

CREATE INDEX idx_order_item_order ON order_item (order_id);
