# Глава 7. Сервисный слой

> **Что мы уже знаем:** слои, модель данных, правило «бизнес-логика живёт в
> сервисах» (главы 5–6). Эта глава — сердце проекта: здесь написана **вся**
> логика магазина, и именно эти классы покрыты модульными тестами.

Четыре сервиса:

| Сервис | Отвечает за | Тест |
|--------|-------------|------|
| `CartService` | корзину: добавить, изменить, убрать, посчитать | `CartServiceTest` (24 теста) |
| `OrderService` | оформление заказа, статусы, список заказов | `OrderServiceTest` (34 теста) |
| `CatalogService` | каталог: видимые товары, категории, валидация | `CatalogServiceTest` (33 теста) |
| `AuthService` | регистрация, вход, роли, профиль | `AuthServiceTest` (28 теста) |

---

## 7.1. Общий вид сервиса

Все четыре сервиса устроены одинаково:

```java
@Service
public class CartService {
    private final ProductRepo products;

    public CartService(ProductRepo products) {
        this.products = products;
    }
    ...
}
```

Что здесь важно:

1. `@Service` — «Spring, создай меня и дай зависимости».
2. `private final` + конструктор — зависимость **внедряется** и не может
   смениться. Это делает класс предсказуемым и легко тестируемым: в тесте мы
   передаём мок репозитория — и сервис об этом не знает.
3. Внутри — **нет ни одного упоминания HTTP**: ни `HttpServletRequest`, ни
   `@ResponseBody`. Только `HttpSession` (это не веб-инструмент, а просто
   «ящик с атрибутами», и его легко подменить — см. главу 10).

### Как сервис «сообщает» об ошибке

Единый механизм на весь проект — `ApiException`:

```java
if (wanted > product.stock)
    throw ApiException.conflict("Недостаточно на складе: доступно " + product.stock + " шт.");
```

Сервис **не знает**, что будет с этой ошибкой: её поймает либо контроллер
(`FormController.result`), либо `ErrorHandler`, и превратит в ответ
пользователю. Это «программистское правило» в чистом виде: сервис говорит
«что не так», а решает вызывающий.

---

## 7.2. `CartService` — корзина

### Главное решение: что хранится в корзине

```java
/** Корзина лежит в HTTP-сессии как таблица: id товара → количество. */
```

Корзина — это `Map<Long, Integer>`: **идентификатор товара → количество**.
Ни названий, ни цен.

Почему так:

* **Цены всегда актуальны.** Когда товар подорожал, корзина покажет новую
  цену, потому что цену мы берём из базы при отображении.
* **Нельзя подделать.** Клиент не контролирует содержимое сессии.
* **Компактно.** 100 позиций — это 100 пар чисел.

### `raw()` — «сырая» корзина

```java
@SuppressWarnings("unchecked")
public Map<Long, Integer> raw(HttpSession session) {
    Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
    if (cart == null) {
        cart = new LinkedHashMap<>();
        session.setAttribute("cart", cart);
    }
    return cart;
}
```

* `session.getAttribute("cart")` — достать из сессии; если там ещё ничего нет —
  вернётся `null`.
* Создаём пустую `LinkedHashMap` (с сохранением порядка: товары в корзине
  показываются в том порядке, в котором их добавили) и **кладём её в
  сессию**.
* `@SuppressWarnings("unchecked")` — предупреждение компилятора: «ты
  приводишь `Object` к `Map<Long, Integer>`, а я не могу проверить, что там
  действительно такой тип». Это безопасный каст: мы сами туда кладем.

Название `raw` («сырой») честно говорит: это данные **как есть**, без
проверок и без актуальных цен.

### `viewAndNormalize()` — «состав корзины с актуальными данными»

```java
public View viewAndNormalize(HttpSession session) {
    Map<Long, Integer> cart = raw(session);
    if (cart.isEmpty()) return new View(List.of(), BigDecimal.ZERO);

    // Все товары корзины забираем одним запросом, а не по одному на позицию.
    Map<Long, Product> byId = new HashMap<>();
    for (Product product : products.findAllById(cart.keySet())) byId.put(product.id, product);

    List<Item> items = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;

    Iterator<Map.Entry<Long, Integer>> iterator = cart.entrySet().iterator();
    while (iterator.hasNext()) {
        Map.Entry<Long, Integer> entry = iterator.next();
        Product product = byId.get(entry.getKey());
        if (product == null || !product.visible || product.stock <= 0) {
            iterator.remove();
            continue;
        }
        if (entry.getValue() > product.stock) entry.setValue(product.stock);

        BigDecimal sum = product.price.multiply(BigDecimal.valueOf(entry.getValue()));
        items.add(new Item(product.id, product.name, product.price, entry.getValue(),
                product.stock, product.imageUrl, sum));
        total = total.add(sum);
    }
    return new View(items, total);
}
```

Разберём по шагам — здесь видна инженерная мысль.

**Шаг 1. Пустая корзина.** Возвращаем пустой список и ноль. Нет смысла делать
запрос в базу.

**Шаг 2. Один запрос вместо N.** `products.findAllById(cart.keySet())` — Hibernate
выполнит **один** SQL-запрос вида

```sql
SELECT * FROM products WHERE id IN (1, 5, 9);
```

а не по запросу на каждую позицию. Это называется «загрузить пачкой»
(batch loading). Разница: 1 запрос вместо 10.

**Шаг 3. Убираем «мёртвые» позиции.** Пока товар был в корзине, он мог:

* исчезнуть из базы (`product == null`);
* стать скрытым администратором (`!product.visible`);
* закончиться (`product.stock <= 0`).

Все три случая → **удаляем позицию из корзины**. Обратите внимание: мы
меняем саму корзину в сессии, а не только «вид». Поэтому метод и называется
`viewAndNormalize` — «показать и привести в порядок».

**Шаг 4. Урезаем количество до остатка.** Если в корзине 5 штук, а на складе
3 — ставим 3. Покупатель увидит правду.

**Шаг 5. Считаем суммы.** Для каждой позиции — `цена × количество`, и
общая сумма.

**Шаг 6. Возвращаем `View`.**

```java
public record Item(Long productId, String name, BigDecimal price, int qty, int stock,
                   String imageUrl, BigDecimal sum) {}
public record View(List<Item> items, BigDecimal total) {}
```

`View` — это «представление корзины для фронтенда»: список позиций и итог.
`Item` — одна позиция. Обратите внимание, в `Item` есть `stock` — чтобы
фронтенд мог ограничить поле количества (`max="${p.stock}"`).

### `add()` — добавить товар

```java
public void add(HttpSession session, Long productId, int qty) {
    check(productId != null && qty > 0, "Некорректное количество");
    Product product = findVisible(productId);
    Map<Long, Integer> cart = raw(session);
    int wanted = cart.getOrDefault(productId, 0) + qty;
    checkStock(product, wanted);
    cart.put(productId, wanted);
}
```

Порядок проверок важен:

1. `check(productId != null && qty > 0, ...)` — входные данные. Ошибка 400.
2. `findVisible(productId)` — товар существует и видим. Ошибка 404.
3. `wanted = уже_в_корзине + qty` — **суммируем**, а не заменяем. Добавили 2,
   потом ещё 3 — будет 5, а не 3.
4. `checkStock(product, wanted)` — хватает ли остатка **с учётом того, что уже
   в корзине**. Ошибка 409.
5. `cart.put(...)` — записываем.

> **Важная деталь про порядок:** проверка остатка идёт **после** суммирования.
> Частая ошибка — проверить остаток только для добавляемого количества.
> Тогда можно было бы добавить 5 штук при остатке 3, потом ещё 5 — и в
> корзине оказалось бы 10 при остатке 3. Тест
> `добавлениеСПревышениемУжеДобавленногоОтклоняется` проверяет именно этот
> случай.

### `setQty()`, `remove()`, `clear()`

```java
public void setQty(HttpSession session, Long productId, int qty) {
    check(qty > 0, "Некорректное количество");
    Product product = findVisible(productId);
    checkStock(product, qty);
    raw(session).put(productId, qty);
}

public void remove(HttpSession session, Long productId) {
    raw(session).remove(productId);
}

public void clear(HttpSession session) {
    raw(session).clear();
}
```

`setQty` — это «поставить ровно столько» (в отличие от `add`, который
прибавляет). `remove` и `clear` ничего не проверяют: удалить то, чего нет, —
не ошибка (идемпотентность).

### Приватные помощники

```java
private Product findVisible(Long id) {
    return products.findById(id).filter(p -> p.visible)
            .orElseThrow(() -> ApiException.notFound(ApiException.PRODUCT_NOT_FOUND));
}

private void checkStock(Product product, int wanted) {
    if (wanted > product.stock)
        throw ApiException.conflict("Недостаточно на складе: доступно " + product.stock + " шт.");
}
```

`private` — «этот метод только для внутреннего использования». Тесты
не вызывают их напрямую: снаружи они не видны.

---

## 7.3. `OrderService` — заказы

### `create()` — самое важное место в проекте

```java
@Transactional
public Order create(User user, Map<Long, Integer> cart, OrderData data) {
    check(!blank(data.customerName()), "Укажите имя");
    check(!blank(data.email()) || !blank(data.phone()), "Укажите e-mail или телефон");
    check(!blank(data.address()), "Укажите адрес доставки");
    check(!cart.isEmpty(), "Корзина пуста");

    Order order = new Order();
    order.userId = user == null ? null : user.id;
    order.customerName = data.customerName().trim();
    order.email = blank(data.email()) ? "" : data.email().trim();
    order.phone = blank(data.phone()) ? "" : data.phone().trim();
    order.address = data.address().trim();
    order.payment = blank(data.payment()) ? "cash" : data.payment();
    order.status = OrderStatus.NEW;
    order.createdAt = LocalDateTime.now();

    BigDecimal total = BigDecimal.ZERO;
    for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
        Product product = products.findById(entry.getKey()).filter(p -> p.visible)
                .orElseThrow(() -> ApiException.notFound("Товар из корзины больше не продаётся"));
        int qty = entry.getValue();
        if (product.stock < qty)
            throw ApiException.conflict("Недостаточно на складе: «" + product.name + "», доступно " + product.stock + " шт.");

        // Товар уже загружен в этой транзакции — Hibernate сам сохранит новый остаток.
        product.stock = product.stock - qty;

        OrderItem item = new OrderItem();
        item.order = order;
        item.productId = product.id;
        item.name = product.name;
        item.qty = qty;
        item.price = product.price;
        order.items.add(item);

        total = total.add(product.price.multiply(BigDecimal.valueOf(qty)));
    }
    order.total = total;
    return orders.save(order);
}
```

#### Разбор по частям

**Проверка формы (4 строки).**

```java
check(!blank(data.customerName()), "Укажите имя");
check(!blank(data.email()) || !blank(data.phone()), "Укажите e-mail или телефон");
```

`||` — «или». Достаточно **одного** из двух: e-mail **или** телефон. Это
правило из ТЗ: заказ оформляется без регистрации.

`blank(...)` — helper из `ApiException`:

```java
public static boolean blank(String text) {
    return text == null || text.isBlank();
}
```

`null` **или** строка из одних пробелов. Обратите внимание на `||` в логике:
`isBlank()` вернёт `true` и для `null`, поэтому проверять `null` отдельно не
нужно — но автор написал оба варианта для ясности.

**Заполнение заказа.** Все строки обрезаются через `.trim()` — пользователь
вводит « Иван », а в базу идёт «Иван». Для пустых e-mail/phone кладём `""`, а
не `null` (чтобы не ловить `NullPointerException` в других местах).

`order.userId = user == null ? null : user.id;` — гостю `userId` не нужен.
Тернарный оператор `? :` — «если условие, то первое значение, иначе второе».

**Создание позиций.** Для каждой позиции корзины:

1. Загружаем товар. Если товара нет или он скрыт — ошибка 404 «Товар из
   корзины больше не продаётся». Это важный сценарий: корзина могла
   «устареть».
2. Проверяем остаток **здесь**, ещё раз — остаток могли потратить, пока
   покупатель заполнял форму. Сообщение содержит **название товара** — так
   покупатель понимает, о ком речь.
3. **Списываем**: `product.stock = product.stock - qty;`
4. Создаём позицию заказа со **снимками** имени и цены.
5. Добавляем позицию в `order.items`.
6. Накапливаем сумму.

**Комментарий про Hibernate** — очень важный:

```java
// Товар уже загружен в этой транзакции — Hibernate сам сохранит новый остаток.
product.stock = product.stock - qty;
```

Мы **не вызываем** `products.save(product)`. Почему? Потому что `product` —
объект, **управляемый Hibernate** (loaded в this session). Hibernate отслеживает
изменения его полей и сам выполнит `UPDATE` в конце транзакции. Это называется
**dirty checking** (проверка изменений).

Это одна из самых «магических» вещей в JPA, и она требует понимания: если бы
товар был загружен **вне** текущей транзакции (а `open-in-view=false`!), то
изменение могло бы не сохраниться. В нашем коде вызов происходит внутри
`@Transactional`-метода, поэтому всё работает.

**Сохранение заказа.** `orders.save(order)` — Hibernate сохранит и заказ, и все
его позиции (благодаря `cascade = CascadeType.ALL`).

#### Почему `@Transactional` здесь обязателен

Представьм: корзина на 200 Br, остаток списался, но при сохранении заказа
база «упала» (например, обрыв связи). Без транзакции:

* остаток **уже списан** (первый UPDATE прошёл);
* заказ **не создан**.

Покупатель потерял деньги, магазин — товар. С транзакцией: откат, и всё
как было.

Именно поэтому ошибки внутри `create` **не ловятся** try/catch: поймать
исключение здесь означало бы «проглотить» его, и Hibernate решил бы, что всё
прошло успешно, и зафиксировал бы частичные изменения.

#### `OrderData` — входные данные

```java
public record OrderData(String customerName, String email, String phone, String address, String payment) {}
```

`record` вместо пяти отдельных параметров метода — потому что пять строковых
параметров легко перепутать местами (и компилятор не поможет). С `record` —
`data.email()` вместо `email`.

### `changeStatus()` — смена статуса

```java
@Transactional
public Order changeStatus(Long orderId, String statusName) {
    Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound(ApiException.ORDER_NOT_FOUND));

    if (blank(statusName)) throw ApiException.badRequest("Неизвестный статус");
    OrderStatus newStatus;
    try {
        newStatus = OrderStatus.valueOf(statusName.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
        throw ApiException.badRequest("Неизвестный статус");
    }
    if (!order.status.canGo(newStatus)) throw ApiException.conflict("Недопустимый переход статуса");

    if (newStatus == OrderStatus.CANCELLED) {
        for (OrderItem item : order.items) {
            Product product = products.findById(item.productId).orElse(null);
            if (product != null) {
                product.stock = product.stock + item.qty;
            }
        }
    }
    order.status = newStatus;
    return orders.save(order);
}
```

**Разбор статуса из строки.** Фронтенд присылает `status=delivered`.
`OrderStatus.valueOf(...)` требует имя **точно** как в enum (`DELIVERED`), поэтому:

```java
statusName.trim().toUpperCase()   // "delivered" → "DELIVERED"
```

Если строка не соответствует ни одному статусу, `valueOf` бросает
`IllegalArgumentException` — ловим и превращаем в понятную ошибку 400.

**Проверка перехода.** `order.status.canGo(newStatus)` — правило из `OrderStatus`
(глава 6.8). Недопустимый переход → 409.

**Возврат на склад при отмене.** Если новый статус — `CANCELLED`, для каждой
позиции заказа загружаем товар и **прибавляем** количество к остатку.

Заметим важную деталь: `products.findById(item.productId).orElse(null)` и
проверка `if (product != null)`. Если товар был удалён из базы, мы **не**
падаем — просто нечего возвращать. Это «мягкая» обработка: лучше потерять
возврат одного товара, чем сломать отмену всего заказа.

> **Почему `changeStatus` тоже `@Transactional`?** Возврат на склад — это
> несколько изменений. Если третье обновление упадёт, без транзакции первые
> два останутся: товары частично вернулись, а статус не изменился. С
> транзакцией — откат всего.

### Остальные методы

```java
public List<Order> ordersOf(User user) {
    return orders.findByUserIdOrderByIdDesc(user.id);
}

public List<Order> allOrders() {
    return orders.findAllByOrderByIdDesc();
}

public Order findOne(Long orderId, Long userId) {
    return orders.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> ApiException.notFound(ApiException.ORDER_NOT_FOUND));
}
```

`findOne` — важный метод безопасности: он ищет заказ **с одновременным**
условием по `id` и `userId`. Покупатель не может открыть чужой заказ, даже
подставив его номер в адрес. Ошибка — 404 (а не 403): мы даже не сообщаем, что
такой заказ существует.

---

## 7.4. `CatalogService` — каталог и админка

### `visibleProducts()` — каталог для покупателя

```java
public List<Product> visibleProducts(Long categoryId, String sort) {
    Stream<Product> stream = products.findByVisibleTrue().stream();
    if (categoryId != null) {
        stream = stream.filter(p -> p.category != null && categoryId.equals(p.category.id));
    }
    Comparator<Product> byPrice = Comparator.comparing((Product p) -> p.price);
    if ("price".equals(sort)) {
        stream = stream.sorted(byPrice);
    } else if ("price_desc".equals(sort)) {
        stream = stream.sorted(byPrice.reversed());
    } else {
        stream = stream.sorted(Comparator.comparing((Product p) -> p.id));
    }
    return stream.toList();
}
```

* `findByVisibleTrue()` — в базе только видимые товары. Скрытые товары вообще
  не попадают в выборку — это правильнее, чем фильтровать в памяти.
* Фильтр по категории — в памяти, через `stream`. Для учебного проекта это
  нормально: товаров немного. В большом проекте фильтр ушёл бы в SQL
  (`findByVisibleTrueAndCategoryId`).
* Сортировка — три варианта: по возрастанию цены, по убыванию, по id (порядок
  добавления). Неизвестное значение сортировки не приводит к ошибке, а
  включает вариант по умолчанию.

### Валидация товара — `saveProduct()`

```java
public Product saveProduct(Long id, ProductData data) {
    check(!blank(data.name()), "Название не может быть пустым");
    check(data.price() != null, "Укажите цену");
    check(data.price().signum() >= 0, "Цена не может быть отрицательной");
    check(data.stock() != null, "Укажите остаток");
    check(data.stock() >= 0, "Остаток не может быть отрицательным");
    Category category = null;
    if (data.categoryId() != null) {
        category = categories.findById(data.categoryId()).orElse(null);
    }
    check(category != null, "Выберите категорию");

    Product product = id == null ? new Product() : findProduct(id);
    product.name = data.name().trim();
    product.category = category;
    product.price = data.price().setScale(2, RoundingMode.HALF_UP);
    product.description = data.description();
    product.imageUrl = blank(data.imageUrl()) ? null : data.imageUrl().trim();
    product.stock = data.stock();
    product.visible = data.visible();
    return products.save(product);
}
```

Разберём необычные места:

* **`signum()`** — метод `BigDecimal`, возвращающий знак числа: −1, 0 или 1.
  `price.signum() >= 0` означает «цена неотрицательная». Для `double` можно
  было бы написать `price >= 0`, но для `BigDecimal` сравнение с `0` запрещено
  (оно неоднозначно), поэтому используют `signum()`.
* **`setScale(2, RoundingMode.HALF_UP)`** — привести к двум знакам после запятой,
  округляя по правилам «половина вверх». Если админ ввёл `4.5` → станет `4.50`.
  Это важно: в базе столбец `NUMERIC(10,2)`, и приведение делает данные
  единообразными.
* **`id == null` → новый товар, иначе редактирование.** Один метод и две
  операции — типичный приём, чтобы не дублировать код. Соответствующий тест:
  `сохранениеСNullIdСоздаётНовыйТовар`.
* **`imageUrl = blank(...) ? null : ...`** — пустой URL превращаем в `null`,
  чтобы в базе не копились пустые строки.

### `saveCategory()` и `deleteCategory()`

```java
public Category saveCategory(Long id, String name) {
    check(!blank(name), "Название категории не может быть пустым");
    Category category = id == null
            ? new Category()
            : categories.findById(id).orElseThrow(() -> ApiException.notFound(ApiException.CATEGORY_NOT_FOUND));
    category.name = name.trim();
    return categories.save(category);
}

public void deleteCategory(Long id) {
    Category category = categories.findById(id)
            .orElseThrow(() -> ApiException.notFound(ApiException.CATEGORY_NOT_FOUND));
    if (products.existsByCategory_Id(id))
        throw ApiException.conflict("Нельзя удалить категорию, в которой есть товары");
    categories.delete(category);
}
```

Зачем проверка `existsByCategory_Id`, если в базе есть `ON DELETE RESTRICT`?
Чтобы сообщение пользователю было **понятным**: «Нельзя удалить категорию, в
которой есть товары», а не «нарушение ограничения целостности». База —
последний рубеж, а приложение — первый.

### `toggleVisible()` — «показать/скрыть»

```java
public void toggleVisible(Long id) {
    Product product = findProduct(id);
    product.visible = !product.visible;
    products.save(product);
}
```

Простое переключение. Почему `toggle`, а не два метода `show`/`hide`? В
админке одна кнопка «Скрыть»/«Показать» — она и вызывает toggle. Ставить
галочку «видимость» тоже можно, но кнопка удобнее.

---

## 7.5. `AuthService` — пользователи и роли

### Регистрация

```java
public User register(HttpSession session, String name, String email, String password) {
    check(!blank(name), "Укажите имя");
    check(!blank(email) && email.contains("@"), "Введите корректный e-mail");
    check(password != null && password.length() >= 6, "Пароль — не короче 6 символов");
    if (users.existsByEmailIgnoreCase(email.trim()))
        throw ApiException.conflict("Пользователь с таким e-mail уже есть");

    User user = createUser(name, email, password, Role.USER);
    session.setAttribute(USER_ID, user.id);
    return user;
}
```

* Проверка e-mail — «содержит @». Это примитивная проверка, но для учебного
  проекта — достаточно.
* Проверка уникальности — `existsByEmailIgnoreCase` → 409. Дополнительно в
  базе стоит `UNIQUE` на `email` — «страховка на случай гонки» (два
  одновременных запроса на регистрацию).
* Пароль **не проверяется на сложность** — только длина ≥ 6. Это осознанное
  упрощение.
* После регистрации мы **сразу выполняем вход**: кладём `userId` в сессию.
  Пользователю не надо снова вводить пароль.

### Вход

```java
public User login(HttpSession session, String email, String password) {
    User user = blank(email) ? null : users.findByEmailIgnoreCase(email.trim()).orElse(null);
    if (user == null || password == null || !encoder.matches(password, user.passwordHash))
        throw ApiException.unauthorized("Неверный e-mail или пароль");
    session.setAttribute(USER_ID, user.id);
    return user;
}
```

**Важная деталь безопасности:** сообщение об ошибке **одинаковое** в обоих
случаях — «Неверный e-mail или пароль». Если бы мы написали «пользователь не
найден» в одном случае и «неверный пароль» в другом, злоумышленник мог бы
перебирать e-mail'ы существующих пользователей. Одинаковое сообщение не
раскрывает ничего.

### «Кто я сейчас»

```java
/** Текущий пользователь или null, если это гость. */
public User current(HttpSession session) {
    Object id = session.getAttribute(USER_ID);
    if (id == null) return null;
    return users.findById((Long) id).orElse(null);
}

/** Текущий пользователь; если не вошёл — ошибка 401. */
public User require(HttpSession session) {
    User user = current(session);
    if (user == null) throw ApiException.unauthorized("Требуется авторизация");
    return user;
}

/** Текущий пользователь-администратор; иначе ошибка 401 или 403. */
public User admin(HttpSession session) {
    User user = require(session);
    if (user.role != Role.ADMIN) throw ApiException.forbidden("Доступ запрещён");
    return user;
}
```

Три метода — три уровня требований:

| Метод | Кто подходит | Если не подходит |
|-------|--------------|------------------|
| `current` | кто угодно (включая гостя) | возвращает `null` |
| `require` | только вошедший | 401 «Требуется авторизация» |
| `admin` | только администратор | 401 (не вошёл) или 403 (вошёл, но не админ) |

Разница между 401 и 403 — не формальность: 401 означает «представьтесь», 403 —
«вас узнали, но вам нельзя».

### `ensureAdmin()`

```java
public void ensureAdmin() {
    if (!users.existsByEmailIgnoreCase(adminEmail))
        createUser("Администратор", adminEmail, adminPassword, Role.ADMIN);
}
```

Вызывается из `SeedData` при старте. Если администратор уже есть — ничего не
делаем. Логин и пароль приходят из настроек через `@Value`.

### `updateProfile()`

```java
public User updateProfile(User user, String name, String phone, String address) {
    check(!blank(name), "Укажите имя");
    user.name = name.trim();
    user.phone = phone == null ? "" : phone.trim();
    user.address = address == null ? "" : address.trim();
    return users.save(user);
}
```

Обратите внимание: `user` передаётся **снаружи** (контроллер уже достал его
через `auth.require(session)`). Сервис не работает с сессией в этом методе —
это правильно: метод меняет данные пользователя, а не «сеанс».

---

## 7.6. Общие приёмы, которые вы встретите во всех сервисах

| Приём | Где | Зачем |
|-------|-----|-------|
| `check(условие, "Текст")` | все сервисы | одна строка вместо `if (...) throw ...` |
| `blank(текст)` | `AuthService`, `OrderService`, `CatalogService` | «пусто или из пробелов» |
| `findById(...).filter(...).orElseThrow(...)` | `CartService`, `OrderService`, `CatalogService` | «достань или 404» |
| `private` вспомогательные методы | все сервисы | детали реализации не торчат наружу |
| `private final` поля | все сервисы | зависимость неизменяема |
| `record` для входных данных | `OrderService.OrderData`, `CatalogService.ProductData` | меньше ошибок с параметрами |
| `@Transactional` | `OrderService.create`, `changeStatus` | «всё или ничего» |

---

## 7.7. Какие сценарии покрыты тестами сервисов

### `CartServiceTest` (24 теста)

* **Добавление:** товар попадает в корзину; повторное добавление суммирует;
  больше остатка → 409; превышение с учётом уже добавленного → 409;
  несуществующий товар → 404; скрытый товар → 404; количество ≤ 0 → 400.
* **Количество:** установка нового; установка больше остатка → 409.
* **Удаление:** удаление позиции; полная очистка.
* **Нормализация:** пропавший товар убирается; скрытый убирается; товар без
  остатка убирается; количество урезается до остатка.
* **Суммы:** одна позиция; несколько позиций; пустая корзина → ноль.

### `OrderServiceTest` (34 теста)

* **Создание:** заказ создаётся; статус «новый»; сумма считается; остаток
  списывается; у каждой позиции свой остаток; гость (`user == null`); пустая
  корзина → 400; нет имени → 400; нет e-mail и телефона → 400; нет адреса →
  400; нехватка остатка → 409 и заказ **не создан**; товар исчез из базы → 404.
* **Статусы:** все допустимые переходы; недопустимые («доставлен → новый»,
  «новый → доставлен», «в обработке → новый»); неизвестный статус → 400; пустой
  статус → 400; несуществующий заказ → 404; **возврат товара на склад при
  отмене**.
* **Просмотр:** заказы пользователя; конкретный заказ; чужой заказ → 404.
* **Суммы:** одна позиция; несколько; пустая корзина.

### `CatalogServiceTest` (33 теста)

* **Категории:** список; создание; переименование; пустое название → 400;
  удаление; удаление несуществующей → 404; удаление непустой → 409.
* **Товары:** каталог только видимых; фильтр по категории; сортировки (три
  варианта); карточка товара; скрытый товар → 404; несуществующий → 404.
* **Валидация:** пустое название → 400; отрицательная цена → 400;
  отсутствующая цена → 400; отрицательный остаток → 400; отсутствующий
  остаток → 400; отсутствующая категория → 400; сохранение нормального
  товара; создание нового (`id == null`); редактирование существующего.
* **Видимость:** переключение пока/скрыть.

### `AuthServiceTest` (28 теста)

* **Регистрация:** успех; пустое имя → 400; некорректный e-mail → 400;
  короткий пароль → 400; повторный e-mail → 409; после регистрации
  пользователь вошёл.
* **Вход:** успех; неверный пароль → 401; несуществующий e-mail → 401;
  пустой e-mail → 401.
* **Текущий пользователь:** гость → `null`; вошедший → пользователь;
  `require` без входа → 401; `admin` без входа → 401; `admin` для
  покупателя → 403; `admin` для администратора → ок.
* **Профиль:** сохранение; пустое имя → 400.
* **Администратор:** `ensureAdmin` создаёт; не создаёт повторно.

---

## 7.8. Главное из главы

1. Сервис — это класс с бизнес-логикой. Он не знает про HTTP и не работает с
   базой напрямую.
2. Ошибки — обычные возвращаемые значения «наружу»: сервис бросает
   `ApiException` с кодом, решать «куда показать» — не его дело.
3. Корзина в сессии — это `Map<Long, Integer>`: только id и количество. Цены
   всегда из базы.
4. `viewAndNormalize` не только показывает, но и **чинит** корзину: убирает
   пропавшие и скрытые товары, урезает количество до остатка.
5. `add` суммирует количество и проверяет остаток **после** суммирования.
6. `create` в `OrderService` — пример «всё или ничего»: `@Transactional`,
   повторная проверка остатков, списание, снимки цены и имени, каскадное
   сохранение позиций.
7. При отмене заказа товары возвращаются на склад.
8. Правила переходов статусов живут только в `OrderStatus`.
9. У пользователя один «стек» требований: `current` → `require` → `admin`.

**Вопросы для самопроверки**

1. Почему корзина хранит только id и количество, а не цену?
2. Что произойдёт, если в `CartService.add` проверять остаток **до**
   суммирования? Приведите конкретный сценарий поломки.
3. Почему `OrderService.create` не вызывает `products.save(product)` после
   списания остатка?
4. Что произойдёт, если убрать `@Transactional` с `OrderService.create`?
   Опишите сценарий, где это приведёт к испорченным данным.
5. Зачем в `OrderItem` хранить `productId`, если есть `name` и `price`?
6. Почему `AuthService.login` возвращает одинаковое сообщение для
   «нет пользователя» и «неверный пароль»?
7. Что делает `@Transactional` на методе `changeStatus`, когда заказ
   переводится из «нового» в «в обработке» (товары не возвращаются)?

---

*Дальше: [Глава 8. Веб-слой](08-controllers.md) — как сервисы превращаются
в ответы браузеру.*
