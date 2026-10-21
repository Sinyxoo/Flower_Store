-- ============================================================================
--  Демо-данные «Электронного магазина цветов».
--
--  Скрипт можно выполнить на пустой базе, созданной скриптом schema.sql:
--      psql -U postgres -d flowerstore -f src/main/resources/db/seed.sql
--
--  Логин администратора: admin@flower.local / admin123
--  (хеш пароля совпадает с тем, который создаёт класс SeedData при запуске)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Администратор
-- ---------------------------------------------------------------------------
INSERT INTO users (name, email, password_hash, phone, address, role)
VALUES ('Администратор',
        'admin@flower.local',
        '$2a$10$OEtsjA5V7bJFtDg84cwVs.PKwFvcOwXcGqTGCAI7bAE880dhBnMqa',
        '',
        '',
        'ADMIN');

-- ---------------------------------------------------------------------------
-- Покупатель (пароль: user123)
-- ---------------------------------------------------------------------------
INSERT INTO users (name, email, password_hash, phone, address, role)
VALUES ('Иван Петров',
        'ivan@mail.local',
        '$2a$10$OEtsjA5V7bJFtDg84cwVs.PKwFvcOwXcGqTGCAI7bAE880dhBnMqa',
        '+375291112233',
        'Минск, ул. Центральная, 1',
        'USER');

-- ---------------------------------------------------------------------------
-- Категории
-- ---------------------------------------------------------------------------
INSERT INTO categories (name) VALUES ('Розы');
INSERT INTO categories (name) VALUES ('Тюльпаны');
INSERT INTO categories (name) VALUES ('Букеты');
INSERT INTO categories (name) VALUES ('Горшечные');

-- ---------------------------------------------------------------------------
-- Товары
-- ---------------------------------------------------------------------------
INSERT INTO products (name, category_id, price, description, image_url, stock, visible)
SELECT 'Роза красная, 60 см', id, 4.50, 'Классическая красная роза с длинным стеблем.', NULL, 100, TRUE
FROM categories WHERE name = 'Розы';

INSERT INTO products (name, category_id, price, description, image_url, stock, visible)
SELECT 'Роза кремовая, 50 см', id, 3.80, 'Нежный кремовый оттенок.', NULL, 40, TRUE
FROM categories WHERE name = 'Розы';

INSERT INTO products (name, category_id, price, description, image_url, stock, visible)
SELECT 'Тюльпаны микс, 15 шт.', id, 36.00, 'Весенний букет из разноцветных тюльпанов.', NULL, 12, TRUE
FROM categories WHERE name = 'Тюльпаны';

INSERT INTO products (name, category_id, price, description, image_url, stock, visible)
SELECT 'Букет «Нежность»', id, 95.00, 'Пионовидные розы, эустома, эвкалипт.', NULL, 5, TRUE
FROM categories WHERE name = 'Букеты';

INSERT INTO products (name, category_id, price, description, image_url, stock, visible)
SELECT 'Букет «Летний луг»', id, 70.00, 'Полевые цветы в крафтовой упаковке.', NULL, 0, TRUE
FROM categories WHERE name = 'Букеты';

INSERT INTO products (name, category_id, price, description, image_url, stock, visible)
SELECT 'Подсолнухи, 5 шт.', id, 45.00, 'Яркий солнечный букет.', NULL, 15, TRUE
FROM categories WHERE name = 'Букеты';

INSERT INTO products (name, category_id, price, description, image_url, stock, visible)
SELECT 'Орхидея фаленопсис', id, 85.00, 'Орхидея в керамическом кашпо.', NULL, 8, TRUE
FROM categories WHERE name = 'Горшечные';

INSERT INTO products (name, category_id, price, description, image_url, stock, visible)
SELECT 'Гортензия белая', id, 60.00, 'Скрыта с сайта.', NULL, 0, FALSE
FROM categories WHERE name = 'Горшечные';

-- ---------------------------------------------------------------------------
-- Пример заказа (номер 1 — оформлен, номер 2 — в обработке)
-- ---------------------------------------------------------------------------
INSERT INTO orders (id, user_id, customer_name, email, phone, address, payment, status, total, created_at)
VALUES (1, (SELECT id FROM users WHERE email = 'ivan@mail.local'),
        'Иван Петров', 'ivan@mail.local', '+375291112233', 'Минск, ул. Центральная, 1',
        'cash', 'DELIVERED', 47.80, TIMESTAMP '2026-10-01 12:30:00');

INSERT INTO orders (id, user_id, customer_name, email, phone, address, payment, status, total, created_at)
VALUES (2, NULL, 'Гость', 'guest@mail.local', '+375297654321', 'Гомель, пр. Ленина, 5',
        'cash', 'NEW', 36.00, TIMESTAMP '2026-10-20 18:05:00');

INSERT INTO order_item (order_id, product_id, name, qty, price)
SELECT 1, p.id, p.name, 2, p.price FROM products p WHERE p.name = 'Роза кремовая, 50 см';

INSERT INTO order_item (order_id, product_id, name, qty, price)
SELECT 1, p.id, p.name, 1, p.price FROM products p WHERE p.name = 'Роза красная, 60 см';

INSERT INTO order_item (order_id, product_id, name, qty, price)
SELECT 2, p.id, p.name, 1, p.price FROM products p WHERE p.name = 'Тюльпаны микс, 15 шт.';
