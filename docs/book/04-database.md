# Глава 4. База данных и JPA

> **Что мы уже знаем:** Spring создаёт объекты сам, настройки читаются из
> `application.properties`, а `@Transactional` гарантирует «всё или ничего»
> (глава 3). Теперь заглянем в хранилище: что такое база данных, как класс
> Java становится таблицей и как Spring Data превращает название метода в SQL.

---

## 4.1. Зачем нужна база данных

Память программы (переменные, объекты) исчезает, когда программа выключится.
Покупатель закрыл вкладку — корзина пропала, потому что она жила в оперативной
памяти сервера.

**База данных** — это программа, которая хранит данные **на диске**, в
**структурированном виде**, и умеет отвечать на вопросы быстро.

В нашем проекте база — **PostgreSQL**. Это промышленная СУБД: она хранит
данные, следит за их целостностью, умеет работать с несколькими пользователями
одновременно и восстанавливается после сбоя.

Всё это приложение общается с базой через слой репозиториев и **не пишет SQL
вручную** — об этом глава 4.4.

---

## 4.2. Таблицы, строки, столбцы, ключи

База состоит из **таблиц**. Таблица — как таблица в Excel, но с правилами.

Наша таблица `products` (из `schema.sql`):

```sql
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
```

Разберём по кусочкам:

| Кусочек | Что значит |
|---------|------------|
| `id BIGSERIAL PRIMARY KEY` | столбец id — **первичный ключ**: уникальный номер строки, выдаётся базой автоматически |
| `name VARCHAR(255) NOT NULL` | текст до 255 символов; `NOT NULL` — значение обязательно |
| `category_id BIGINT REFERENCES categories (id)` | **внешний ключ**: в этом столбце хранится номер категории из другой таблицы |
| `ON DELETE RESTRICT` | если удалить категорию, на которую ссылаются товары, база **запретит** удаление |
| `price NUMERIC(10, 2) NOT NULL` | число с точностью 10 цифр, из которых 2 после запятой (то есть `1234.56`) |
| `stock INTEGER NOT NULL DEFAULT 0` | целое число, по умолчанию 0 |
| `visible BOOLEAN NOT NULL DEFAULT TRUE` | «да/нет», по умолчанию «да» |

### Что даёт первичный ключ

* **Уникальность.** Двух строк с одинаковым `id` быть не может.
* **Ссылочная целостность.** Если на товар ссылается позиция заказа, база не
  даст удалить товар «насильно».
* **Простота связей.** Вместо «название товара» (которое может измениться)
  заказы хранят `product_id` — номер.

### Что такое связи и «один ко многим»

```
categories (1) ─────< (many) products
                          │
                          └──< (many) order_item ──> (1) orders
```

* **Один ко многим**: в одной категории — много товаров. В `products` есть
  столбец `category_id`.
* **Позиции заказа**: у одного заказа много позиций. В `order_item` есть
  `order_id`.

> **Здесь есть важная тонкость проекта.** В таблице `order_item` лежат
> `name` и `price` — **копии** названия и цены на момент заказа. Это
> «снимок» (snapshot). Зачем? Чтобы если завтра администратор поменяет цену
> розы с 4.50 на 6.00, **старые заказы не изменились**: покупатель платил
> 4.50, и в истории должно остаться 4.50. В таблице при этом остаётся
> `product_id` — на случай, если понадобится узнать, какой это был товар.

---

## 4.3. Модель проекта ↔ таблицы базы

Класс `Product` — это «чертёж таблицы»:

```java
@Entity
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public String name;
    @JsonIgnore @ManyToOne public Category category;
    @Column(nullable = false, precision = 10, scale = 2) public BigDecimal price;
    @Column(length = 2000) public String description;
    public String imageUrl;
    public int stock;
    public boolean visible = true;
}
```

| Java | SQL | Смысл |
|------|-----|-------|
| `@Entity` | таблица | «этот класс хранится в базе» |
| `@Id` | `PRIMARY KEY` | «это поле — идентификатор» |
| `@GeneratedValue(IDENTITY)` | `BIGSERIAL` | «номер выдаёт база сама» |
| `@Column(nullable = false)` | `NOT NULL` | «значение обязательно» |
| `@Column(precision = 10, scale = 2)` | `NUMERIC(10,2)` | «деньги: 2 знака после запятой» |
| `@ManyToOne` | `category_id ... REFERENCES` | «это поле — ссылка на другую таблицу» |
| `@Enumerated(STRING)` | `VARCHAR` | «перечисление храним текстом, а не номером» |

### `@JsonIgnore` — зачем модели знать про JSON

```java
@JsonIgnore @ManyToOne public Category category;
```

Мы **не хотим** отдавать в JSON объект категории целиком: во-первых, это
лишние данные, во-вторых, так появился бы бесконечный круг
`товар → категория → товары категории → ...`.

Вместо этого в `Product` есть два «вычисляемых» поля для JSON:

```java
public Long getCategoryId() { return category == null ? null : category.id; }
public String getCategoryName() { return category == null ? "" : category.name; }
```

Они не хранятся в базе — они просто вычисляются. Фронтенд получает
`categoryId` и `categoryName` и рисует подпись «Цветы» над названием товара.

> **Правило, которое стоит запомнить:** бизнес-модель не должна зависеть от
> формата ответа. Здесь мы пошли на небольшое исключение — добавили в модель
> два getter’а только для JSON. В больших проектах для этого делают отдельные
> DTO-классы, но для учебного проекта это избыточно.

---

## 4.4. Spring Data JPA: SQL, которого нет

Посмотрите на репозиторий целиком:

```java
public interface ProductRepo extends JpaRepository<Product, Long> {
    List<Product> findByVisibleTrue();

    /** Есть ли товары в категории. Category_Id — переход по связи category. */
    boolean existsByCategory_Id(Long categoryId);
}
```

Это **интерфейс**, и в нём нет ни одного SQL! Тем не менее Spring Data умеет
создать реализацию, которая:

* сохраняет объекты,
* находит их по `id`,
* выполняет запросы по «именам методов».

### Откуда берётся реализация интерфейса

Spring Boot при старте сканирует интерфейсы, наследующие `JpaRepository`, и
создаёт для каждого «волшебную» реализацию — прокси-объект. Выглядит так:

```java
ProductRepo products = new MagicProductRepoImplementation(dataSource, entityManager);
```

Реализацию генерирует библиотека **Spring Data JPA** на основе анализа
имени метода. Правила разбора имени:

```
findByVisibleTrue()
 │    │      └── условие: поле visible == true
 │    └───────── префикс "By"
 └────────────── "find" — вернуть список
```

```
existsByCategory_Id(Long categoryId)
 │       │        │    └── вложенное свойство: у товара есть объект category, у него поле id
 │       │        └─────── имя поля
 │       └──────────────── обход связи
 └──────────────────────── "exists" — вернуть true/false
```

Сравните с другими репозиториями:

```java
public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);   // найти по e-mail, без учёта регистра
    boolean existsByEmailIgnoreCase(String email);        // есть ли такой e-mail
}

public interface OrderRepo extends JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = "items")
    List<Order> findByUserIdOrderByIdDesc(Long userId);    // заказы пользователя, свежие первыми

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByOrderByIdDesc();                  // все заказы, свежие первыми

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdAndUserId(Long id, Long userId);  // один заказ конкретного пользователя
}
```

Разбор последнего: `findById` + `AndUserId` + `Long userId` → «найти заказ с
таким id **и** таким userId». Такой запрос не даст открыть чужой заказ, даже
если подставить его номер в адрес. Это важная мера безопасности, и она
покрыта тестом (`повторЧужогоЗаказаДаётОшибку404`).

`IgnoreCase` — «не учитывать регистр». Пользователь вводит `IVAN@MAIL.LOCAL` —
такой же, как `ivan@mail.local`.

### Что даёт `JpaRepository` «бесплатно»

Наследуя `JpaRepository<Product, Long>`, мы получаем готовые методы:

```java
Optional<Product> findById(Long id);      // найти по id (пусто — если нет)
List<Product> findAll();                 // все
Product save(Product product);           // сохранить (или обновить)
void delete(Product product);            // удалить
boolean existsById(Long id);             // есть ли
long count();                            // сколько всего
```

Именно поэтому интерфейсы репозиториев такие короткие — 3–20 строк вместо
сотни строк SQL.

---

## 4.5. Ленивая загрузка и `@EntityGraph`

В `Order` позиции объявлены так:

```java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
public List<OrderItem> items = new ArrayList<>();
```

Это связь «один ко many». Слово **lazy** (ленивая) означает: когда мы читаем
заказ, Hibernate **не** загружает позиции сразу, а откладывает «на потом» —
до первого обращения к `order.items`.

Зачем так? Потому что списки заказов бывают большими, а показывать позиции
нужно не всегда.

### Проблема N+1

Представьте: администратор открывает страницу заказов — их 50. Hibernate
выполняет:

1. `SELECT * FROM orders ORDER BY id DESC` — 50 заказов.
2. Для каждого заказа — отдельный `SELECT * FROM order_item WHERE order_id = ?`.
   Ещё 50 запросов!

Итого **51 запрос** вместо одного. Это и называют проблемой N+1.

### Решение: `@EntityGraph`

```java
@EntityGraph(attributePaths = "items")
List<Order> findAllByOrderByIdDesc();
```

`@EntityGraph` говорит Hibernate: «когда грузишь заказы, **сразу** загрузи
вместе с ними и позиции». Один запрос с `LEFT JOIN` вместо пятидесяти.

Обратите внимание: `@EntityGraph` стоит на **трёх** методах `OrderRepo` —
везде, где возвращаются заказы. Забыть его где-то — classic источник
«медленного» бага.

### `cascade = CascadeType.ALL`

Что произойдёт, если удалить заказ?

* Без `cascade` — в базе останутся «сиротские» позиции (или будет ошибка
  ограничения).
* С `CascadeType.ALL` — Hibernate сам удалит все позиции заказа.

Работает и в другую сторону: сохранили заказ с позициями — Hibernate сам
сохранит позиции. Именно поэтому в `OrderService.create` достаточно одного
`orders.save(order)`: все `order.items` уедут в базу автоматически.

---

## 4.6. Немного SQL: как это выглядит «под капотом»

Чтобы понимать, что происходит, вот SQL-эквиваленты наших запросов:

```sql
-- findByVisibleTrue()
SELECT * FROM products WHERE visible = TRUE;

-- findByUserIdOrderByIdDesc(7)
SELECT * FROM orders WHERE user_id = 7 ORDER BY id DESC;

-- findByIdAndUserId(42, 7)
SELECT * FROM orders WHERE id = 42 AND user_id = 7;

-- existsByCategory_Id(2)
SELECT COUNT(*) > 0 FROM products WHERE category_id = 2;
```

А вот как выглядит запрос Hibernate для `Order` с `@EntityGraph`:

```sql
SELECT o.*, i.* FROM orders o
LEFT JOIN order_item i ON o.id = i.order_id
ORDER BY o.id DESC;
```

---

## 4.7. Транзакции на уровне базы

Мы говорили о `@Transactional` в главе 3. Теперь посмотрим, что происходит
внутри.

### Что такое транзакция в SQL

```sql
BEGIN;                      -- начать
UPDATE products SET stock = stock - 2 WHERE id = 1;
INSERT INTO orders (...) VALUES (...);
INSERT INTO order_item (...) VALUES (...);
COMMIT;                     -- зафиксировать
```

Если между `BEGIN` и `COMMIT` что-то пошло не так (например, на второй
вставке закончился остаток), выполняется `ROLLBACK` — база возвращается к
состоянию до `BEGIN`.

### Уровни изоляции

Если с базой работают несколько пользователей одновременно (а в интернет-магазине
это всегда так), возникают проблемы:

* **Грязное чтение** — прочитали данные, которые ещё не подтверждены.
* **Неповторяемое чтение** — прочитали одно и то же дважды и получили разное.
* **Фантомное чтение** — при повторном чтении появились «новые» строки.

PostgreSQL по умолчанию использует уровень `READ COMMITTED` — «видно только
подтверждённые изменения». Этого достаточно для нашего магазина.

### Конкуренция за остаток

Классическая проблема: два покупателя одновременно кладут последнюю розу в
корзину.

```
Покупатель А: читает остаток 1 → списывает 1 → остаток 0   ✓
Покупатель Б: читает остаток 1 (до коммита А!) → списывает 1 → остаток -1  ✗
```

Чтобы этого не было, нужны «блокировки» (`:SELECT ... FOR UPDATE`) или
оптимистичное versioning. В нашем учебном проекте эта защита **не
реализована** — это честный пробел, о котором стоит знать: при большой
нагрузке остатки надо защищать. Транзакция гарантирует целостность *одной*
операции, но не защищает от гонки двух параллельных заказов.

---

## 4.8. SQL-скрипты проекта

В `src/main/resources/db/` лежат два скрипта:

| Файл | Что делает |
|------|------------|
| `schema.sql` | создаёт все таблицы с нуля (`DROP TABLE IF EXISTS` + `CREATE TABLE`) |
| `seed.sql` | добавляет демо-данные: администратора, покупателя, 4 категории, 8 товаров, 2 заказа |

`schema.sql` начинается с `DROP TABLE IF EXISTS` — **осторожно**: он удаляет
существующие таблицы вместе с данными. В комментарии скрипта это прямо
написано.

Администратор в `seed.sql` записан с уже посчитанным BCrypt-хешем:

```sql
INSERT INTO users (name, email, password_hash, role) VALUES
('Администратор', 'admin@flower.local',
 '$2a$10$OEtsjA5V7bJFtDg84cwVs.PKwFvcOwXcGqTGCAI7bAE880dhBnMqa', 'ADMIN');
```

Хеш нельзя «отменить», поэтому его посчитали один раз и вставили готовым.
Это нормальная практика: в скриптах развёртывания пароль не хранят открытым.

Скрипты запускаются **вручную**:

```bash
psql -U postgres -d flowerstore -f src/main/resources/db/schema.sql
psql -U postgres -d flowerstore -f src/main/resources/db/seed.sql
```

Именно поэтому в `application.properties` стоит `spring.sql.init.mode=never`:
приложение не должно самостоятельно удалять таблицы при запуске.

---

## 4.9. Главное из главы

1. База данных хранит данные на диске в таблицах; у таблиц есть столбцы,
   первичные ключи и связи.
2. JPA — «отображение объектов в таблицы»: аннотации `@Entity`, `@Id`,
   `@Column`, `@ManyToOne`, `@OneToMany` описывают, как класс Java станет
   таблицей.
3. Spring Data JPA генерирует SQL по **именам методов** репозитория:
   `findByVisibleTrue`, `findByIdAndUserId`, `existsByCategory_Id`.
4. `JpaRepository` даёт базовые методы (save, findById, findAll, delete).
5. Ленивая загрузка экономит время, но создаёт проблему N+1 запросов;
   `@EntityGraph` решает её.
6. `cascade = ALL` — «удали/сохрани заказ вместе с его позициями».
7. Снимок цены и названия в позиции заказа — пример «исторической
   неизменности».
8. Транзакция — «всё или ничего»; при ошибке изменения откатываются.

**Вопросы для самопроверки**

1. Зачем в `order_item` хранить копию названия и цены, если есть `product_id`?
2. Что произойдёт, если удалить `@EntityGraph` с метода
   `findAllByOrderByIdDesc`? Что именно станет хуже?
3. Как изменится SQL-запрос, если в `findByVisibleTrue` заменить `True` на
   `False`?
4. Почему `deleteCategory` в сервисе проверяет `existsByCategory_Id`, если в
   базе уже есть `ON DELETE RESTRICT`?
5. Что такое проблема N+1 и как её обнаружить, не читая код?

---

*Дальше: [Глава 5. Архитектура проекта](05-architecture.md) — как все эти
слои складываются вместе и что происходит с запросом пользователя от начала до
конца.*
