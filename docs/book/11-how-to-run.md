# Глава 11. Как запустить у себя

> **Что мы уже знаем:** мы разобрали всё устройство проекта и научились
> запускать тесты. Теперь — практическая глава: пошаговая инструкция, как
> поднять работающий магазин на вашем компьютере, от установки JDK до
> открытия сайта в браузере.
>
> Следуйте шагам по порядку. Если что-то пошло не так — сразу переходите к
> [главе 12 «Типичные проблемы»](12-troubleshooting.md).

---

## 11.1. Что нужно установить

| Что | Версия | Зачем | Обязательно? |
|-----|--------|-------|--------------|
| **JDK** | 17–23 (лучше 17 или 21) | компиляция и запуск | Да |
| **Maven** | 3.8+ | сборка, запуск, тесты | Да (или IntelliJ IDEA) |
| **PostgreSQL** | 13+ | рабочая база приложения | Да |
| **IntelliJ IDEA** | Community или Ultimate | удобная разработка | Нет, но очень желательно |
| **Браузер** | любой современный | проверить результат | Да |

### Почему JDK 17–23, а не «последний»

Это важное ограничение, о котором стоит знать сразу:

* **Spring Boot 3.3.5** официально поддерживает Java до 23.
* **Mockito 5.11** (внутри — Byte Buddy 1.14.19) поддерживает Java до 23.
* **JaCoCo 0.8.12** не умеет инструментировать классы новых версий.

Что произойдёт на JDK 27 (и выше):

* проект **соберётся** и тесты **пройдут** — в `pom.xml` включён флаг
  `net.bytebuddy.experimental=true`, разрешающий Byte Buddy работать на
  новых JDK;
* но агент JaCoCo начнёт печатать предупреждения вида
  `Unknown class file major version 71`, и отчёт о покрытии может быть
  неполным.

**Вывод:** для чистого вывода и корректного отчёта используйте JDK 17–23.
Если в IntelliJ у вас стоит JDK 27 — переключитесь (см. раздел 11.7).

### Установка JDK

Скачайте **Temurin 17** (или Liberica JDK 21) с сайта
<https://adoptium.net/>. Распакуйте, например, в `C:\Users\User\.jdks\`:

```
C:\Users\User\.jdks\temurin-17.0.11
```

Проверьте, что Java видна в системе:

```powershell
java -version
```

Ожидаемый вывод:

```
openjdk version "17.0.11" 2024-04-16
OpenJDK Runtime Environment Temurin-17.0.11+9
OpenJDK 64-Bit Server VM Temurin-17.0.11+9
```

### Установка Maven

Скачайте бинарник с <https://maven.apache.org/download.cgi> и распакуйте.
Затем задайте переменную окружения `PATH`, чтобы `mvn` был доступен из
командной строки.

Проверка:

```powershell
mvn -version
```

Должно быть видно **и** версию Maven, **и** версию JDK, которую Maven
использует:

```
Apache Maven 3.9.9
Java version: 17.0.11, vendor: Eclipse Adoptium
```

> **Важно:** `mvn -version` показывает, какую Java использует Maven. Если
> там Java 27, а вы думали, что используете 17 — вот и источник странных
> ошибок (глава 12).

### Установка PostgreSQL

Скачайте с <https://www.postgresql.org/download/>. При установке:

* запомните **пароль** пользователя `postgres` — он понадобится;
* оставьте порт `5432`;
* в списке компонентов можно снять «pgAdmin» и «Command Line Tools», если
  они не нужны.

После установки PostgreSQL **запускается как служба Windows** — проверьте
в «Службах» (services.msc), что `postgresql-x64-...` в состоянии «Выполняется».

---

## 11.2. Подготовка базы данных

### Создание базы

Откройте командную строку PostgreSQL (или выполните в PowerShell):

```powershell
createdb -U postgres flowerstore
```

Если `createdb` не найден — добавьте в `PATH` папку
`C:\Program Files\PostgreSQL\<версия>\bin`.

Либо через `psql`:

```powershell
psql -U postgres -c "CREATE DATABASE flowerstore;"
```

Если база уже есть — ошибка `database "flowerstore" already exists` это
не страшно, всё можно продолжать.

### Применение скриптов

```powershell
psql -U postgres -d flowerstore -f src/main/resources/db/schema.sql
psql -U postgres -d flowerstore -f src/main/resources/db/seed.sql
```

`psql` спросит пароль пользователя `postgres` — введите тот, что вы задали
при установке.

### Что делают скрипты

**`schema.sql`** — создаёт таблицы с нуля:

```sql
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS users;

CREATE TABLE categories (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
...
```

> ⚠️ **Внимание!** `schema.sql` начинается с `DROP TABLE IF EXISTS` —
> он **удаляет** существующие таблицы вместе с данными. Запускайте его
> только на пустой базе. Если нужно просто добавить демо-данные — хватит
> одного `seed.sql`.

**`seed.sql`** — добавляет демо-данные:

| Что | Сколько |
|-----|---------|
| Администратор `admin@flower.local` | 1 |
| Покупатель `ivan@mail.local` | 1 |
| Категории (Розы, Тюльпаны, Букеты, Горшечные) | 4 |
| Товары (один скрыт, один с нулевым остатком) | 8 |
| Заказы (доставлен и новый) | 2 |

### А можно и без скриптов?

Да. В `application.properties` стоит `spring.jpa.hibernate.ddl-auto=update` —
Hibernate сам создаст таблицы. А демо-данные добавит класс `SeedData` при
первом запуске (если база пуста).

**Но скрипты всё равно нужны**, потому что:

* они показывают **реальную структуру БД** — её можно прочитать и
  сравнить с моделями (глава 4);
* при развёртывании на «чистом» сервере это стандартный способ;
* `seed.sql` содержит **готовые хеши паролей** — их не нужно считать.

---

## 11.3. Настройка подключения

По умолчанию проект ожидает:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/flowerstore}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:123}
```

То есть: сервер `localhost`, порт `5432`, база `flowerstore`, пользователь
`postgres`, пароль `123`.

Если ваш пароль другой — **не правьте файл**, а задайте переменную
окружения. Это правильнее: в файле не должно быть секретов.

### PowerShell

```powershell
$env:DB_PASSWORD="ваш_пароль"
mvn spring-boot:run
```

Переменная живёт только в текущем окне PowerShell. Чтобы задать её
постоянно:

```powershell
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "ваш_пароль", "User")
```

После этого перезапустите терминал.

### cmd

```cmd
set DB_PASSWORD=ваш_пароль
mvn spring-boot:run
```

### IntelliJ IDEA

**Run → Edit Configurations → Environment variables**:

```
DB_PASSWORD=ваш_пароль;DB_USER=postgres;DB_URL=jdbc:postgresql://localhost:5432/flowerstore
```

Так же задаются и логин/пароль администратора:

```
ADMIN_EMAIL=admin@flower.local
ADMIN_PASSWORD=admin123
```

---

## 11.4. Сборка и запуск

### Через Maven (из командной строки)

```powershell
mvn spring-boot:run
```

Через несколько секунд вы увидите:

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.3.5)

2026-10-21 13:01:12.345  INFO 1 --- [ main] shop.FlowerStoreApplication : Starting FlowerStoreApplication
2026-10-21 13:01:15.123  INFO 1 --- [ main] shop.SeedData             : Добавлены демо-категории и товары
2026-10-21 13:01:16.789  INFO 1 --- [ main] o.s.b.w.e.t.TomcatWebServer  : Tomcat started on port 8080 (http) with context path ''
2026-10-21 13:01:16.790  INFO 1 --- [ main] shop.FlowerStoreApplication : Started FlowerStoreApplication in 4.512 seconds
```

Откройте <http://localhost:8080/index.html>.

### Через IntelliJ IDEA

1. Откройте папку проекта (File → Open).
2. Дождитесь, пока IntelliJ скачает зависимости Maven (внизу — индикатор).
3. Откройте `src/main/java/shop/FlowerStoreApplication.java`.
4. Нажмите **зелёную стрелку** рядом с `main` → **Run 'FlowerStoreApplication'**.

> **Важно:** проверьте, что в *Project Structure → Project → SDK* выбран
> JDK 17–23, а в *Settings → Build Tools → Maven → Runner → JRE* — тот же
> JDK. Иначе получите странные ошибки (глава 12).

### Остановить приложение

Нажмите **Ctrl+C** в консоли или кнопку ■ (Stop) в IntelliJ.

---

## 11.5. Проверка, что всё работает

### Шаг 1. Главная страница

<http://localhost:8080/index.html>

Должны быть: заголовок «Живые цветы с доставкой на дом», кнопка «Открыть
каталог», четыре категории и несколько товаров.

> Если страница «пустая» (есть шапка, но нет товаров), откройте консоль
> браузера (F12 → Console). Скорее всего, в базе нет данных — проверьте,
> что `seed.sql` выполнился.

### Шаг 2. Каталог и фильтры

<http://localhost:8080/catalog.html>

Выберите категорию «Розы», нажмите «Показать» — товаров станет меньше.
Выберите сортировку «Сначала дорогие» — первыми пойдут самые дорогие.

### Шаг 3. Корзина

1. Нажмите «В корзину» у любого товара.
2. Должно появиться зелёное сообщение «Товар добавлен в корзину».
3. В шапке рядом со словом «Корзина» появилось число `1`.
4. Откройте <http://localhost:8080/cart.html> — товар там есть.

### Шаг 4. Оформление заказа

1. В корзине нажмите «Оформить заказ».
2. Заполните имя и адрес (e-mail или телефон — хотя бы одно).
3. Нажмите «Подтвердить заказ».
4. Вы должны увидеть «Спасибо за заказ, номер заказа N».
5. Откройте админку и посмотрите, что заказ появился со статусом «Новый».

### Шаг 5. Вход покупателя

<http://localhost:8080/login.html>

| Логин | Пароль |
|-------|--------|
| `ivan@mail.local` | `user123` |
| `admin@flower.local` | `admin123` |

После входа в шапке появится ваше имя и ссылка «Личный кабинет».

### Шаг 6. Админка

Войдите как `admin@flower.local` и откройте
<http://localhost:8080/admin/products.html>.

Проверьте:

* список всех товаров (включая скрытый);
* создание товара;
* **смена статуса заказа**: откройте `/admin/orders.html` и нажмите
  «В обработке», затем «Доставлен». После «Доставлен» кнопок не должно
  быть;
* **отмена заказа**: у заказа в статусе «Новый» нажмите «Отменить» —
  товары должны вернуться на склад (проверьте остаток в списке товаров).

### Шаг 7. Проверка API

Откройте в браузере <http://localhost:8080/api/products> — увидите JSON:

```json
[
  {"id":1,"name":"Роза красная, 60 см","price":4.50,"description":"...","imageUrl":null,"stock":100,"visible":true,"categoryId":1,"categoryName":"Розы"},
  ...
]
```

А <http://localhost:8080/api/auth/me> для гостя вернёт **пустой** ответ с кодом
204 (в браузере — пустая страница).

---

## 11.6. Запуск тестов

### Команда

```powershell
mvn test
```

Через ~10 секунд вы увидите:

```
[INFO] Tests run: 211, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Запуск одного теста

```powershell
mvn test -Dtest=CartServiceTest
```

Или конкретного метода:

```powershell
mvn test -Dtest=CartServiceTest#повторноеДобавлениеСуммируетКоличество
```

> В PowerShell `#` — начало комментария! Обязательно заключите аргумент в
> кавычки: `mvn test "-Dtest=CartServiceTest#метод"`.

### Полная проверка с отчётом о покрытии

```powershell
mvn clean verify
```

Это команда, которую нужно выполнить **перед сдачей работы**. Она:

1. удаляет папку `target` (`clean`);
2. компилирует всё с нуля;
3. запускает все 211 тестов (`test`);
4. собирает `jar`-файл (`package`);
5. генерирует отчёт о покрытии (`verify` + JaCoCo).

Результат:
```
[INFO] Tests run: 211, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] --- jacoco:0.8.12:report (report) @ flower-store ---
[INFO] Analyzed bundle 'target/flower-store-1.0.jar'
```

### Отчёт о покрытии

```
target/site/jacoco/index.html
```

Открыть:

```powershell
start target/site/jacoco/index.html
```

Что смотреть в отчёте:

1. **Общая сводка** (верхняя таблица) — общий процент.
2. **Красные строки** — код, который ни один тест не выполнил.
3. **Жёлтые ветви** — `if`, где выполнилась только одна сторона.

В текущем проекте: `shop.service` — 100 %, `shop.model`,
`shop.exception`, `shop.controller` — 100 %, `shop` — 94 %, весь проект —
≈ 99,5 %.

### Из IntelliJ IDEA

Правый клик по папке `src/test/java` → **Run 'All Tests'**. Или зелёная
стрелка рядом с конкретным тестовым методом.

> IntelliJ использует **тот JDK, что настроен в Project Structure**, а не
> тот, что в `JAVA_HOME`. Это частая причина расхождений (глава 12).

---

## 11.7. Полезные команды Maven

| Команда | Что делает |
|---------|------------|
| `mvn compile` | только компиляция (быстрая проверка компилируемости) |
| `mvn test` | компиляция + тесты |
| `mvn clean verify` | полная проверка + отчёт о покрытии + jar |
| `mvn package` | собрать `target/flower-store-1.0.jar` |
| `mvn spring-boot:run` | запустить приложение |
| `mvn spring-boot:run -Dspring-boot.run.profiles=dev` | запуск с профилем |
| `mvn clean` | удалить папку `target` |
| `mvn -o test` | без обращения к интернету (offline) |
| `mvn dependency:tree` | показать дерево зависимостей |
| `mvn help:effective-pom` | показать итоговый pom со всеми настройками родителя |

### Запуск собранного jar-файла

```powershell
mvn clean package
java -jar target/flower-store-1.0.jar
```

Это работает, потому что `spring-boot-maven-plugin` собирает **«толстый»**
jar — со всеми зависимостями внутри. Размер — около 50–60 МБ.

---

## 11.8. Структура проекта — «куда что класть»

```
Flower Store/
├── pom.xml                       зависимости и настройки сборки
├── README.md                     краткая инструкция (этот же проект, коротко)
├── .idea/                        настройки IntelliJ (не обязательно в git)
├── docs/
│   ├── ПОЯСНИТЕЛЬНАЯ_ЗАПИСКА.md   документ для преподавателя
│   └── book/                     ← ЭТА КНИГА
└── src/
    ├── main/
    │   ├── java/shop/
    │   │   ├── FlowerStoreApplication.java   точка входа
    │   │   ├── SeedData.java                  демо-данные
    │   │   ├── model/                         сущности JPA
    │   │   ├── repository/                    репозитории Spring Data
    │   │   ├── service/                       бизнес-логика
    │   │   ├── controller/                    HTTP-маршруты
    │   │   └── exception/                     ошибки и их обработка
    │   └── resources/
    │       ├── application.properties         настройки
    │       ├── db/schema.sql                  DDL для PostgreSQL
    │       ├── db/seed.sql                    демо-данные
    │       └── static/                        фронтенд
    └── test/
        ├── java/shop/                         211 тестов
        └── resources/application.properties   настройки тестов (H2)
```

---

## 11.9. Частые вопросы новичка

### «Мне нужно ставить PostgreSQL, чтобы сдать работу?»

Для **прогона тестов** — нет. `mvn test` работает на встроенной H2 и не
требует никакой базы.

Для **демонстрации** — да, нужен PostgreSQL (или любой совместимый сервер).

### «Можно ли запустить без базы вообще?»

Можно попробовать запустить, но `SeedData` упадёт с ошибкой подключения.
Чтобы приложение стартовало, нужна рабочая база.

### «Я изменил порт — где это сделать?»

В `src/main/resources/application.properties`:

```properties
server.port=8080
```

Или временно, без правки файла:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=9090"
```

### «Как посмотреть, какие SQL-запросы уходят в базу?»

Включите вывод SQL Hibernate:

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

**Не включайте это перед сдачей** — вывод будет огромным. Вместо этого
смотрите SQL в `psql`:

```sql
EXPLAIN ANALYZE SELECT * FROM products WHERE visible = TRUE;
```

### «Как сбросить базу и начать заново?»

```powershell
psql -U postgres -d flowerstore -f src/main/resources/db/schema.sql
psql -U postgres -d flowerstore -f src/main/resources/db/seed.sql
```

### «Как посмотреть, что записалось в базу?»

```powershell
psql -U postgres -d flowerstore
```

```
flowerstore=# SELECT id, name, price, stock, visible FROM products ORDER BY id;
 id |       name        | price | stock | visible
----+-------------------+-------+-------+---------
  1 | Роза красная, 60 см|  4.50 |   100 | t
  2 | Роза кремовая, 50 см| 3.80 |    40 | t
```

Полезные команды в `psql`:

| Команда | Что делает |
|---------|------------|
| `\dt` | список таблиц |
| `\d products` | структура таблицы |
| `\l` | список баз данных |
| `SELECT * FROM orders;` | все заказы |
| `\q` | выход |

### «Как остановить зависшее приложение?»

* В окне, где запущен Maven: **Ctrl+C**.
* В IntelliJ: кнопка ■ (Stop) на панели Run.
* Если порт 8080 занят «мёртвым» процессом (глава 12).

---

## 11.10. Чек-лист перед сдачей

- [ ] `mvn clean verify` → **211 тестов, BUILD SUCCESS**
- [ ] Отчёт `target/site/jacoco/index.html` открывается и показывает покрытие
      сервисного слоя **100 %**
- [ ] Приложение запускается: `mvn spring-boot:run`
- [ ] Главная страница показывает категории и товары
- [ ] Корзина работает: добавить, изменить количество, удалить
- [ ] Заказ оформляется, номер заказа показывается
- [ ] Вход работает: `ivan@mail.local` / `user123`
- [ ] Админка открывается только для администратора
- [ ] Смена статуса заказа работает, отмена возвращает товары на склад
- [ ] `README.md` и `docs/ПОЯСНИТЕЛЬНАЯ_ЗАПИСКА.md` актуальны
- [ ] Нет «TODO», закомментированного кода и файлов `backup`

---

## 11.11. Главное из главы

1. Требования: **JDK 17–23**, **Maven 3.8+**, **PostgreSQL 13+**.
2. База готовится двумя скриптами: `schema.sql` (таблицы) и `seed.sql`
   (данные). `schema.sql` **удаляет** таблицы — осторожно.
3. Пароль от базы задаётся переменной окружения `DB_PASSWORD`, а не
   правкой файла.
4. Запуск: `mvn spring-boot:run` → <http://localhost:8080/index.html>.
5. Тесты: `mvn test` (≈10 секунд). Они **не трогают** рабочую PostgreSQL.
6. Отчёт о покрытии: `mvn clean verify` → `target/site/jacoco/index.html`.
7. Перед сдачей — `mvn clean verify` и чек-лист 11.10.

**Вопросы для самопроверки**

1. Почему для тестов не нужна PostgreSQL, а для запуска приложения — нужна?
2. Что произойдёт, если запустить `schema.sql` на базе, в которой уже есть
   заказы?
3. Как задать другой порт, не правя `application.properties`?
4. Почему `mvn -version` важнее, чем кажется?
5. Какая команда Maven даёт и тесты, и отчёт о покрытии?

---

*Дальше: [Глава 12. Типичные проблемы](12-troubleshooting.md) — что делать,
когда что-то пошло не так.*
