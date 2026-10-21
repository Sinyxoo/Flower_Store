# Глава 8. Веб-слой

> **Что мы уже знаем:** сервисы содержат всю бизнес-логику и умеют сообщать
> об ошибках через `ApiException` (глава 7). Теперь разберём, кто превращает
> эти вызовы в ответы браузеру: контроллеры, общий обработчик ошибок и
> коды HTTP-ответов.

---

## 8.1. Три вида контроллеров

В проекте три вида веб-контроллеров, и различаются они **форматом ответа**:

| Контроллер | Аннотация | Отвечает | Пример адреса |
|-----------|-----------|----------|---------------|
| `ApiController` | `@RestController` | **JSON** — данные для страниц | `/api/products` |
| `ShopController` | `@Controller` | **редиректы** — действия покупателя | `/cart/add` |
| `AdminController` | `@Controller` | **редиректы** — действия администратора | `/admin/products/save` |

Плюс общий базовый класс `FormController` — это не контроллер сам по себе, а
«помощник» для двух последних.

Почему два разных мира?

* **API** отвечает данными. Фронтенд сам решает, как их показать.
* **Формы** отвечают редиректом на страницу. Это нужно потому, что после
  нажатия кнопки браузер всё равно должен получить **страницу** (а не JSON),
  иначе пользователь увидит кусок JSON вместо сайта.

---

## 8.2. `ApiController` — данные для страниц

```java
@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuthService auth;
    private final CatalogService catalog;
    private final CartService cart;
    private final OrderService orders;

    public ApiController(AuthService auth, CatalogService catalog, CartService cart, OrderService orders) {
        this.auth = auth;
        this.catalog = catalog;
        this.cart = cart;
        this.orders = orders;
    }
```

Конструктор с четырьмя сервисами — и снова никаких `new`: Spring всё
подставит.

### Методы

```java
/** Кто сейчас вошёл. Гость — ответ 204 (пустой). */
@GetMapping("/auth/me")
public ResponseEntity<User> me(HttpSession session) {
    User user = auth.current(session);
    if (user == null) return ResponseEntity.noContent().build();
    return ResponseEntity.ok(user);
}

@GetMapping("/categories")
public List<Category> categories() {
    return catalog.allCategories();
}

@GetMapping("/products")
public List<Product> products(@RequestParam(required = false) Long category,
                              @RequestParam(required = false) String sort) {
    return catalog.visibleProducts(category, sort);
}

@GetMapping("/products/{id}")
public Product product(@PathVariable Long id) {
    return catalog.visibleProduct(id);
}

@GetMapping("/cart")
public CartService.View cart(HttpSession session) {
    return cart.viewAndNormalize(session);
}

@GetMapping("/me/orders")
public List<Order> myOrders(HttpSession session) {
    return orders.ordersOf(auth.require(session));
}
```

### Разбор приёмов

**`@PathVariable Long id`** — «возьми часть адреса». В `/api/products/7`
семёрка — это `{id}`. Spring сам приведёт её к числу.

**`ResponseEntity<T>`** — «ответ с явным кодом». Обычно можно просто вернуть
объект, и Spring поставит 200. Но для гостя нам нужен особый код:

```java
if (user == null) return ResponseEntity.noContent().build();
```

`204 No Content` означает: «запрос выполнен успешно, но тела ответа нет».
Это честнее, чем отдавать `null` (который во фронтенде превратился бы в
`null` вместо «объекта пользователя нет»).

Фронтенд с этим знаком:

```javascript
async function get(url) {
  const response = await fetch(url);
  if (response.status === 204) return null;      // ← гость
  if (!response.ok) throw new Error('Ошибка ' + response.status);
  return response.json();
}
```

**`auth.require(session)` прямо в аргументе** — красиво и коротко:

```java
public List<Order> myOrders(HttpSession session) {
    return orders.ordersOf(auth.require(session));
}
```

Если пользователь не вошёл, `require` бросит 401, и метод не выполнится
дальше. `ErrorHandler` превратит это в JSON `{"message": "Требуется авторизация"}`.

### Защищённые методы

```java
// ---------- данные для админки ----------
@GetMapping("/admin/products")
public List<Product> adminProducts(HttpSession session) {
    auth.admin(session);
    return catalog.allProducts();
}

@GetMapping("/admin/orders")
public List<Order> adminOrders(HttpSession session) {
    auth.admin(session);
    return orders.allOrders();
}

@GetMapping("/admin/users")
public List<User> adminUsers(HttpSession session) {
    auth.admin(session);
    return auth.allUsers();
}
```

Обратите внимание: `auth.admin(session)` — **первая строка** метода. Она
бросает 401 или 403, если прав нет, и до `return` дело не дойдёт.

**Это не защита от злоумышленника!** Настоящая защита — на сервере, а не в
JavaScript. Здесь мы просто не отдаём данные тому, кому нельзя.

> **Важное различие, о котором часто спрашивают на защите:** если злоумышленник
> умеет отправлять запросы (например, через `curl`), то подделать «я админ» на
> стороне браузера невозможно — состояние сессии живёт на сервере. Поэтому
> проверка прав **в серверном коде** надёжна. А вот проверка «спрятать кнопку
> админки» — это только удобство для пользователя, а не защита.

---

## 8.3. `ShopController` — действия покупателя

```java
@Controller
public class ShopController extends FormController {
    private static final Logger log = LoggerFactory.getLogger(ShopController.class);
    ...
}
```

`extends FormController` — наследование: контроллер получает готовый
метод `result(...)`, который избавляет от повторяющегося кода.

### Корзина — три метода, одинаковых по форме

```java
@PostMapping("/cart/add")
public String addToCart(@RequestParam Long productId, @RequestParam(defaultValue = "1") int qty,
                        HttpSession session, RedirectAttributes redirect) {
    return result(redirect, "/cart.html", "Товар добавлен в корзину",
            () -> cart.add(session, productId, qty));
}

@PostMapping("/cart/update")
public String updateCart(@RequestParam Long productId, @RequestParam int qty,
                         HttpSession session, RedirectAttributes redirect) {
    return result(redirect, "/cart.html", "Корзина обновлена",
            () -> cart.setQty(session, productId, qty));
}

@PostMapping("/cart/remove")
public String removeFromCart(@RequestParam Long productId, HttpSession session, RedirectAttributes redirect) {
    return result(redirect, "/cart.html", "Товар убран из корзины",
            () -> cart.remove(session, productId));
}
```

Разберём **последний аргумент** — `() -> cart.add(session, productId, qty)`:

* `() -> ...` — **лямбда** (глава 2), анонимная функция без имени.
* Она ничего не принимает и ничего не возвращает.
* Её тип — `Runnable` (в `FormController` объявлено именно так).

Зачем это делать? Чтобы `result(...)` мог **выполнить** действие и **поймать**
ошибку:

```java
protected String result(RedirectAttributes redirect, String okPath, String errPath, String msg, Runnable action) {
    try {
        action.run();
        if (msg != null) redirect.addAttribute("msg", msg);
        return "redirect:" + okPath;
    } catch (ApiException e) {
        redirect.addAttribute("error", e.getMessage());
        return "redirect:" + errPath;
    }
}
```

Без лямбды пришлось бы писать в каждом методе свой try/catch — десяток
одинаковых блоков. С лямбдой — одна строка на действие.

> **Лямбда «откладывает» выполнение.** Строка `() -> cart.add(...)` **не**
> вызывает `add` — она просто готовит вызов. Вызов происходит внутри
> `action.run()` в блоке `try`. Поэтому ошибка из сервиса попадает именно в
> `catch`. Это важно и для новичков: «лямбда выполнится позже, когда её
> вызовут».

### Оформление заказа — исключение из правила

```java
@PostMapping("/checkout")
public String checkout(@RequestParam String customerName,
                       @RequestParam(defaultValue = "") String email,
                       @RequestParam(defaultValue = "") String phone,
                       @RequestParam String address,
                       @RequestParam(defaultValue = "cash") String payment,
                       HttpSession session, RedirectAttributes redirect) {
    try {
        OrderService.OrderData data = new OrderService.OrderData(customerName, email, phone, address, payment);
        Order order = orders.create(auth.current(session), cart.raw(session), data);
        cart.clear(session);
        redirect.addAttribute("id", order.id);
        return "redirect:/order-success.html";
    } catch (ApiException e) {
        redirect.addAttribute("error", e.getMessage());
        return "redirect:/checkout.html";
    }
}
```

Здесь try/catch написан **вручную**, и это не копипаст по недосмотру.
Причина в комментарии:

```java
// Здесь try/catch написан вручную: в ответе нужен ещё и номер созданного заказа.
```

Методу `result` не хватает возможности **получить результат** действия: здесь
нужен `order.id`, чтобы показать покупателю номер заказа. `result` работает с
`Runnable`, а здесь нужен `Supplier` (функция, возвращающая значение).
Результат работы в данном случае — сохранить его в переменную и
использовать после.

Также обратите внимание на порядок:

```java
Order order = orders.create(...);   // 1. создать заказ
cart.clear(session);                // 2. очистить корзину
redirect.addAttribute("id", order.id);  // 3. показать номер
```

Очистить корзину можно **только после** успешного создания заказа. Если
`create` бросит исключение, до `clear` мы не дойдём — и покупатель сможет
попробовать ещё раз, не потеряв корзину.

### Вход, регистрация, выход

```java
@PostMapping("/login")
public String login(@RequestParam String email, @RequestParam String password,
                    HttpSession session, RedirectAttributes redirect) {
    return result(redirect, "/account.html", "/login.html", "Вы вошли",
            () -> auth.login(session, email, password));
}

@PostMapping("/register")
public String register(@RequestParam String name, @RequestParam String email,
                       @RequestParam String password, @RequestParam String password2,
                       HttpSession session, RedirectAttributes redirect) {
    return result(redirect, "/account.html", "/register.html", "Аккаунт создан", () -> {
        if (!password.equals(password2)) throw ApiException.badRequest("Пароли не совпадают");
        auth.register(session, name, email, password);
    });
}
```

У `register` — **четыре** перегрузки `result`:

```java
result(redirect, okPath, errPath, msg, action)   // при ошибке — на errPath
result(redirect, path, msg, action)              // при ошибке — на тот же path
```

Регистрация возвращает на `/register.html` при ошибке (чтобы не потерять
entered данные — хотя в нашем проекте они не сохраняются), а вход — на
`/login.html`.

Проверка «пароли совпадают» — в контроллере, потому что это проверка **ввода
в форме**, а не бизнес-правило магазина (об этом — в главе 5.5).

### Личный кабинет и «повторить заказ»

```java
@PostMapping("/account/profile")
public String saveProfile(@RequestParam String name,
                          @RequestParam(defaultValue = "") String phone,
                          @RequestParam(defaultValue = "") String address,
                          HttpSession session, RedirectAttributes redirect) {
    User user = auth.require(session);   // не вошёл — ErrorHandler отправит на страницу входа
    return result(redirect, "/account.html", "Данные сохранены",
            () -> auth.updateProfile(user, name, phone, address));
}

/** «Повторить заказ»: кладёт все товары старого заказа в корзину. */
@PostMapping("/account/repeat/{orderId}")
public String repeatOrder(@PathVariable Long orderId, HttpSession session, RedirectAttributes redirect) {
    User user = auth.require(session);
    Order order = orders.findOne(orderId, user.id);

    String error = null;
    for (OrderItem item : order.items) {
        try {
            cart.add(session, item.productId, item.qty);
        } catch (ApiException e) {
            error = e.getMessage();   // товара нет или не хватает — добавим остальные
        }
    }
    if (error != null) redirect.addAttribute("error", error);
    return "redirect:/cart.html";
}
```

`repeatOrder` — пример «мягкой» обработки ошибок: если один товар не
добавился (закончился или удалён), мы **не прерываем** цикл, а добавляем
остальные и показываем предупреждение. Пользователю полезнее получить
часть заказа в корзине и увидеть, чего не хватает.

И ещё: `orders.findOne(orderId, user.id)` — заказ ищется **с проверкой
владельца** (глава 7.3). Чужой заказ → 404.

### Обратная связь

```java
@PostMapping("/contacts")
public String sendMessage(@RequestParam(defaultValue = "") String name,
                          @RequestParam(defaultValue = "") String contact,
                          @RequestParam(defaultValue = "") String message,
                          RedirectAttributes redirect) {
    if (message.isBlank()) {
        redirect.addAttribute("error", "Напишите сообщение");
    } else {
        log.info("Сообщение от '{}' ({}): {}", name, contact, message);
        redirect.addAttribute("msg", "Сообщение отправлено");
    }
    return "redirect:/contacts.html";
}
```

Признаем честно: сообщение **никуда не сохраняется** — только пишется в лог
сервера. Это упрощение учебного проекта (для настоящей почты нужна отдельная
таблица «обращения» или отправка письма). Зато видно, как работает логирование:
`log.info(...)` — вывод в консоль и в файл логов.

---

## 8.4. `AdminController` — действия администратора

```java
@Controller
public class AdminController extends FormController {
```

Структура та же, что у `ShopController`, но во **всех** методах первой строкой
идёт `auth.admin(session)`:

```java
@PostMapping("/admin/products/save")
public String saveProduct(@RequestParam(required = false) Long id,
                          @RequestParam(defaultValue = "") String name,
                          @RequestParam(required = false) Long categoryId,
                          @RequestParam(required = false) BigDecimal price,
                          @RequestParam(required = false) Integer stock,
                          @RequestParam(defaultValue = "") String description,
                          @RequestParam(defaultValue = "") String imageUrl,
                          @RequestParam(required = false) String visible,   // галочка: есть — включена
                          HttpSession session, RedirectAttributes redirect) {
    auth.admin(session);
    CatalogService.ProductData data = new CatalogService.ProductData(
            name, categoryId, price, description, imageUrl, stock, visible != null);
    try {
        catalog.saveProduct(id, data);
    } catch (ApiException e) {
        // Возвращаем на форму вместе с тем, что пользователь уже ввёл.
        redirect.addAttribute("error", e.getMessage());
        if (id != null) redirect.addAttribute("id", id);
        redirect.addAttribute("name", name);
        redirect.addAttribute("categoryId", categoryId);
        redirect.addAttribute("price", price);
        redirect.addAttribute("stock", stock);
        redirect.addAttribute("description", description);
        redirect.addAttribute("imageUrl", imageUrl);
        redirect.addAttribute("visible", visible != null);
        return "redirect:/admin/product-form.html";
    }
    redirect.addAttribute("msg", "Товар сохранён");
    return "redirect:/admin/products.html";
}
```

### `@RequestParam(required = false)`

У полей формы в админке почти все параметры необязательные: при создании
товара `id` нет, при редактировании — есть. `required = false` означает «если
параметра нет — передай `null`» (для чисел) или значение по умолчанию.

### Галочка «видим»

```java
@RequestParam(required = false) String visible,   // галочка: есть — включена
...
visible != null
```

HTML-галочка — хитрый элемент: если она **отмечена**, в запросе есть
`visible=on`; если **не отмечена** — параметра нет вообще. Поэтому
`visible != null` — это «галочка стоит» → `true`.

### Возврат введённых данных в форму

Блок `catch` копирует все поля в редирект, чтобы форма открылась
**заполненной**. Это удобно для пользователя (не надо вводить всё заново) и
для фронтенда: `pages.js` читает эти параметры:

```javascript
if (params.has('name')) {
    $('#title').textContent = params.get('id') ? 'Изменить товар' : 'Новый товар';
    $('#pid').value = params.get('id') || '';
    $('#name').value = params.get('name') || '';
    ...
}
```

> **Замечание об утечке данных.** Этот подход удобен, но в реальном проекте
> так делать не стоит: параметры редиректа попадают в **историю браузера** и
> в журналы сервера. Правильнее — отображать страницу формы напрямую (без
> редиректа) с данными в модели. В учебном проекте это допустимое упрощение.

### Остальные методы админки

```java
@PostMapping("/admin/products/{id}/toggle")
public String toggleProduct(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
    auth.admin(session);
    return result(redirect, "/admin/products.html", "Товар обновлён", () -> catalog.toggleVisible(id));
}

@PostMapping("/admin/products/{id}/delete")
public String deleteProduct(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
    auth.admin(session);
    return result(redirect, "/admin/products.html", "Товар удалён", () -> catalog.deleteProduct(id));
}

@PostMapping("/admin/categories")
public String addCategory(@RequestParam(defaultValue = "") String name,
                          HttpSession session, RedirectAttributes redirect) {
    auth.admin(session);
    return result(redirect, "/admin/categories.html", "Категория добавлена",
            () -> catalog.saveCategory(null, name));
}

@PostMapping("/admin/categories/{id}")
public String renameCategory(@PathVariable Long id, @RequestParam(defaultValue = "") String name,
                             HttpSession session, RedirectAttributes redirect) {
    auth.admin(session);
    return result(redirect, "/admin/categories.html", "Категория переименована",
            () -> catalog.saveCategory(id, name));
}

@PostMapping("/admin/categories/{id}/delete")
public String deleteCategory(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
    auth.admin(session);
    return result(redirect, "/admin/categories.html", "Категория удалена",
            () -> catalog.deleteCategory(id));
}

@PostMapping("/admin/orders/{id}/status")
public String changeOrderStatus(@PathVariable Long id, @RequestParam String status,
                                HttpSession session, RedirectAttributes redirect) {
    auth.admin(session);
    return result(redirect, "/admin/orders.html", "Статус обновлён",
            () -> orders.changeStatus(id, status));
}
```

Заметьте: `saveCategory(null, name)` — создание (id = null), а
`saveCategory(id, name)` — переименование. Один сервисный метод, два сценария
(глава 7.4).

---

## 8.5. `FormController` — общий помощник

```java
/**
 * Общее для контроллеров форм: выполнить действие и показать результат.
 * Успех — ?msg=..., ошибка — ?error=... (эти параметры читает layout.js).
 */
abstract class FormController {

    /** Действие; и при успехе, и при ошибке возвращаемся на одну и ту же страницу. */
    protected String result(RedirectAttributes redirect, String path, String msg, Runnable action) {
        return result(redirect, path, path, msg, action);
    }

    /** Действие; при успехе возвращаемся на okPath, при ошибке — на errPath. */
    protected String result(RedirectAttributes redirect, String okPath, String errPath, String msg, Runnable action) {
        try {
            action.run();
            if (msg != null) redirect.addAttribute("msg", msg);
            return "redirect:" + okPath;
        } catch (ApiException e) {
            redirect.addAttribute("error", e.getMessage());
            return "redirect:" + errPath;
        }
    }
}
```

* `abstract class` — «это нельзя создать, но можно наследовать». Класс
  существует только как основа.
* `package-private` (без `public`) — класс виден только внутри пакета
  `shop.controller`. Это правильно: наружу его выставлять незачем.
* `protected` — метод доступен наследникам.
* Первая перегрузка просто вызывает вторую с одинаковыми `okPath` и `errPath`.

### `RedirectAttributes` — «сумка для параметров редиректа»

```java
redirect.addAttribute("msg", msg);
return "redirect:/cart.html";
```

Spring соберёт адрес: `/cart.html?msg=Товар+добавлен+в+корзину`. Значение
будет URL-экранировано автоматически.

---

## 8.6. `ErrorHandler` — последний рубеж

```java
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handle(ApiException e, HttpServletRequest request) {
        // Запрос данных (/api/...) — отвечаем JSON
        if (request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity.status(e.status).body(Map.of("message", message(e)));
        }
        // Запрос из формы — отправляем на нужную страницу, текст ошибки — в адресе
        String page = "/404.html";
        if (e.status == 401) page = "/login.html";
        if (e.status == 403) page = "/403.html";
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", page + "?error=" + encode(message(e)))
                .build();
    }
```

### `@RestControllerAdvice` и `@ExceptionHandler`

* `@RestControllerAdvice` — «я глобальный обработчик ошибок для всех
  контроллеров». Он применяется ко всем контроллерам сразу.
* `@ExceptionHandler(ApiException.class)` — «если где-то вылетела именно такая
  ошибка — обработай её здесь».

Схема обработки ошибок в проекте:

```
                    ошибка в контроллере
                            │
                            ▼
              try/catch в методе контроллера?  ──да──► ?error=... (форма)
                            │ нет
                            ▼
                 ErrorHandler (глобальный)
                            │
        ┌───────────────────┴────────────────────┐
        ▼                                        ▼
   адрес /api/...                            форма
   → JSON + код                             → 303 + Location: /login.html
                                            /403.html или /404.html
```

### Почему `SEE_OTHER` (303), а не `302 Found`

* `302` — «временно перенаправь», браузер может повторить запрос методом
  POST.
* `303` — «смотри на другой адрес, и делай это запросом GET».

Для наших форм важно именно 303: после `POST /cart/add` браузер должен
получить страницу **GET**-ом. Если бы был 302, некоторые браузеры повторили бы
POST — и товар добавился бы дважды.

### Кодирование текста ошибки

```java
/** Текст ошибки не должен быть пустым — иначе Map.of бросит исключение. */
private static String message(ApiException e) {
    return e.getMessage() == null ? "Ошибка" : e.getMessage();
}

/** Текст ошибки попадает в адрес страницы — экранируем, чтобы не сломать URL. */
private static String encode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
}
```

* `message(...)` — страховка: `Map.of("message", null)` бросил бы
  `NullPointerException`. Теперь ошибка в обработчике ошибок не приведёт к
  новой ошибке.
* `encode(...)` — текст в адресе может содержать пробелы, `&`, кириллицу.
  Без кодирования адрес развалился бы. `URLEncoder.encode` превращает пробел в
  `+`, а кириллицу — в `%D0%A2...`.

---

## 8.7. Полная таблица маршрутов проекта

### API (JSON)

| Метод | Адрес | Кто может | Ответ |
|-------|-------|-----------|-------|
| GET | `/api/auth/me` | все | 200 + объект пользователя / 204 (гость) |
| GET | `/api/categories` | все | 200 + список |
| GET | `/api/products` | все | 200 + список видимых товаров |
| GET | `/api/products/{id}` | все | 200 + товар / 404 |
| GET | `/api/cart` | все | 200 + состав корзины |
| GET | `/api/me/orders` | вошедший | 200 + список / 401 |
| GET | `/api/admin/products` | админ | 200 / 401 / 403 |
| GET | `/api/admin/orders` | админ | 200 / 401 / 403 |
| GET | `/api/admin/users` | админ | 200 / 401 / 403 |

### Формы (редиректы)

| Метод | Адрес | Что делает | При ошибке |
|-------|-------|-----------|-----------|
| POST | `/cart/add` | добавить товар | `/cart.html?error=...` |
| POST | `/cart/update` | изменить количество | `/cart.html?error=...` |
| POST | `/cart/remove` | убрать товар | — |
| POST | `/checkout` | оформить заказ | `/checkout.html?error=...` |
| POST | `/login` | войти | `/login.html?error=...` |
| POST | `/register` | зарегистрироваться | `/register.html?error=...` |
| POST | `/logout` | выйти | — |
| POST | `/account/profile` | сохранить профиль | `/account.html?error=...` |
| POST | `/account/repeat/{id}` | повторить заказ | 404, если заказ не мой |
| POST | `/contacts` | написать в поддержку | `/contacts.html?error=...` |
| POST | `/admin/products/save` | сохранить товар | `/admin/product-form.html?error=...` |
| POST | `/admin/products/{id}/toggle` | скрыть/показать | `/admin/products.html?error=...` |
| POST | `/admin/products/{id}/delete` | удалить товар | `/admin/products.html?error=...` |
| POST | `/admin/categories` | добавить категорию | `/admin/categories.html?error=...` |
| POST | `/admin/categories/{id}` | переименовать | `/admin/categories.html?error=...` |
| POST | `/admin/categories/{id}/delete` | удалить категорию | `/admin/categories.html?error=...` |
| POST | `/admin/orders/{id}/status` | сменить статус | `/admin/orders.html?error=...` |

---

## 8.8. Как это проверяется тестами

`@WebMvcTest` поднимает **только веб-слой**: контроллеры и обработчик ошибок.
Сервисы заменяются заглушками (`@MockBean`), база не нужна вовсе.

```java
@WebMvcTest(ShopController.class)
@DisplayName("ShopController — корзина, заказ, вход, профиль")
class ShopControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService auth;
    @MockBean
    private CartService cart;
    @MockBean
    private OrderService orders;
```

* `MockMvc` — «поддельный браузер»: позволяет выполнить запрос к контроллеру
  и посмотреть ответ (код, тело, заголовки) **без** запуска сервера.
* `@MockBean` — «подставь заглушку вместо настоящего сервиса».

Типичный тест:

```java
@Test
@DisplayName("товар добавляется в корзину")
void товарДобавляетсяВКорзину() throws Exception {
    MvcResult result = mvc.perform(post("/cart/add").param("productId", "1").param("qty", "2"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    assertThat(redirectTo(result)).isEqualTo("/cart.html?msg=Товар добавлен в корзину");
    verify(cart).add(any(), eq(1L), eq(2));
}
```

Что здесь проверяется:

1. `mvc.perform(post(...))` — «отправь POST на `/cart/add` с такими полями».
2. `status().is3xxRedirection()` — «ответ должен быть редиректом».
3. `redirectTo(result)` — «адрес редиректа должен быть ровно таким» (с
   декодированием `%`-последовательностей, поэтому текст читается).
4. `verify(cart).add(any(), eq(1L), eq(2))` — «сервис должен был получить
   productId=1 и qty=2». Это проверка «сервис вызван с правильными
   аргументами» — типичный мок-тест.

Всего в контроллерных тестах 43 проверки: 14 в `ApiControllerTest`, 18 в
`ShopControllerTest`, 11 в `AdminControllerTest`.

---

## 8.9. Главное из главы

1. Контроллер **разбирает запрос и формирует ответ**, но **не решает**.
   Все решения — в сервисах.
2. `@RestController` → JSON, `@Controller` → редирект или страница.
3. `@RequestParam`, `@PathVariable`, `HttpSession`, `RedirectAttributes` —
   четыре способа получить данные запроса.
4. `ResponseEntity` — когда нужно указать код ответа (204 для гостя, 400/401
   для ошибок).
5. `FormController.result(...)` + лямбда `() -> ...` — убирает
   повторяющиеся try/catch.
6. `auth.admin(session)` — первая строка каждого метода админки.
7. `ErrorHandler` — глобальный обработчик: JSON для `/api/...`, редирект с
   `?error=` для форм; 303 вместо 302, чтобы браузер не повторял POST.
8. Текст ошибки кодируется (`URLEncoder`) — иначе адрес развалится.

**Вопросы для самопроверки**

1. Почему в `ApiController` возвращается `204`, а не `200` с `null`?
2. Что произойдёт, если в `ShopController.addToCart` убрать проверку
   `auth.require(...)`? (Подсказка: там её и нет — а вот в `saveProfile` есть.)
3. Почему в `checkout` try/catch написан вручную, а в `addToCart` — нет?
4. Почему `cart.clear(session)` стоит **после** `orders.create(...)`?
5. Что произойдёт, если `ErrorHandler` вернёт 302 вместо 303?
6. Почему `visible` приходит как `String`, а не как `boolean`?
7. Зачем `URLEncoder` в `ErrorHandler`?

---

*Дальше: [Глава 9. Фронтенд](09-frontend.md) — как страницы получают данные
и почему здесь нет фреймворков.*
