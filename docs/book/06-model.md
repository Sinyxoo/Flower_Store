# Глава 6. Модель данных

> **Что мы уже знаем:** мы понимаем архитектуру и то, как объект становится
> таблицей (главы 4–5). Теперь читаем **каждый класс** предметной области
> построчно: что в нём хранится, зачем и как это используется дальше.

Все файлы главы лежат в `src/main/java/shop/model/`.

---

## 6.1. Семь классов

| Класс | Таблица | Что описывает |
|-------|---------|---------------|
| `Category` | `categories` | категория каталога («Розы», «Тюльпаны») |
| `Product` | `products` | товар: название, цена, остаток, видимость |
| `User` | `users` | человек: покупатель или администратор |
| `Role` | — (enum) | роль: `USER` или `ADMIN` |
| `Order` | `orders` | заказ покупателя |
| `OrderItem` | `order_item` | одна позиция заказа |
| `OrderStatus` | — (enum) | статус заказа и правила переходов |

Два из них — **перечисления** (`enum`), они не хранятся в базе отдельными
таблицами, а хранятся в соответствующем столбце как текст.

---

## 6.2. `Category` — самая простая сущность

```java
@Entity
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public String name;
}
```

Что здесь видно:

* `@Entity` — «это таблица».
* `@Id` — поле `id` — первичный ключ.
* `@GeneratedValue(strategy = GenerationType.IDENTITY)` — номер выдаёт база
  (в PostgreSQL это `BIGSERIAL`).
* `@Column(nullable = false)` — название не может быть пустым на уровне базы.
  Это «страховка»: даже если забыть проверку в сервисе, база не пропустит
  пустое значение.
* Поля **публичные** — это стиль проекта. Обычно в Java делают поля
  `private` + геттеры/сеттеры, но здесь ради краткости оставили `public`.
  Геттеры появляются только там, где они нужны — например, `getCategoryName`
  в `Product` (глава 6.3).

> **Замечание для будущего программиста.** Публичные поля — это осознанное
> упрощение учебного проекта. В «взрослом» коде поля делают `private`,
> добавляют геттеры/сеттеры (или Lombok), и добавляют `equals`/`hashCode`/
> `toString`. Здесь мы сознательно не усложняем.

---

## 6.3. `Product` — товар

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

    // Эти два метода нужны только для JSON: страницы получают categoryId и categoryName
    public Long getCategoryId() { return category == null ? null : category.id; }
    public String getCategoryName() { return category == null ? "" : category.name; }
}
```

### Поля

| Поле | Тип | Ограничение в базе | Смысл |
|------|-----|--------------------|-------|
| `id` | `Long` | PK, авто | номер товара |
| `name` | `String` | `NOT NULL`, 255 | название |
| `category` | `Category` | `category_id` → `categories.id` | категория товара |
| `price` | `BigDecimal` | `NOT NULL`, `NUMERIC(10,2)` | цена |
| `description` | `String` | до 2000 символов | описание |
| `imageUrl` | `String` | 255 | адрес картинки (может быть пустым) |
| `stock` | `int` | `NOT NULL` | остаток на складе |
| `visible` | `boolean` | `NOT NULL`, default `TRUE` | показывать ли в каталоге |

### Почему `visible`, а не удаление

Товар можно **скрыть** с сайта, но не удалять из базы. Заказы, оформленные
давно, ссылаются на `product_id` — если бы товар удалялся, старые заказы
потеряли бы смысл. Поэтому «спрятать» — это `visible = false`, а «удалить» —
это настоящее удаление строки (и только если на товар нет заказов... впрочем,
в нашей базе нет такого ограничения, и это ещё один пробел).

### Два метода только для JSON

`@JsonIgnore` на поле `category` означает: «в JSON эту связь не отдавать».
Вместо неё отдаются два вычисляемых значения. Зачем так сложно? Чтобы не
возникло «рекурсии» в JSON и не лить лишние данные.

### `stock` — это «сколько осталось», а не «сколько продано»

Остаток уменьшается при оформлении заказа и возвращается при отмене.
Остатки — центральное понятие проекта, и именно вокруг них больше всего
тестов.

---

## 6.4. `User` — человек

```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String name;
    @Column(unique = true, nullable = false) public String email;
    @JsonIgnore public String passwordHash;
    public String phone = "";
    public String address = "";
    /** USER или ADMIN */
    @Enumerated(EnumType.STRING) public Role role = Role.USER;
}
```

### Зачем `@Table(name = "users")`

По умолчанию Hibernate назвал бы таблицу `user`. Но `USER` — **зарезервированное
слово** в PostgreSQL (а также в Oracle, MySQL). Если бы таблица называлась
`user`, запросы к ней падали бы с ошибкой синтаксиса. Поэтому явно указываем
`users`.

Это отличный пример того, зачем в проекте есть **SQL-скрипты**: в них видно
реальные имена таблиц.

### `passwordHash` и `@JsonIgnore`

Пароль хранится **только** в виде BCrypt-хеша, и поле `passwordHash` никогда
не попадает в JSON. Если бы `@JsonIgnore` не было, хеш пароля утекал бы в
браузер, в логи и в ответы API. В проекте есть тест
`парольВОтветеНеПоказывается`, который специально это проверяет.

### `@Enumerated(EnumType.STRING)`

Роль хранится текстом: `USER` или `ADMIN`, а не числами `0` и `1`. Так в
базе сразу видно, что записано, и при добавлении новой роли старые строки не
«сдвинутся».

### Поля по умолчанию `""`

`phone` и `address` могут быть пустыми (гость заказывает без регистрации),
поэтому они не `NOT NULL`, а просто пустые строки. Это удобно: в коде не надо
проверять `null` — достаточно сравнить с `""`.

---

## 6.5. `Role` — роль

```java
/** Роль пользователя: обычный покупатель или администратор. */
public enum Role {
    USER, ADMIN
}
```

Пять строк — и вся логика доступа. Добавить новую роль (например,
`MANAGER`) — добавить одно слово в enum и дописать проверки.

> **Почему роль хранится рядом с пользователем, а не «настраивается»?**
> Простые интернет-магазины обычно так и делают. Более сложные системы
> хранят права в отдельной таблице и назначают их группам — но это уже
> другая история и другой объём кода.

---

## 6.6. `Order` — заказ

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long userId;
    public String customerName;
    public String email;
    public String phone;
    public String address;
    public String payment;
    @Enumerated(EnumType.STRING) public OrderStatus status = OrderStatus.NEW;
    @Column(precision = 12, scale = 2) public BigDecimal total;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    public LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    public List<OrderItem> items = new ArrayList<>();

    /** Статусы, в которые заказ ещё можно перевести — для кнопок в админке. */
    public List<OrderStatus> getNextStatuses() {
        return status == null ? List.of() : status.next();
    }
}
```

### Поля заказа — и почему их так много

| Поле | Зачем |
|------|-------|
| `id` | номер заказа |
| `userId` | кто заказал; **null** для гостя |
| `customerName`, `email`, `phone`, `address` | контакты и адрес доставки |
| `payment` | способ оплаты: `cash` / `card` / `online` |
| `status` | текущий статус |
| `total` | итоговая сумма |
| `createdAt` | когда оформлен |
| `items` | позиции заказа |

**Почему контакты хранятся в заказе, а не берутся из `User`?** Гость заказывает
без регистрации — у него вообще нет записи в `users`. Даже для
зарегистрированного покупателя данные в заказе — это «снимок на момент
заказа»: если покупатель потом поменяет адрес, в старом заказе останется
тот адрес, по которому ему реально везли. Это та же идея, что со снимком
цены в позиции.

### Почему `userId`, а не `User user`

Связь `@ManyToOne User user` потребовала бы отдельной таблицы-связи или
JOIN'а при каждой загрузке. Кроме того, в `orders` нет ограничения
`REFERENCES users(id)` — заказ гостя просто не привязан ни к кому. Хранение
«просто номера» проще и достаточно: нам нужно знать, «чей это заказ», а не
«всё ли о нём знать».

Это компромисс, и его минус — база не проверит, что `user_id` существует.
Но зато нет лишних JOIN'ов и проблем с ленивой загрузкой.

### `@JsonFormat` — как дата выглядит в JSON

```java
@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
public LocalDateTime createdAt;
```

Без этой аннотации Jackson отдаст дату в формате `2026-10-21T13:01:49.123456`.
С ней — `2026-10-21 13:01`, что читается и выглядит аккуратно. Для JSON это
не страшно, но для учебного проекта приятно.

### `getNextStatuses()` — «какие кнопки показать»

Этот метод **не** хранит данные, а вычисляет: «из текущего статуса можно
перейти в такие-то». Фронтенд использует его, чтобы нарисовать кнопки
перехода. Логика переходов при этом не дублируется во фронтенде — она
определена один раз в `OrderStatus.next()`.

---

## 6.7. `OrderItem` — позиция заказа

```java
@Entity
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @JsonIgnore @ManyToOne(optional = false) @JoinColumn(name = "order_id") public Order order;
    public Long productId;
    public String name;
    public int qty;
    public BigDecimal price;
}
```

| Поле | Смысл |
|------|-------|
| `order` | **обратная** ссылка: в каком заказе эта позиция. `optional = false` — позиция без заказа невозможна |
| `productId` | какой это был товар (может быть `null`, если товар удалён) |
| `name` | **снимок** названия |
| `qty` | количество |
| `price` | **снимок** цены |

### `mappedBy` — двусторонняя связь

В `Order` написано `@OneToMany(mappedBy = "order", ...)`. Это значит: «у меня
список позиций, а связь хранится на их стороне — в поле `order` класса
`OrderItem`».

То есть `OrderItem.order` — «настоящая» сторона связи (в ней живёт
`order_id` в базе), а `Order.items` — «зеркало», собранное Hibernate'ом.

В коде это выглядит так:

```java
OrderItem item = new OrderItem();
item.order = order;          // указали, в каком заказе позиция
...
order.items.add(item);       // и добавили позицию в список заказа
```

Первую строку делать **обязательно**: без неё Hibernate не поймёт, к какому
заказу привязать позицию.

---

## 6.8. `OrderStatus` — статусы и правила переходов

```java
public enum OrderStatus {
    NEW, PROCESSING, DELIVERED, CANCELLED;

    /** В JSON статус уходит строчными буквами: new, processing, delivered, cancelled */
    @JsonValue
    public String toJson() { return name().toLowerCase(); }

    /** Статусы, в которые заказ ещё можно перевести из текущего. */
    public List<OrderStatus> next() {
        return switch (this) {
            case NEW -> List.of(PROCESSING, CANCELLED);
            case PROCESSING -> List.of(DELIVERED, CANCELLED);
            default -> List.of();   // «доставлен» и «отменён» менять нельзя
        };
    }

    /** Можно ли перейти из текущего статуса в статус «to» */
    public boolean canGo(OrderStatus to) {
        return next().contains(to);
    }
}
```

### `@JsonValue` — как enum попадает в JSON

По умолчанию Jackson отдаст `"NEW"`. С `@JsonValue` — `"new"`. Фронтенд
использует строчные значения:

```javascript
const STATUS = { new: 'Новый', processing: 'В обработке', delivered: 'Доставлен', cancelled: 'Отменён' };
```

Стало быть: **один язык статусов на сервере** (`OrderStatus`) и **человеческие
названия** — только во фронтенде. На сервере нет строки «В обработке», есть
`PROCESSING`.

### `switch`-выражение — современный Java

```java
return switch (this) {
    case NEW -> List.of(PROCESSING, CANCELLED);
    case PROCESSING -> List.of(DELIVERED, CANCELLED);
    default -> List.of();
};
```

Это `switch`, записанный как **выражение** (значение). Старый синтаксис
выглядел бы так:

```java
switch (this) {
    case NEW: return List.of(PROCESSING, CANCELLED);
    case PROCESSING: return List.of(DELIVERED, CANCELLED);
    default: return List.of();
}
```

`switch`-выражение введён в Java 14 и в новом коде читается лучше: сразу
видно, что метод **возвращает** результат.

### Где живут правила переходов

**Только здесь.** Ни в контроллере, ни во фронтенде, ни в базе. Одна
«таблица переходов»:

```
NEW        → PROCESSING, CANCELLED
PROCESSING → DELIVERED, CANCELLED
DELIVERED  → (никого)
CANCELLED  → (никого)
```

Проверка `canGo` используется в `OrderService.changeStatus`:

```java
if (!order.status.canGo(newStatus)) throw ApiException.conflict("Недопустимый переход статуса");
```

А список доступных кнопок — в `Order.getNextStatuses()` для фронтенда.

> **Общий принцип (очень важный для тестируемости):** бизнес-правило должно
> быть записано **один раз**. Если оно есть и в сервисе, и в контроллере, и в
> JavaScript — рано или поздно они разойдутся, и баг будет найден
> пользователями, а не тестами.

---

## 6.9. Как классы связаны между собой

```
        Category
            │  (у товара одна категория)
            ▼
        Product ◄──────────┐
            │             │  (в позиции заказа — id товара и снимок цены)
            │             │
            │        OrderItem ──┐
            │             │       │
            ▼             │       │  (у заказа много позиций)
          Order ──────────┘       │
            │                     │
            │ userId (null = гость)│
            ▼                     │
          User ◄──────────────────┘
            │
            └── role: Role (USER | ADMIN)
```

И отдельно — `Order.status: OrderStatus`, где сам `OrderStatus` знает
допустимые переходы.

---

## 6.10. Что проверяется тестами модели

| Класс | Файл теста | Что проверяем |
|-------|-------------|---------------|
| `OrderStatus` | `OrderStatusTest` | все переходы, запрет «доставлен → новый», `toJson()` |
| `Order` | `OrderTest` | `getNextStatuses()`, сумма, `items` |
| `Product` | `ProductTest` | `getCategoryId()` / `getCategoryName()`, в том числе при `null`-категории |
| `ApiException` | `ApiExceptionTest` | коды 400/401/403/404/409, `check()`, `blank()` |
| `ErrorHandler` | `ErrorHandlerTest` | JSON для `/api/...`, редиректы для форм, кодирование текста |

Эти тесты — самые быстрые в проекте (миллисекунды): никакого контекста
Spring, никакой базы, только обычные объекты.

---

## 6.11. Главное из главы

1. Модель — это «предметная область»: классы, которые описывают данные
   магазина и правила, живущие внутри самих данных (например, переходы
   статусов).
2. `@Entity`, `@Id`, `@Column`, `@ManyToOne`, `@OneToMany`, `@Enumerated` —
   «как класс становится таблицей».
3. `BigDecimal` для денег, `LocalDateTime` для дат, `enum` для конечных
   наборов значений.
4. «Снимки» (копии названия и цены в позиции заказа, контакты в заказе) —
   защита истории от изменений.
5. `@JsonIgnore` защищает приватные данные (хеш пароля, связи) от утечки в
   JSON.
6. Правила переходов статусов записаны **в одном месте** — в `OrderStatus`.
7. `@Table(name = "users")` — пример того, как зарезервированные слова SQL
   требуют явного имени таблицы.

**Вопросы для самопроверки**

1. Зачем в `Order` есть и `userId`, и `customerName`/`email`/`phone`?
2. Что произойдёт, если убрать `@JsonIgnore` с `passwordHash`? Назовите
   последствия хотя бы два.
3. Почему `Category` не имеет метода `getName()`-подобного JSON-метода, а
   `Product` — имеет?
4. В чём разница между «скрыть товар» и «удалить товар» с точки зрения
   истории заказов?
5. Где в проекте записано правило «из отменённого заказа нельзя перейти в
   новый»? Почему не в контроллере?

---

*Дальше: [Глава 7. Сервисный слой](07-services.md) — главная глава о
бизнес-логике и о том, как она устроена.*
