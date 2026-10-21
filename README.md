# Электронный магазин цветов «Flower Store»

Курсовой проект по дисциплине **«Модульное тестирование ПО»**.

Веб-приложение интернет-магазина цветов и букетов: каталог с фильтрами, корзина
(в том числе для гостя), оформление заказа, личный кабинет с историей заказов
и панель администратора для управления товарами, категориями и заказами.

Бэкенд — Spring Boot 3.3.5 (Java 17), Spring Data JPA, PostgreSQL.
Фронтенд — статические HTML-страницы + JavaScript (без фреймворков).

---

## 1. Что нужно установить

| Что | Версия | Зачем |
|-----|--------|-------|
| JDK | **17–23** (рекомендуется 17 или 21) | сборка и запуск |
| Maven | 3.8+ | сборка, тесты, отчёт о покрытии |
| PostgreSQL | 13+ | рабочая база приложения (для тестов не нужна) |

> **Почему не «17 и новее».** Spring Boot 3.3.5, Mockito 5.11 и JaCoCo 0.8.12
> официально поддерживают Java до 23. На более новых JDK (например, Java 27)
> проект тоже собирается и тесты проходят — в `pom.xml` включён флаг
> `net.bytebuddy.experimental=true`, — но агент JaCoCo печатает предупреждения
> о классах, сгенерированных на лету. Поэтому для чистого вывода и корректного
> отчёта о покрытии используйте JDK 17–23.
>
> Если тесты запускаются из IntelliJ IDEA, проверьте, что в *Settings → Build,
> Execution, Deployment → Build Tools → Maven → Runner* и в *Project Structure →
> Project* выбран JDK 17–23, а не 27.

---

## 2. Запуск приложения

### 2.1. Подготовка базы

Создайте базу и примените скрипты:

```bash
createdb -U postgres flowerstore
psql -U postgres -d flowerstore -f src/main/resources/db/schema.sql
psql -U postgres -d flowerstore -f src/main/resources/db/seed.sql
```

* `schema.sql` — создаёт таблицы (`categories`, `users`, `products`, `orders`, `order_item`).
* `seed.sql` — демо-данные: администратор, покупатель, 4 категории, 8 товаров, 2 заказа.

> Схему может создать и Hibernate сам (`spring.jpa.hibernate.ddl-auto=update`),
> а демо-данные добавляет класс `SeedData` при первом запуске.
> SQL-скрипты нужны для развёртывания «с нуля» и для проверки структуры БД.

### 2.2. Запуск

```bash
mvn spring-boot:run
```

Приложение откроется на <http://localhost:8080>.

Адрес и пароль к PostgreSQL задаются переменными окружения
(значения по умолчанию — `localhost:5432`, пользователь `postgres`, пароль `123`):

```bash
DB_URL=jdbc:postgresql://localhost:5432/flowerstore DB_USER=postgres DB_PASSWORD=123 mvn spring-boot:run
```

Данные для входа (создаются скриптом `seed.sql`):

| Роль | Логин | Пароль |
|------|-------|--------|
| Администратор | `admin@flower.local` | `admin123` |
| Покупатель | `ivan@mail.local` | `user123` |

---

## 3. Модульные тесты

### 3.1. Запуск — одна команда

```bash
mvn test
```

Тесты **не обращаются к рабочей базе PostgreSQL**: во время сборки
используется встроенная H2 (см. `src/test/resources/application.properties`),
поэтому рабочие данные не пострадают.

Полный прогон с отчётом о покрытии и сборкой jar-файла:

```bash
mvn clean verify
```

Результат последнего прогона: **211 тестов, 0 ошибок, 0 падений**,
`BUILD SUCCESS`, отчёт о покрытии — `target/site/jacoco/index.html`.

### 3.2. Отчёт о покрытии кода

После `mvn clean verify` отчёт JaCoCo лежит здесь:

```
target/site/jacoco/index.html
```

Открыть в браузере:

```bash
start target/site/jacoco/index.html     # Windows
```

Текущее покрытие:

| Слой | Покрытие строк |
|------|----------------|
| `shop.service` (сервисный слой — цель курсового проекта) | **100 %** |
| `shop.model` | 100 % |
| `shop.exception` | 100 % |
| `shop.controller` | 100 % |
| `shop` (запуск, демо-данные) | 94 % (не вызваны `main()`) |
| **Весь проект** | **≈ 99 %** |

Требование ТЗ — не менее 70–80 % по сервисному слою — выполнено с запасом.

### 3.3. Какие тесты написаны

| Класс тестов | Что проверяет | Тестов |
|---------------|---------------|--------|
| `shop.service.CartServiceTest` | корзина: добавление, остатки, суммы, нормализация, удаление | 24 |
| `shop.service.OrderServiceTest` | оформление заказа, списание остатка, статусы, возврат на склад | 34 |
| `shop.service.CatalogServiceTest` | валидация товара, категории, видимость, сортировки | 33 |
| `shop.service.AuthServiceTest` | регистрация, вход, роли, профиль | 28 |
| `shop.controller.ApiControllerTest` | JSON-API: маршруты, коды ответов | 14 |
| `shop.controller.ShopControllerTest` | действия покупателя из форм, редиректы | 18 |
| `shop.controller.AdminControllerTest` | админка и разграничение доступа | 11 |
| `shop.repository.RepositoryDataTest` | запросы репозиториев на H2 (`@DataJpaTest`) | 11 |
| `shop.model.OrderStatusTest`, `OrderTest`, `ProductTest` | модели и правила переходов | 21 |
| `shop.exception.ApiExceptionTest`, `ErrorHandlerTest` | ошибки бизнес-логики и их обработка | 14 |
| `shop.FlowerStoreApplicationTests` | контекст Spring и демо-данные | 3 |
| **Всего** | | **211** |

Подробное описание сценариев — в [`docs/ПОЯСНИТЕЛЬНАЯ_ЗАПИСКА.md`](docs/ПОЯСНИТЕЛЬНАЯ_ЗАПИСКА.md).

### 3.4. Используемые инструменты

* **JUnit 5** — тестовый фреймворк (`@Test`, `@Nested`, `@DisplayName`).
* **Mockito** — моки репозиториев и HTTP-сессии; тесты сервисного слоя
  работают без базы данных.
* **AssertJ** — читаемые проверки (`assertThat(...)`, `catchThrowable(...)`).
* **JaCoCo** — отчёт о покрытии кода.
* **Spring Boot Test** — `@WebMvcTest` (веб-слой) и `@DataJpaTest` (доступ к данным на H2).

---

## 4. Структура проекта

```
src/main/java/shop/
├── FlowerStoreApplication.java   точка входа, бин BCryptPasswordEncoder
├── SeedData.java                 демо-категории, товары и администратор
├── model/                        сущности JPA: Product, Category, User, Order, OrderItem, OrderStatus, Role
├── repository/                   Spring Data JPA: ProductRepo, CategoryRepo, UserRepo, OrderRepo
├── service/                      бизнес-логика: CartService, OrderService, CatalogService, AuthService
├── controller/                   ApiController (JSON), ShopController, AdminController, ErrorHandler
└── exception/                    ApiException (400/401/403/404/409)

src/main/resources/
├── application.properties        настройки (БД, порт, администратор)
├── db/schema.sql                 DDL-скрипт для PostgreSQL
├── db/seed.sql                   демо-данные
└── static/                       страницы, стили и скрипты фронтенда

src/test/java/shop/
├── support/TestFixtures.java     общая оснастка: готовые сущности и «настоящая» сессия
├── service/                      модульные тесты сервисного слоя (Mockito, без БД)
├── controller/                   тесты веб-слоя (@WebMvcTest)
├── repository/                   тесты доступа к данным (@DataJpaTest + H2)
├── model/, exception/            тесты моделей и обработки ошибок
└── FlowerStoreApplicationTests   проверка, что приложение собирается целиком
```

---

## 5. Возможности приложения

**Покупатель**
* каталог с фильтром по категории и сортировкой (по цене, по добавлению);
* карточка товара, добавление в корзину, изменение количества, удаление;
* оформление заказа без регистрации (в форме достаточно e-mail **или** телефона);
* регистрация, вход, выход, личный кабинет, история заказов, повтор заказа;
* форма обратной связи.

**Администратор** (`admin@flower.local` / `admin123`)
* товары: создание, редактирование, скрытие/показ, удаление;
* категории: создание, переименование, удаление (если в категории нет товаров);
* заказы: смена статуса по правилам переходов, возврат товара на склад при отмене;
* список пользователей.

**Правила статусов заказа:** `новый → в обработке → доставлен`,
из «нового» и «в обработке» можно отменить, из «доставленного» и «отменённого» — нельзя.

---

## 6. Учебник по проекту

В репозитории есть **книга-учебник** — подробное объяснение всего проекта
для читателя, который ещё ни разу не программировал.

📖 **[Flower Store: учебник по устройству интернет-магазина](docs/book/README.md)**

13 глав, каждая — с реальным кодом проекта:

| № | Глава | О чём |
|---|-------|-------|
| 1 | [Что делает программа](docs/book/01-what-is-shop.md) | полный сценарий покупателя и администратора |
| 2 | [Словарь новичка](docs/book/02-language-basics.md) | Java, HTTP, HTML, JSON, cookies и сессия |
| 3 | [Как работает Spring Boot](docs/book/03-spring-boot.md) | контейнер, внедрение зависимостей, аннотации |
| 4 | [База данных и JPA](docs/book/04-database.md) | таблицы, связи, Hibernate, Spring Data, транзакции |
| 5 | [Архитектура проекта](docs/book/05-architecture.md) | слои и «путешествие запроса» от кнопки до базы |
| 6 | [Модель данных](docs/book/06-model.md) | построчный разбор всех сущностей |
| 7 | [Сервисный слой](docs/book/07-services.md) | вся бизнес-логика проекта |
| 8 | [Веб-слой](docs/book/08-controllers.md) | маршруты, коды ответов, обработка ошибок |
| 9 | [Фронтенд](docs/book/09-frontend.md) | статические страницы, `fetch`, формы |
| 10 | [Тестирование](docs/book/10-testing.md) | JUnit, Mockito, AssertJ, `@WebMvcTest`, JaCoCo |
| 11 | [Как запустить у себя](docs/book/11-how-to-run.md) | пошаговая инструкция от JDK до работающего магазина |
| 12 | [Типичные проблемы](docs/book/12-troubleshooting.md) | Byte Buddy и JDK 27, порт, кодировка, ошибки компиляции |
| 13 | [Итоги и задания](docs/book/13-summary.md) | карта знаний, вопросы, задания для самостоятельной работы |

Книга полезна в двух случаях: как **учебное пособие** при разборе проекта
защитнику и как **справочник** при подготовке к защите.
