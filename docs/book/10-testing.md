# Глава 10. Тестирование

> **Что мы уже знаем:** мы разобрали архитектуру (глава 5) и увидели, что
> бизнес-правила живут в сервисах. Эта глава — про то, **как доказать**, что
> эти правила работают, и как не сломать их при следующем изменении.
>
> В проекте 211 автоматических тестов и отчёт о покрытии кода. Это не
> «для галочки» — это главный инструмент, который отличает учебную работу от
> настоящей.

---

## 10.1. Зачем нужны тесты

### Проблема, которую они решают

Представьте: вы сдали курсовую. Преподаватель нашёл ошибку в расчёте суммы
заказа. Вы исправили. Но где ещё эта ошибка могла повлиять на результат?
Проверить вручную нужно **каждый** сценарий: 24 теста корзины, 34 теста
заказов, 33 теста каталога, 28 тестов авторизации… У вас уйдёт неделя
ручной проверки — и вы всё равно что-нибудь забудете.

**Автоматический тест** — это программа, которая делает ту же проверку за вас,
за полсекунды, и делает это **каждый раз**, когда вы нажимаете «запустить».

### Три выгоды

1. **Уверенность.** Изменил код — запустил тесты — всё зелёное. Не зелёное?
   Значит, ты что-то сломал. Именно ты, а не «кто-то другой».
2. **Документация.** Тест показывает, как должен работать код, точнее любого
   комментария. Имя теста `добавлениеСПревышениемУжеДобавленногоОтклоняется`
   читается как описание сценария.
3. **Скорость разработки.** Чем раньше вы пишете тест, тем дешевле ловить
   ошибки. Ошибку в 1000-й строке найти за минуту; ошибку в логике создания
   заказа — за часы.

### Тест — это «исполняемое требование»

Сравните два варианта описания поведения:

> **Вариант А (документация).** Корзина не должна позволять добавить больше
> товара, чем есть на складе.

> **Вариант Б (тест).**
> ```java
> @Test
> @DisplayName("товар, которого больше нет на складе, добавить нельзя")
> void добавлениеБольшеОстаткаОтклоняется() {
>     when(products.findById(1L)).thenReturn(Optional.of(roses(3)));
>
>     ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 1L, 4));
>
>     assertThat(error).isNotNull();
>     assertThat(error.status).isEqualTo(409);
>     assertThat(error).hasMessageContaining("Недостаточно на складе");
>     assertThat(cart.raw(session)).isEmpty();
> }
> ```

Второй вариант **не может устареть**: если правило перестанет работать — тест
упадёт. Документация устаревает молча.

---

## 10.2. Какие тесты есть в проекте

| Вид | Аннотация | Что поднимает | Сколько | Время |
|-----|-----------|---------------|---------|-------|
| **Модульные** (сервисы) | ничего, обычный класс | ничего | 119 | ~0,5 с |
| **Модульные** (модель, исключения) | ничего, обычный класс | ничего | 35 | ~0,1 с |
| **Веб-тесты** | `@WebMvcTest` | только контроллеры + `ErrorHandler` | 43 | ~3 с |
| **Тесты данных** | `@DataJpaTest` | Hibernate + H2 | 11 | ~2 с |
| **Интеграционный** | `@SpringBootTest` | всё приложение целиком (на H2) | 3 | ~5 с |
| **Итого** | | | **211** | **~10 с** |

Обратите внимание на пропорцию: **154 теста из 211 (73 %)** работают вообще
без Spring, без базы и без HTTP. Это и есть главный результат правильной
архитектуры: правила магазина можно проверить обычными Java-объектами.

---

## 10.3. JUnit 5 — каркас тестов

JUnit — самая популярная библиотека тестов для Java. Версия 5 (в проекте она
приходит вместе с `spring-boot-starter-test`) называется **JUnit Jupiter** и
использует аннотации.

### Минимальный тест

```java
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void изНовогоМожноПерейтиВОбработку() {
        assertThat(OrderStatus.NEW.canGo(OrderStatus.PROCESSING)).isTrue();
    }
}
```

* `@Test` — «этот метод — тест». Метод не принимает аргументов и ничего не
  возвращает.
* Имя класса заканчивается на `Test` — так Maven Surefire (плагин запуска
  тестов) находит их автоматически.
* Имя метода — **на русском языке, целыми словами**. Это не экзотика: в
  проекте так сделано намеренно, чтобы отчёт о прогонах читался как
  список требований.

### `@BeforeEach` — подготовка перед каждым тестом

```java
private ProductRepo products;
private CartService cart;
private HttpSession session;

@BeforeEach
void setUp() {
    products = mock(ProductRepo.class);
    cart = new CartService(products);
    session = TestFixtures.fakeSession();
    flowers = TestFixtures.category(1L, "Цветы");
}
```

`@BeforeEach` выполняется **перед каждым** тестом в этом классе. Это важно:
тесты не должны зависеть друг от друга. Если бы общий мок создавался один раз
на весь класс, состояние от первого теста «протекло» бы во второй.

> **Правило независимости:** тесты могут выполняться в любом порядке, по
> одному или все вместе. Если пришлось «отключать» какой-то тест, чтобы
> остальные прошли — значит, тесты связаны, и это дефект.

### `@DisplayName` — человеческое имя теста

```java
@Nested
@DisplayName("Добавление товара в корзину")
class Add {

    @Test
    @DisplayName("товар попадает в корзину с нужным количеством")
    void добавлениеТовараКладётЕгоВКорзину() {
```

Без `@DisplayName` в отчёте будет `CartServiceTest$Add.добавлениеТовараКладётЕгоВКорзину`.
С ней — «Добавление товара в корзину → товар попадает в корзину с нужным
количеством». Разница огромна, когда вы читаете отчёт после неудачного
прогона.

### `@Nested` — группировка внутри класса

`@Nested`-классы позволяют разбить большой тест-класс на смысловые группы.
В `CartServiceTest` их четыре: `Add` (добавление), `SetQty` (изменение
количества), `Remove` (удаление) и `Normalize` (приведение корзины в
порядок).

```java
@Nested
@DisplayName("Расчёт суммы заказа")
class Sums {
    @Test @DisplayName("сумма одной позиции = цена × количество")
    void суммаПоОднойПозиции() { ... }
}
```

В отчёте это выглядит как дерево, а не как простыня из 34 одинаковых строк.

> **Важно:** `@Nested`-классы — это **внутренние** классы. Их конструктор по
> умолчанию «видит» поля внешнего класса, и `@BeforeEach` внешнего класса
> выполняется **перед** каждым вложенным тестом. Поэтому `@BeforeEach` в
> `setUp()` — общий для всех групп.

---

## 10.4. AssertJ — как читать проверки

AssertJ — библиотека «умных утверждений». В проекте используется
`assertThat(...)` из неё.

### Базовые проверки

```java
assertThat(error).isNotNull();
assertThat(error.status).isEqualTo(409);
assertThat(cart.raw(session)).isEmpty();
assertThat(cart.raw(session)).containsEntry(1L, 2);
assertThat(order.items).hasSize(1);
assertThat(products.count()).isPositive();
```

Сравните с «классическим» JUnit:

```java
assertNotNull(error);
assertEquals(409, error.status);
assertTrue(cart.raw(session).isEmpty());
```

Формулировки AssertJ читаются как **предложения на русском**: «ошибка не
null», «статус равен 409», «корзина пуста». Кроме того, при падении AssertJ
показывает **оба** значения (что получилось и что ожидалось) — очень
экономит время.

### Проверки для денег — `isEqualByComparingTo`

```java
assertThat(order.total).isEqualByComparingTo("30.00");
```

Обычный `isEqualTo` для `BigDecimal` сравнивает **не только число, но и
масштаб**: `30.0` и `30.00` — разные объекты, и `isEqualTo` скажет «нет».
`isEqualByComparingTo` сравнивает **по значению**, как математик: 30.0 = 30.00.

Это очень частая ловушка в тестах с деньгами. В проекте используется
`isEqualByComparingTo` везде, где сравниваются суммы.

### Проверки для исключений — `catchThrowable`

```java
ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 1L, 4));

assertThat(error).isNotNull();
assertThat(error.status).isEqualTo(409);
assertThat(error).hasMessageContaining("Недостаточно на складе");
```

`catchThrowable(() -> ...)` — «выполни лямбду, но вместо падения верни
исключение». Это **безопаснее**, чем `assertThrows`:

```java
assertThrows(ApiException.class, () -> cart.add(session, 1L, 4));
```

`assertThrows` скажет только «было ApiException» — но не проверит **код** (409)
и **текст** сообщения. `catchThrowable` позволяет проверить всё. В проекте
используется именно он, потому что HTTP-код — часть контракта.

> **Смысл `catchThrowable`:** если исключения **не** было, `error` будет
> `null`, и первая же проверка `assertThat(error).isNotNull()` упадёт с
> понятным сообщением. Без этой проверки следующая строка упала бы с
> `NullPointerException` — и вы бы не поняли, что произошло.

---

## 10.5. Mockito — «подделки» вместо настоящих зависимостей

### Зачем

`CartService` при создании требует `ProductRepo`. Настоящий репозиторий
требует подключения к PostgreSQL. Для модульного теста это не нужно и даже
вредно (медленно, нужен сервер БД).

**Мок** (mock) — объект-заглушка: он запоминает, какие методы у него
вызвали, и возвращает то, что вы скажете.

```java
products = mock(ProductRepo.class);
cart = new CartService(products);
```

Обратите внимание: `new CartService(products)` — **единственное** место, где
в тестах встречается `new`. В основном коде объекты создаёт Spring, а в
тестах — мы. Это прямое следствие внедрения зависимостей: раз сервис
получает репозиторий снаружи, его можно подменить.

### `when(...).thenReturn(...)` — «если спросят X, верни Y»

```java
when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
```

Читается: «когда у репозитория спросят товар с id 1 — верни вот такой
объект, у которого остаток 5».

`Optional.of(...)` — товар существует. `Optional.empty()` — товара нет:

```java
when(products.findById(99L)).thenReturn(Optional.empty());
```

### Проверка «что именно было вызвано» — `verify`

```java
verify(cart).add(any(), eq(1L), eq(2));
```

«Проверь, что у сервиса вызвали метод `add` с id=1 и количеством=2».

* `any()` — «любое значение» (для первого аргумента — сессии).
* `eq(1L)` — «ровно 1L».

Другие полезные проверки:

```java
verify(orders, never()).save(any());     // «сохранение НЕ вызывалось»
verify(catalog).visibleProducts(eq(2L), eq("price_desc"));
```

### «А что если метод должен бросить ошибку?» — `doThrow`

```java
doThrow(ApiException.badRequest("Цена не может быть отрицательной"))
        .when(catalog).saveProduct(any(), any());
```

Это позволяет сымитировать **любую** ошибку сервиса и проверить, как
контроллер на неё отреагирует. В `AdminControllerTest` так проверяется
возврат на форму с введёнными данными.

### «А что если метод должен изменить состояние?» — `doAnswer`

```java
when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
```

Настоящий JPA-репозиторий `save()` возвращает **сохранённый** объект (у
которого проставлен id). Мок по умолчанию вернёт `null`. Поэтому мы
учим его вести себя как настоящий: «верни то, что тебе передали».

Этот приём называется «заглушка с поведением» (stub with behavior) и очень
часто встречается в тестах.

### `any()`, `eq()`, `anyString()` — «шаблоны» вместо точных значений

| Что | Значение |
|-----|----------|
| `any()` | любое значение |
| `eq(x)` | ровно `x` |
| `anyString()` | любая строка |
| `anyInt()` | любое целое |
| `never()` | ни разу |
| `times(2)` | ровно два раза |

### `fakeSession()` — самая хитрая подделка в проекте

Сессия — это интерфейс `HttpSession` с кучей методов. Создавать настоящую
сессию в модульном тесте не нужно, но нам нужно, чтобы `getAttribute` и
`setAttribute` **работали по-настоящему**: положили корзину — потом её же
прочитали.

```java
public static HttpSession fakeSession() {
    Map<String, Object> attributes = new HashMap<>();
    HttpSession session = mock(HttpSession.class);
    when(session.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0, String.class)));
    doAnswer(call -> {
        attributes.put(call.getArgument(0, String.class), call.getArgument(1));
        return null;
    }).when(session).setAttribute(anyString(), any());
    doAnswer(call -> {
        attributes.remove(call.getArgument(0, String.class));
        return null;
    }).when(session).removeAttribute(anyString());
    return session;
}
```

Разбор:

1. Создаём обычную `HashMap` — «настоящее хранилище» атрибутов.
2. Создаём мок `HttpSession`.
3. `getAttribute` → достаём из Map.
4. `setAttribute` → кладём в Map.
5. `removeAttribute` → убираем из Map.

Обратите внимание на `call.getArgument(0, String.class)` — Mockito даёт
«объект вызова», из которого можно достать аргументы. `getArgument(0,
String.class)` — «первый аргумент, приведённый к `String`».

> **Почему так сложно, а не `MockHttpSession`?** Spring Boot действительно
> умеет «настоящую сессию в памяти» — `MockHttpSession`. Но в модульных
> тестах сервисов мы сознательно **не поднимаем Spring**, поэтому
> `MockHttpSession` недоступен. Своя реализация из трёх строк решает задачу
> без единой зависимости от фреймворка.

### `TestFixtures` — фабрики готовых объектов

```java
public static Product product(long id, String name, String price, int stock, boolean visible, Category category) {
    Product product = new Product();
    product.id = id;
    product.name = name;
    product.price = new BigDecimal(price);
    product.stock = stock;
    product.visible = visible;
    product.category = category;
    return product;
}
```

Зачем? Чтобы не повторять десять строк создания товара в каждом тесте. Одна
фабрика — и тесты читаются чисто:

```java
private Product roses(int stock) {
    return TestFixtures.product(1L, "Розы", "10.00", stock, true, flowers);
}
```

Этот маленький «свой» класс экономит больше всего кода во всём тестовом
наборе.

---

## 10.6. Тесты сервисного слоя — «золотой стандарт» этого проекта

Рассмотрим тест целиком — от объявления до последней проверки:

```java
@DisplayName("CartService — корзина покупателя")
class CartServiceTest {

    private ProductRepo products;
    private CartService cart;
    private HttpSession session;
    private Category flowers;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepo.class);
        cart = new CartService(products);
        session = TestFixtures.fakeSession();
        flowers = TestFixtures.category(1L, "Цветы");
    }

    private Product roses(int stock) {
        return TestFixtures.product(1L, "Розы", "10.00", stock, true, flowers);
    }

    @Nested
    @DisplayName("Добавление товара в корзину")
    class Add {

        @Test
        @DisplayName("повторное добавление суммирует количество")
        void повторноеДобавлениеСуммируетКоличество() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));

            cart.add(session, 1L, 2);
            cart.add(session, 1L, 3);

            assertThat(cart.raw(session)).containsEntry(1L, 5);
        }

        @Test
        @DisplayName("превышение остатка с учётом уже лежащего в корзине отклоняется")
        void добавлениеСПревышениемУжеДобавленногоОтклоняется() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            cart.add(session, 1L, 4);

            ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 1L, 2));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
            assertThat(cart.raw(session)).containsEntry(1L, 4);
        }
    }
}
```

### Структура теста «ARRANGE → ACT → ASSERT»

| Часть | Что делаем | В примере |
|-------|------------|-----------|
| **Arrange** | готовим данные | `when(products.findById(1L)).thenReturn(Optional.of(roses(5)))` |
| **Act** | выполняем действие | `cart.add(session, 1L, 4);` |
| **Assert** | проверяем результат | `assertThat(...).containsEntry(1L, 4)` |

Между частями — пустая строка. Этот порядок — стандарт де-факто в Java-тестах
и делает код легко читаемым.

### Что здесь проверяется и почему это ценно

Второй тест — пример теста, который **ловит реальный баг**:

```java
cart.add(session, 1L, 4);                              // положили 4 при остатке 5
ApiException error = ... cart.add(session, 1L, 2);     // хотим ещё 2 — всего 6 > 5
assertThat(error.status).isEqualTo(409);
assertThat(cart.raw(session)).containsEntry(1L, 4);     // корзина НЕ изменилась
```

Проверяются **три** вещи:

1. Бросается именно 409 (конфликт), а не 400.
2. Сообщение правильное (в другом тесте).
3. **Корзина не изменилась** — важнейшая проверка! Частая ошибка: код
   меняет корзину до проверки остатка, и покупатель видит «6 роз», хотя
   купить нельзя.

### Покрытие сценариев

Правило: для каждого публичного метода сервиса проверяются

* **успешный путь** — «как должно работать»;
* **границы** — ровно столько, сколько есть; на одну единицу больше;
* **ошибочные пути** — каждая ветка `check(...)` и каждый `orElseThrow`.

Полный список по сервисам приведён в главе 7.7.

---

## 10.7. `@WebMvcTest` — тесты контроллеров

### Идея

Контроллер — это «переводчик» между HTTP и сервисом. Его работа:

1. Правильно **найти** метод по адресу и методу HTTP.
2. Правильно **разобрать** параметры запроса.
3. Правильно **передать** их сервису.
4. Правильно **сформировать** ответ (код, тело, редирект).

Всё это можно проверить **без настоящего сервера** — с помощью `MockMvc`.
Реальные сервисы при этом заменяются заглушками.

### Что поднимается

```java
@WebMvcTest(ApiController.class)
@DisplayName("ApiController — данные для страниц")
class ApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService auth;
    @MockBean
    private CatalogService catalog;
    @MockBean
    private CartService cart;
    @MockBean
    private OrderService orders;
```

* `@WebMvcTest(ApiController.class)` — поднять **только веб-слой**, и только
  указанный контроллер (+ `ErrorHandler`, потому что он глобальный).
  Репозитории и база не поднимаются вообще.
* `@MockBean` — «подставь заглушку вместо настоящего бина». Spring заменит
  настоящий `AuthService` моком в контейнере.

> **Устаревшее, но рабочее:** в Spring Boot 3.4+ рекомендуют новый
> `@MockitoBean`. В версии 3.3.5 (наша) `@MockBean` — правильный выбор.

### Примеры проверок

**Код ответа:**

```java
@Test
@DisplayName("гостю отвечают пустым кодом 204")
void гостюОтвечаютПустымКодом204() throws Exception {
    when(auth.current(any())).thenReturn(null);

    mvc.perform(get("/api/auth/me")).andExpect(status().isNoContent());
}
```

**Тело ответа (JSON):**

```java
mvc.perform(get("/api/products/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Розы"))
        .andExpect(jsonPath("$.categoryName").value("Цветы"));
```

`jsonPath("$.name")` — «поле `name` в корне JSON». Путь `$.items[0].qty`
дойдёт до элемента массива.

**Отсутствие лишних полей — важная проверка безопасности:**

```java
@Test
@DisplayName("пароль в ответе не показывается")
void парольВОтветеНеПоказывается() throws Exception {
    when(auth.current(any())).thenReturn(user());

    mvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
}
```

Этот тест «защищает» аннотацию `@JsonIgnore` от случайного удаления.

**Проверка передачи параметров в сервис:**

```java
@Test
@DisplayName("фильтр и сортировка каталога передаются в сервис")
void фильтрИСортировкаПередаютсяВСервис() throws Exception {
    when(catalog.visibleProducts(any(), any())).thenReturn(List.of());

    mvc.perform(get("/api/products").param("category", "2").param("sort", "price_desc"))
            .andExpect(status().isOk());

    verify(catalog).visibleProducts(eq(2L), eq("price_desc"));
}
```

Смысл: контроллер — «тупой переводчик». Он не должен фильтровать сам. Если
кто-то добавит в контроллер свою логику фильтрации, тест упадёт.

### Тесты форм: редиректы

```java
@Test
@DisplayName("сохранённый товар возвращает на страницу списка")
void сохранённыйТоварВозвращаетНаСтраницуСписка() throws Exception {
    MvcResult result = mvc.perform(post("/admin/products/save")
                    .param("name", "Розы")
                    .param("categoryId", "1")
                    .param("price", "10.00")
                    .param("stock", "5")
                    .param("visible", "on"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    assertThat(redirectTo(result)).isEqualTo("/admin/products.html?msg=Товар сохранён");
}
```

Помощник, который используется во всех трёх контроллерных тестах:

```java
private static String redirectTo(MvcResult result) {
    return URLDecoder.decode(result.getResponse().getRedirectedUrl(), StandardCharsets.UTF_8);
}
```

Зачем `URLDecoder.decode`? Потому что адрес с русским текстом в HTTP передаётся
в виде `%D0%A2%D0%BE%D0%B2%D0%B0%D1%80...`. Декодирование превращает его обратно
в читаемый текст, и сравнение в тесте становится возможным.

### Тесты форм: ошибка возвращает на форму

```java
@Test
@DisplayName("при ошибке товар возвращается на форму вместе с введёнными данными")
void приОшибкеТоварВозвращаетсяНаФорму() throws Exception {
    doThrow(ApiException.badRequest("Цена не может быть отрицательной"))
            .when(catalog).saveProduct(any(), any());

    MvcResult result = mvc.perform(post("/admin/products/save")
                    .param("id", "3").param("name", "Розы")
                    .param("price", "-5").param("stock", "5"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    String url = redirectTo(result);
    assertThat(url).startsWith("/admin/product-form.html?error=");
    assertThat(url).contains("name=Розы");
    assertThat(url).contains("price=-5");
    assertThat(url).contains("stock=5");
    assertThat(url).contains("id=3");
}
```

Этот тест проверяет ** UX-контракт**: при ошибке пользователь не теряет
введённые данные. Такой тест — отличный пример «теста на поведение
пользователя», а не на внутренности.

### Тесты прав доступа

```java
@Test
@DisplayName("гость не попадает в админку")
void гостьНеПопадаетВАдминку() throws Exception {
    when(auth.admin(any())).thenThrow(ApiException.unauthorized("Требуется авторизация"));

    MvcResult result = mvc.perform(get("/api/admin/orders"))
            .andExpect(status().isUnauthorized())
            .andReturn();
}
```

Проверяется и код (401), и заголовок `Location` (редирект на `/login.html`).

---

## 10.8. `@DataJpaTest` — тесты доступа к данным

### Идея

Проверить, что репозитории действительно умеют то, что заявлено: что
`findByVisibleTrue()` возвращает только видимые, что `@EntityGraph` тянет
позиции заказа, что `findByIdAndUserId` не отдаёт чужой заказ.

### Что поднимается

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Репозитории — работа с базой данных")
class RepositoryDataTest {
```

* `@DataJpaTest` — поднять **только** слой JPA: репозитории, Hibernate и
  встроенную базу. Веб-слой и сервисы **не** поднимаются.
* `@AutoConfigureTestDatabase(replace = Replace.NONE)` — «**не** заменяй
  источник данных на встроенный». По умолчанию Spring Boot подставляет H2
  автоматически, но `NONE` означает «используй тот, что настроен в
  `application.properties`» — то есть наш H2 с `MODE=PostgreSQL`.

Почему `NONE`, если мы всё равно хотим H2? Потому что `NONE` делает
настройку **явной**: видно, что тесты работают на базе из
`src/test/resources/application.properties`, а не «на чём-то, что Spring
придумал».

### Пример теста

```java
@Test
@DisplayName("EntityGraph подтягивает позиции заказа")
void entityGraphПодтягиваетПозицииЗаказа() {
    Order order = TestFixtures.order(1L, OrderStatus.NEW);
    TestFixtures.item(order, 1L, "Розы", 2, "10.00");
    orders.save(order);

    List<Order> found = orders.findAllByOrderByIdDesc();

    assertThat(found).hasSize(1);
    assertThat(found.get(0).items).hasSize(1);
}
```

Без `@EntityGraph` этот тест **упал бы** с ошибкой ленивой загрузки
(`LazyInitializationException`), потому что `open-in-view=false`. То есть
тест защищает не только от «забыли аннотацию», но и от «перенесли запрос в
контроллер».

### Почему база в тестах — H2, а не PostgreSQL

Три причины:

1. **Скорость.** H2 в памяти работает за миллисекунды; PostgreSQL — за
   сотни миллисекунд на каждый запрос (диск, сеть).
2. **Независимость.** Тесты должны запускаться на любом компьютере, где
   есть Java и Maven. Не у каждого первокурсника стоит PostgreSQL.
3. **Безопасность.** Тесты не могут случайно испортить рабочие данные.

Но есть риск: H2 — это **не** PostgreSQL. Код, который работает на H2,
теоретически может не работать на PostgreSQL (разные диалекты SQL,
разные типы, разная строгость). Поэтому режим `MODE=PostgreSQL` — важная
страховка: H2 старается вести себя как PostgreSQL.

> **Правило:** в тестах не проверяют «специфику PostgreSQL» (например,
> что `ILIKE` работает определённым образом). Такие вещи проверяются
> вручную или на отдельном интеграционном окружении с настоящим PostgreSQL
> (Testcontainers — популярный инструмент для этого).

---

## 10.9. `@SpringBootTest` — «а оно вообще запускается?»

```java
@SpringBootTest
@DisplayName("Приложение — контекст и демо-данные")
class FlowerStoreApplicationTests {

    @Autowired
    private CategoryRepo categories;
    @Autowired
    private ProductRepo products;
    @Autowired
    private UserRepo users;

    @Test
    @DisplayName("контекст Spring собирается без ошибок")
    void контекстСобираетсяБезОшибок() {
        assertThat(categories).isNotNull();
        assertThat(products).isNotNull();
        assertThat(users).isNotNull();
    }

    @Test
    @DisplayName("при первом запуске создаются категории и товары")
    void приПервомЗапускеСоздаютсяКатегорииИТовары() {
        assertThat(categories.count()).isPositive();
        assertThat(products.count()).isPositive();
    }

    @Test
    @DisplayName("при первом запуске создаётся администратор")
    void приПервомЗапускеСоздаётсяАдминистратор() {
        assertThat(users.existsByEmailIgnoreCase("admin@flower.local")).isTrue();
    }
}
```

Этот тест поднимает **всё приложение целиком** (на H2!) и проверяет, что:

* Spring-контекст собирается (все бины создаются, зависимости разрешаются);
* `SeedData` отработал — категории и товары созданы;
* `AuthService.ensureAdmin()` создал администратора.

> **Почему база — H2, а не рабочая PostgreSQL?** Потому что `SeedData` при
> старте **записывает** данные. Если бы тест работал на рабочей базе, он
> изменил бы её. Именно поэтому в `src/test/resources/application.properties`
> указан `jdbc:h2:mem:flowerstore-test` — база в памяти, исчезает после
> прогона.

### Кто самый «тяжёлый» тест

`@SpringBootTest` поднимает весь контекст — это медленно (несколько секунд).
Таких тестов в проекте 3, и это правильно: **не надо** тестировать всё
через полный контекст. Полный контекст нужен только там, где важна
проверка «вся конструкция собирается».

---

## 10.10. JaCoCo и покрытие кода

### Что измеряет JaCoCo

JaCoCo — инструмент, который вставляет в код «счётчики» и после прогона
тестов считает:

* **покрытые строки** — строки, которые выполнились хотя бы раз;
* **покрытые ветви** — `if`/`else`, где выполнились **оба** варианта;
* **покрытые методы** — методы, которые вызывали;
* **покрытые классы**.

### Как запустить

```bash
mvn clean verify
```

`verify` — фаза Maven, которая идёт после `test` и запускает отчёт JaCoCo.

Отчёт лежит в:

```
target/site/jacoco/index.html
```

Откройте его в браузере — это HTML-страница с цветной таблицей: зелёный
(покрыто), жёлтый (частично), красный (не покрыто).

### Покрытие в этом проекте

| Пакет | Покрытие |
|-------|----------|
| `shop.service` | **100 %** (207/207 строк) |
| `shop.model` | **100 %** |
| `shop.exception` | **100 %** |
| `shop.controller` | **100 %** |
| `shop` (корневой: `FlowerStoreApplication`, `SeedData`) | 94,4 % (34/36) |
| **Весь проект** | **99,5 %** (401/403) |

Две непокрытые строки — это, скорее всего, `main()` и что-то в `SeedData`,
что невозможно вызвать в тесте иначе, чем «прогнать приложение целиком».

### Как читать покрытие и не попасть в ловушку

> **Покрытие — это не цель, а диагностика.**
>
> 100 % покрытия не означает, что код правильный. Можно написать тест,
> который вызывает метод и ничего не проверяет — покрытие будет 100 %, а
> смысла ноль.
>
> И наоборот: покрытие 50 % может быть perfectly нормальным, если
> непокрытым остался код, который невозможно вызвать (например,
> обработчик `catch (Exception e)` для невозможной ошибки).

Правильный вопрос не «какое у меня покрытие?», а: **«есть ли здесь код,
> который никто не проверяет, и является ли он важным?»**

В нашем проекте покрытие 99,5 % — это следствие **дисциплины**, а не
цели: каждое бизнес-правило из главы 7 имеет тест.

### Настройка JaCoCo в `pom.xml`

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>${jacoco.version}</version>
  <executions>
    <execution>
      <id>prepare-agent</id>
      <goals><goal>prepare-agent</goal></goals>
      <configuration>
        <!-- Служебные классы, которые Mockito генерирует в рантайме, не инструментируем -->
        <excludes>
          <exclude>*$MockitoMock*</exclude>
        </excludes>
      </configuration>
    </execution>
    <execution>
      <id>report</id>
      <phase>verify</phase>
      <goals><goal>report</goal></goals>
    </execution>
  </executions>
</plugin>
```

* `prepare-agent` — подключить «агента», который инструментирует код. Он
  запускается **до** тестов.
* `report` в фазе `verify` — после тестов собрать HTML-отчёт.
* `excludes` — не инструментировать классы, которые Mockito **генерирует в
  рантайме** (`$MockitoMock$...`). Это служебные классы, они не часть
  проекта, и без исключения JaCoCo ругается на них.

---

## 10.11. TDD — разработка через тесты

### Что это такое

**TDD** (Test-Driven Development) — порядок работы, при котором:

1. **Красный.** Пишем тест, описывающий нужное поведение. Он падает
   (потому что кода ещё нет).
2. **Зелёный.** Пишем минимальный код, чтобы тест прошёл.
3. **Рефакторинг.** Улучшаем код, сохраняя зелёный статус.

Цикл занимает минуты и повторяется много раз.

### Зачем это нужно

* **Вы не забудете про крайние случаи.** Когда вы сначала думаете «какие
  бывают ситуации», вы сами себе подсказываете тесты.
* **Вы не напишете лишнего.** Реализация ровно та, что нужна для теста.
* **Вы не сможете «сломать и забыть».** Зелёный тест — это зафиксированное
  поведение.

### Как это выглядит на практике (на примере проекта)

Допустим, вы хотите добавить правило: «в корзине не может быть больше 50
позиций».

**Шаг 1. Красный.**

```java
@Test
@DisplayName("не больше 50 позиций в корзине")
void неБольше50Позиций() {
    when(products.findById(1L)).thenReturn(Optional.of(roses(1000)));

    for (int i = 0; i < 50; i++) cart.add(session, 1L, 1);   // 50 позиций... хотя это одна позиция
    ...
}
```

Правильный тест (одна позиция, но много разных товаров):

```java
@Test
@DisplayName("больше 50 позиций в корзине отклоняется")
void больше50ПозицийОтклоняется() {
    when(products.findById(anyLong())).thenReturn(Optional.of(roses(1000)));

    for (long id = 1; id <= 50; id++) cart.add(session, id, 1);

    ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 51L, 1));

    assertThat(error).isNotNull();
    assertThat(error.status).isEqualTo(409);
}
```

Запускаем → **красный**: метода/проверки нет, тест падает.

**Шаг 2. Зелёный.** Добавляем в `CartService.add`:

```java
check(cart.size() < 50 || cart.containsKey(productId), "Слишком много товаров в корзине");
```

Запускаем → **зелёный**.

**Шаг 3. Рефакторинг.** Выносим «50» в константу:

```java
private static final int MAX_ITEMS = 50;
```

Запускаем → всё ещё зелёный.

### Проект написан «по TDD-духу»

Даже если код писался не в строгом TDD-порядке, результат выглядит так же:
на каждое правило из главы 7 есть тест с понятным названием. Именно поэтому
глава 7.7 выглядит как «список требований с галочками».

---

## 10.12. Как читать красный отчёт

Когда тест падает, IntelliJ показывает красную полосу. Не пугайтесь — читайте
сообщение.

### Пример 1: проверка не выполнилась

```
org.opentest4j.AssertionFailedError:
  Expecting actual: 4
  to be equal to: 7
```

Читается: «ожидал 4, а получил 7». Значит, списание остатка считается не так,
как вы думали. **Что делать:** посмотрите код метода и пересчитайте вручную.

### Пример 2: `NullPointerException`

```
java.lang.NullPointerException: Cannot invoke "String.getMessage()" because "e" is null
```

Частая причина: вы ожидали исключение, а его не было — и код продолжил
работать с `null`. Или наоборот: исключение было, но не того типа.

**Что делать:** проверьте `catchThrowable` и тип приведения.

### Пример 3: Mockito жалуется на лишний вызов

```
org.mockito.exceptions.misusing.UnnecessaryStubbingException:
  Unnecessary stubbing: products.findById(1L)
```

Это **не ошибка теста**, а **подсказка**: вы настроили мок, но код его не
вызвал. Значит, либо тест проверяет не то, либо в коде есть лишний вызов.

### Пример 4: ошибка в самом моке

```
UnnecessaryStubbingException: Unnecessary stubbing: products.findById(1L)
→ 1. products.findById(1L)
→ 2. products.findById(1L)
```

Mockito заметил, что вы **переопределили** одну и ту же настройку дважды.
Обычно это значит, что стоит объединить их в `thenReturn(a, b)` (первый
вызов — `a`, второй — `b`).

### Пример 5: тест неожиданно проходит

Если тест падал, а после вашего «исправления» неожиданно начал проходить —
проверьте, что вы не **удалили** проверку. Пустой тест (без `assertThat`)
всегда «зелёный».

> **Правило:** тест, который ничего не проверяет, — хуже отсутствующего
> теста, потому что создаёт ложное чувство защищённости.

---

## 10.13. Как написать новый тест: пошаговая инструкция

Допустим, вы изменили `CartService` и хотите проверить новое поведение.

### Шаг 1. Найдите нужный тест-класс

| Что меняете | Где тест |
|-------------|----------|
| `CartService` | `CartServiceTest` |
| `OrderService` | `OrderServiceTest` |
| `CatalogService` | `CatalogServiceTest` |
| `AuthService` | `AuthServiceTest` |
| `Product`, `Order`, `OrderStatus` | `ProductTest`, `OrderTest`, `OrderStatusTest` |
| `ApiException`, `ErrorHandler` | `ApiExceptionTest`, `ErrorHandlerTest` |
| Контроллер | `ShopControllerTest` / `ApiControllerTest` / `AdminControllerTest` |
| Репозиторий | `RepositoryDataTest` |

### Шаг 2. Напишите `@DisplayName` как утверждение о поведении

```java
@DisplayName("при нулевом остатке товар в корзину не попадает")
```

Читается как требование. Если не получается сформулировать — значит, вы
ещё не до конца понимаете, что должно произойти. Это полезный сигнал!

### Шаг 3. Подготовьте данные (Arrange)

```java
when(products.findById(1L)).thenReturn(Optional.of(roses(0)));
```

### Шаг 4. Выполните действие (Act)

```java
ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 1L, 1));
```

### Шаг 5. Проверьте результат (Assert)

```java
assertThat(error).isNotNull();
assertThat(error.status).isEqualTo(409);
assertThat(cart.raw(session)).isEmpty();
```

Проверяйте **всё**, что важно: код ошибки, текст, и — главное — **состояние
после операции** (корзина не изменилась? остаток не списался?).

### Шаг 6. Запустите и убедитесь, что тест **падает**

Это обязательный шаг! Новый тест должен сначала **провалиться** — иначе вы не
проверили, что он вообще что-то ловит. Простой способ: временно сломайте
код (например, уберите проверку) и убедитесь, что тест покраснел.

### Шаг 7. Запустите весь набор

```bash
mvn test
```

Все тесты должны быть зелёными. Если ваш новый тест прошёл, а какой-то
другой сломался — скорее всего, вы изменили поведение, которое где-то
ещё описано.

---

## 10.14. Частые ошибки новичков

| Ошибка | Почему это плохо | Как правильно |
|--------|------------------|---------------|
| Один тест проверяет всё подряд | непонятно, что именно сломалось | один `@Test` — одно поведение |
| Нет `@DisplayName` | в отчёте — технические имена | пишите по-русски, как предложение |
| Тест без `assertThat` | «зелёный» тест ничего не проверяет | минимум одна проверка |
| Общее состояние между тестами | тесты зависят от порядка | всё создавайте в `@BeforeEach` |
| `assertEquals` для `BigDecimal` | `30.0` ≠ `30.00` | `isEqualByComparingTo` |
| Проверка только «не упало» | не проверяет результат | проверяйте код, текст и состояние |
| Один огромный тест-класс на 500 строк | невозможно найти нужный | `@Nested`-группы |
| Тест на «свой же код» (копипаст реализации) | тест сломается вместе с кодом | проверяйте **поведение**, а не внутренности |
| `Thread.sleep` в тестах | медленно и ненадёжно | тестируйте без времени или используйте `Clock` |

---

## 10.15. Команды Maven для тестов

| Команда | Что делает |
|---------|------------|
| `mvn test` | скомпилировать и запустить все тесты |
| `mvn test -Dtest=CartServiceTest` | только один тест-класс |
| `mvn test -Dtest=CartServiceTest#добавлениеТовараКладётЕгоВКорзину` | один конкретный тест |
| `mvn clean verify` | тесты + отчёт JaCoCo (полная проверка перед сдачей) |
| `mvn -o test` | то же, но без обращения к интернету (offline) |

> `-Dtest=ИмяКласса` — очень полезная вещь при разработке: не ждать все 211
> тестов, пока ищешь ошибку.

---

## 10.16. Главное из главы

1. Тест — это исполняемое требование: он не устаревает и защищает от
   регрессий.
2. **154 из 211 тестов** не требуют ни Spring, ни базы, ни HTTP — это
   следствие правильной архитектуры.
3. JUnit 5: `@Test`, `@BeforeEach`, `@Nested`, `@DisplayName` — каркас.
4. AssertJ: `assertThat(...)` читается как предложение;
   `isEqualByComparingTo` — для денег; `catchThrowable` — для ошибок.
5. Mockito: `mock`, `when/thenReturn`, `verify`, `doThrow`, `doAnswer`.
6. `TestFixtures.fakeSession()` — сессия на обычной `HashMap`.
7. `@WebMvcTest` + `MockMvc` — проверка маршрутов, кодов, JSON и редиректов.
8. `@DataJpaTest` + H2 — проверка самих запросов; `@SpringBootTest` —
   проверка, что всё собирается.
9. JaCoCo: отчёт в `target/site/jacoco/index.html`; покрытие — диагностика,
   а не цель.
10. TDD: красный → зелёный → рефакторинг.
11. Новый тест должен **сначала упасть** — иначе вы не знаете, что он
    ловит.

**Вопросы для самопроверки**

1. Почему `mvn test` в этом проекте **не трогает** рабочую PostgreSQL?
2. Зачем нужен `catchThrowable`, если есть `assertThrows`?
3. Почему для `BigDecimal` используется `isEqualByComparingTo`, а не
   `isEqualTo`? Приведите пример двух разных, но равных значений.
4. Что означает «тесты должны быть независимы»? Как это обеспечивается в
   `CartServiceTest`?
5. Зачем в `ApiControllerTest` есть проверка `jsonPath("$.passwordHash").doesNotExist()`?
6. Чем `@WebMvcTest` отличается от `@SpringBootTest`? Что каждый из них
   поднимает?
7. Что такое «покрытие 100 %» и почему оно **не** гарантирует отсутствие
   ошибок?
8. Вы изменили правило «не больше 50 позиций». Какой тест может упасть и
   почему это хорошо?

---

*Дальше: [Глава 11. Как запустить у себя](11-how-to-run.md) — практическая
инструкция от установки JDK до работающего магазина.*
