# Глава 12. Типичные проблемы

> **Что мы уже знаем:** мы научились запускать проект и тесты. Эта глава —
> «коллокция неудачных ситуаций»: почти каждая проблема, с которой
> сталкивается новичок в этом проекте, уже решена. Здесь описано, как
> распознать проблему и что сделать.
>
> Проблемы разбиты по месту возникновения: **сборка**, **тесты**,
> **запуск**, **база данных**, **страницы**.

---

## 12.1. Проблемы сборки (Maven)

### 12.1.1. `Could not resolve dependencies` / `Failed to read artifact`

```
[ERROR] Could not resolve dependencies for project shop:flower-store:1.0:
  Could not find artifact org.postgresql:postgresql:jar:42.7.3
```

**Причина.** Maven не может скачать зависимость: нет интернета, или
повреждён локальный репозиторий.

**Что делать:**

1. Проверьте интернет.
2. Если вы уже скачивали зависимости раньше — используйте **offline-режим**:
   ```powershell
   mvn -o test
   ```
3. Если зависимости скачивались, но повреждены — удалите проблемный файл из
   локального репозитория и запустите снова:
   ```
   C:\Users\<Пользователь>\.m2\repository\org\postgresql\postgresql\
   ```
   Maven скачает его заново.
4. В IntelliJ: **Maven → Reload All Maven Projects** (кнопка ⟳ на панели
   Maven).

### 12.1.2. `release version 17 not supported`

```
[ERROR] Fatal error compiling: java.lang.IllegalArgumentException:
  release version 17 not supported
```

**Причина.** Maven компилирует на старом JDK (8 или 11), а в `pom.xml` стоит
`<java.version>17</java.version>`.

**Что делать.** Проверьте:

```powershell
mvn -version
```

Если там Java 8 — задайте `JAVA_HOME` (см. раздел 12.1.3) или в IntelliJ
выберите JDK в *Settings → Build Tools → Maven → Runner → JRE*.

### 12.1.3. Как правильно задать `JAVA_HOME` в PowerShell

```powershell
$env:JAVA_HOME="C:\Users\User\.jdks\temurin-17.0.11"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

Постоянно (для текущего пользователя):

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Users\User\.jdks\temurin-17.0.11", "User")
```

> После `SetEnvironmentVariable` нужно **закрыть и открыть** терминал —
> текущий процесс не увидит изменения.

### 12.1.4. `illegal character: '\ufeff'`

```
[ERROR] ...\src\main\java\shop\model\Product.java:[1,1] illegal character: '\ufeff'
```

**Причина.** В начало файла попал **BOM** (Byte Order Mark) — невидимый
служебный символ. Java его не допускает.

BOM появляется, когда файл создан в **Windows PowerShell 5.1** командой
`Set-Content -Encoding UTF8` (в PowerShell 5.1 `UTF8` = UTF8 **с BOM**).

**Как обнаружить:** откройте файл в «Блокноте» и сохраните как
«UTF-8 **без BOM**»; либо в IntelliJ: *File → File Properties → File encoding*
→ выбрать `UTF-8` (не `UTF-8 with BOM`).

**Как исправить в PowerShell:**

```powershell
$file = "src\main\java\shop\model\Product.java"
$text = Get-Content $file -Raw
[System.IO.File]::WriteAllText($file, $text, (New-Object System.Text.UTF8Encoding($false)))
```

Или в IntelliJ IDEA: **File → Save As → выбрать кодировку UTF-8 без BOM**.

> **Общее правило:** не редактируйте исходники командой `Set-Content -Encoding UTF8`
> в PowerShell 5.1. Используйте редактор кода (IDE) или
> `[System.IO.File]::WriteAllText` с `UTF8Encoding($false)`.

---

## 12.2. Проблемы с JDK и Byte Buddy (самый известный «баг» проекта)

### Симптом

```
java.lang.IllegalArgumentException:
  Java 27 (71) is not supported by the current version of Byte Buddy
  which officially supports Java 23 (67)
```

или (в сокращённом виде):

```
Mockito cannot mock this class: Java 27 (71) is not supported ...
```

### Причина

Byte Buddy — библиотека, которая генерирует моки «на лету» прямо в памяти.
Она должна знать структуру классов той версии Java, на которой вы работаете.
Версия Byte Buddy 1.14.19 (внутри Mockito 5.11) знает про Java до 23.

Если ваш IntelliJ или системный `JAVA_HOME` указывает на **JDK 27**, все
тесты с моками (`CartServiceTest`, `OrderServiceTest`, `CatalogServiceTest`,
`AuthServiceTest`, `ApiControllerTest`, `ShopControllerTest`,
`AdminControllerTest`) упадут с этой ошибкой.

### Важно понимать

**Это не ошибка вашего кода.** Проект написан на Java 17 и полностью
работоспособен. Проблема — в том, что окружение использует слишком новую
версию JDK для используемой версии Mockito.

### Решение 1 (рекомендуемое). Использовать JDK 17–23

В IntelliJ IDEA:

1. **File → Project Structure → Project** → **SDK** → выберите JDK 17 или 21
   (или нажмите *Add SDK* и укажите путь к установленному JDK).
2. **Settings → Build Tools → Maven → Runner → JRE** → тот же JDK.
3. **Settings → Build Tools → Maven → Importing → JDK for importer** → тот же
   JDK.
4. **Settings → Build, Execution, Deployment → Build Tools → Maven →
   Maven home path** → проверьте, что выбран ваш Maven, а не «bundled» с
   неправильным JDK.

Затем: **Maven → Reload All Maven Projects** и снова запустите тесты.

В PowerShell:

```powershell
$env:JAVA_HOME="C:\Users\User\.jdks\liberica-full-23.0.2"
mvn -o test
```

### Решение 2. Оставить JDK 27 и включить экспериментальный режим

В `pom.xml` уже добавлено:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <systemPropertyVariables>
      <net.bytebuddy.experimental>true</net.bytebuddy.experimental>
    </systemPropertyVariables>
  </configuration>
</plugin>
```

Что это делает: заставляет Byte Buddy **не проверять** номер версии Java и
работать с новыми class-файлами «на свой страх и риск». После этого тесты
проходят и на JDK 27.

**Цена:** агент JaCoCo начнёт печатать предупреждения:

```
[WARNING] Unknown class file major version 71
```

Это значит, что отчёт о покрытии для классов, скомпилированных под JDK 27,
может быть неполным. **Поэтому решение 1 предпочтительнее.**

### Решение 3. Обновить Mockito

В `pom.xml` можно явно указать более новую версию:

```xml
<dependency>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-core</artifactId>
  <version>5.14.2</version>
  <scope>test</scope>
</dependency>
```

Новые версии Byte Buddy поддерживают более свежие JDK. Но это требует
проверки, что всё остальное (Spring Boot 3.3.5) совместимо.

### Решение 4. Обновить весь Spring Boot

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.4.x</version>   <!-- новее -->
</parent>
```

Это самый «правильный» путь для продакшена, но требует повторной проверки
всех тестов.

---

## 12.3. Проблемы тестов

### 12.3.1. Тест падает, хотя код выглядит правильным

**Симптомы:**

```
Expecting actual: 4
to be equal to: 7
```

```
expected: 409
 but was: 400
```

**Что делать (по порядку):**

1. **Перечитайте сообщение целиком.** AssertJ показывает оба значения —
   «что получилось» и «что ожидалось».
2. **Проверьте мок.** Часто причина в том, что вы настроили `when(...)` не
   для того метода или не для того аргумента:
   ```java
   when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
   ```
   А в коде вызывается `products.findById(2L)` — мок вернёт `Optional.empty()`,
   и вы получите 404 вместо ожидаемого 200.
3. **Проверьте порядок вызовов.** Если мок настроен на «первый вызов — X,
   второй — Y», а код зовёт их в другом порядке, значения перепутаются.
4. **Запустите один тест** с подробным выводом:
   ```powershell
   mvn test "-Dtest=CartServiceTest#добавлениеТовараКладётЕгоВКорзину"
   ```

### 12.3.2. `UnnecessaryStubbingException`

```
org.mockito.exceptions.misusing.UnnecessaryStubbingException:
  Unnecessary stubbing: products.findById(1L)
```

**Причина.** Вы настроили мок, но код его **не вызвал**.

**Что делать:**

* Если тест всё равно проверяет нужное поведение — просто уберите лишнюю
  настройку (Mockito в новых версиях подсказывает, какую строку убрать).
* Если тест **должен был** вызвать этот метод, а не вызвал — значит, вы
  тестируете не тот код. Перечитайте тест.

### 12.3.3. `NullPointerException` в тесте

```
java.lang.NullPointerException: Cannot invoke "shop.model.User.getId()"
because "user" is null
```

**Причина.** Вы не подготовили данные. Например, в `AuthServiceTest` нужно
настроить `users.findByEmailIgnoreCase(...)`, иначе `login` получит `null` и
выбросит 401.

**Что делать.** Добавьте настройку мока:

```java
when(users.findByEmailIgnoreCase("ivan@mail.local")).thenReturn(Optional.of(user));
```

### 12.3.4. Тест падает только при запуске «всех сразу», а по одному проходит

**Причина.** Тесты зависят друг от друга (общее состояние) или меняется
порядок выполнения.

**Что делать:**

* Перенесите всё создание объектов в `@BeforeEach`.
* Проверьте, нет ли статических полей, которые меняются между тестами.
* Проверьте, не остаётся ли «грязных» данных в H2 — используйте
  `@Transactional` на тестовом классе (Spring откатит изменения после
  каждого теста) или `@DirtiesContext`.

### 12.3.5. Тесты контроллеров падают с 500 вместо ожидаемого кода

**Симптом:**

```
Status expected = <400> but was = <500>
```

**Причина.** В `@WebMvcTest` сервис — это `@MockBean`. Если вы **не
настроили** его поведение, Mockito по умолчанию возвращает `null`, а
код контроллера пытается с этим `null` что-то сделать → 500.

**Что делать.** Настройте мок так, чтобы он **бросал** нужное исключение:

```java
doThrow(ApiException.badRequest("Цена не может быть отрицательной"))
        .when(catalog).saveProduct(any(), any());
```

Или возвращал ожидаемое значение:

```java
when(catalog.visibleProduct(1L)).thenReturn(TestFixtures.product(...));
```

### 12.3.6. `MockHttpServletResponse: No primary or single unique constructor` — ошибка в JSON

```
Cannot deserialize value of type ... : no single-constructor
```

**Причина.** Jackson не может превратить JSON в ваш объект.

**Что делать.** Проверьте, что в тесте вы **не** подсовываете мок там, где
нужен настоящий объект. Например, `when(auth.current(any())).thenReturn(user())`
— где `user()` возвращает **настоящий** `User` из `TestFixtures`, а не мок.

---

## 12.4. Ошибки компиляции Java

Эти ошибки возникают чаще всего. Разберём типичные.

### 12.4.1. `findAll(any())` — «неоднозначный вызов метода»

```
reference to findAll is ambiguous
  both findAll(Sort) in JpaRepository and findAll(Example) in JpaRepository match
```

**Причина.** У `JpaRepository` **несколько** перегрузок `findAll`:
`findAll()`, `findAll(Sort)`, `findAll(Pageable)`, `findAll(Example)`.
Аргумент `any()` подходит сразу к нескольким — компилятор не может выбрать.

**Решения:**

```java
// 1. Указать тип явно:
verify(products).findAll(any(Sort.class));

// 2. Использовать findAll() без аргументов:
verify(products).findAll();

// 3. Для конкретного метода репозитория — указать тип аргумента:
when(products.findAllByOrderByIdDesc()).thenReturn(List.of());
```

**Общее правило:** если компилятор говорит «неоднозначно» — укажите тип
аргумента явно (`any(Sort.class)`, `eq(1L)` вместо `any()`).

### 12.4.2. `extracting(p -> p.id)` — «неоднозначно» или «не найдено»

```
incompatible types: Object cannot be converted to ...
```

**Причина.** `extracting` в AssertJ принимает функцию, но лямбда без типа
неоднозначна — компилятор не понимает, что такое `p`.

**Решения:**

```java
// 1. Явный тип параметра:
assertThat(products).extracting((Product p) -> p.id).contains(1L);

// 2. Ссылка на метод (лучший вариант — работает всегда):
assertThat(products).extracting(Product::getId).contains(1L);

// 3. Для полей public — тоже работает:
assertThat(products).extracting(Product::id).contains(1L);
```

> **Совет.** Всегда указывайте тип в лямбде: `(Product p) -> p.price`.
> Это убирает 90 % ошибок компиляции с лямбдами.

### 12.4.3. `order.total` против `order.total()`

```
cannot find symbol: method total()
```

или наоборот

```
error: non-static variable total cannot be referenced from a static context
```

**Причина.** Путаница **поля** и **метода-получателя**.

| Где | Как обращаться |
|-----|----------------|
| `Order` — обычный класс, поле `public BigDecimal total` | `order.total` (без скобок) |
| `record` (например `CartService.Item`) | `item.total()` (со скобками) |
| `record` `OrderService.OrderData` | `data.email()` (со скобками) |

**Правило:** у `record` всегда скобки, у обычного класса — по объявлению в
коде.

### 12.4.4. `incompatible types: Object cannot be converted to String`

```
incompatible types: Object cannot be converted to String
```

**Причина.** `session.getAttribute("cart")` возвращает `Object` — нужно
привести к типу:

```java
@SuppressWarnings("unchecked")
Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
```

### 12.4.5. `cannot find symbol: method ...`

```
cannot find symbol: method saveProduct(...)

location: variable catalog of type CatalogService
```

**Что делать.** Проверьте:

1. Правильно ли вы **написали** имя метода (регистр важен: `saveProduct`, а не
   `saveproduct`).
2. **Импортирован** ли нужный класс (`import shop.service.CatalogService;`).
3. Не изменилась ли **сигнатура** метода в сервисе, а тест calling старую
   версию. После изменения сигнатуры компилятор покажет ошибку в тесте —
   это хорошо: тест защищает вас от рассинхронизации.

### 12.4.6. `cannot infer type arguments`

```
cannot infer type arguments for method <T> Optional<T> of(...)
```

**Причина.** Тип не выводится. Укажите его явно:

```java
Optional<Product> p = Optional.<Product>of(product);
```

Чаще всего это означает, что вы объявили переменную без типа:
`var` вместо `Product`.

---

## 12.5. Проблемы запуска приложения

### 12.5.1. `Port 8080 was already in use`

```
***************************
APPLICATION FAILED TO START
***************************
Description:
Web server failed to start. Port 8080 was already in use.
```

**Причина.** Порт 8080 уже занят. Чаще всего — **вашим же предыдущим
запуском** (забыли остановить).

**Что делать (Windows):**

```powershell
# Найти, кто слушает 8080
netstat -ano | findstr :8080

# Из последнего столбца — PID процесса
taskkill /PID 12345 /F
```

Или посмотреть в IntelliJ: **Run → Run Configurations → Edit → Show all
Spring Boot configurations** — там можно увидеть, что уже запущено.

**Альтернатива — сменить порт:**

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

### 12.5.2. `Connection refused` / `ECONNREFUSED`

```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused.
```

**Причина.** PostgreSQL не запущен, или настройки подключения неверны.

**Что делать:**

1. Проверьте, что служба PostgreSQL работает: `services.msc` → ищите
   `postgresql-x64-*` → должна быть в состоянии «Выполняется».
2. Проверьте порт: по умолчанию 5432. Если вы ставили другой — поменяйте
   `DB_URL`.
3. Проверьте имя базы: должно быть `flowerstore`.

### 12.5.3. `password authentication failed for user "postgres"`

```
FATAL: password authentication failed for user "postgres"
```

**Что делать.** Пароль в `application.properties` по умолчанию `123`, а ваш —
другой. Задайте переменную окружения:

```powershell
$env:DB_PASSWORD="ваш_реальный_пароль"
mvn spring-boot:run
```

### 12.5.4. `database "flowerstore" does not exist`

```
FATAL: database "flowerstore" does not exist
```

**Что делать.** Создайте базу:

```powershell
createdb -U postgres flowerstore
```

И примените скрипты (глава 11.2).

### 12.5.5. `relation "products" does not exist`

```
ERROR: relation "products" does not exist
```

**Причина.** База есть, но пустая — Hibernate не создал таблицы.

**Что делать:**

* Проверьте, что `spring.jpa.hibernate.ddl-auto=update` (не `none`).
* Или примените `schema.sql` вручную.
* Проверьте, что подключаетесь к **той самой** базе (частая ошибка — база
  создана, но `DB_URL` указывает на другую).

### 12.5.6. `UnknownHostException` / `Connection to db:5432 refused`

**Причина.** Вы задали `DB_URL` как `jdbc:postgresql://db:5432/flowerstore` —
это адрес Docker-контейнера, которого у вас нет. Должно быть `localhost`.

### 12.5.7. Приложение запустилось, но страница пустая

**Что делать.**

1. Откройте консоль браузера (F12 → Console). Ищите ошибки JavaScript.
2. Проверьте, что в базе есть товары:
   ```powershell
   psql -U postgres -d flowerstore -c "SELECT count(*) FROM products;"
   ```
   Если `0` — выполните `seed.sql`.
3. Проверьте вкладку **Network** в DevTools: запросы `/api/products` должны
   возвращать 200.

### 12.5.8. Товары в корзине пропали после перезапуска

**Это не ошибка!** Корзина хранится в HTTP-сессии, а сессии в проекте живут
в памяти сервера. Перезапустили приложение — сессии исчезли.

В «настоящем» магазине для этого ставят Redis или Spring Session
(глава 5.4).

### 12.5.9. После входа страница ведёт себя странно

**Что делать.** Очистите куки браузера для `localhost` (у значка замка →
«Файлы cookie и данные сайта» → удалить), затем войдите заново.

---

## 12.6. Проблемы с `psql` и кириллицей

### 12.6.1. В `psql` вместо русских букв — «кракозябры»

```
РРѕРѕР°Р·С‹Р№
```

**Причина.** Кодировка терминала и клиента `psql` не совпадают.

**Что делать.** Запускайте `psql` с явной кодировкой UTF-8:

```powershell
psql -U postgres -d flowerstore -E UTF8 --client-encoding=UTF8
```

В Windows-терминале дополнительно:

```powershell
chcp 65001
```

### 12.6.2. Ошибка при вставке кириллицы

```
ERROR: invalid byte sequence for encoding "UTF8": 0xd0
```

**Причина.** Файл в другой кодировке (например, Windows-1251).

**Что делать.** Пересохраните файл в UTF-8 и выполните:

```powershell
psql -U postgres -d flowerstore -E UTF8 -f src/main/resources/db/seed.sql
```

### 12.6.3. Windows PowerShell и кириллица в командах

Если вы вставляете SQL прямо в PowerShell, а не из файла, кириллица может
исказиться. Надёжнее — **всегда использовать файлы** (`-f schema.sql`),
а не `-c "INSERT ..."`.

### 12.6.4. `psql` не найден

```
psql : термин "psql" не распознан
```

**Что делать.** Добавьте в `PATH` папку `C:\Program Files\PostgreSQL\<версия>\bin`
или вызывайте по полному пути:

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -d flowerstore
```

---

## 12.7. Проблемы в IntelliJ IDEA

### 12.7.1. IntelliJ использует не тот JDK

**Симптом.** В терминале `java -version` показывает 17, а тесты падают с
ошибкой Byte Buddy про Java 27.

**Причина.** IntelliJ имеет **собственные** настройки JDK, не зависящие от
`JAVA_HOME`.

**Что делать.** Проверьте **три** места:

1. *File → Project Structure → Project → SDK*
2. *Settings → Build Tools → Maven → Runner → JRE*
3. *Settings → Build Tools → Maven → Importing → JDK for importer*

Во всех трёх должен быть JDK 17–23.

### 12.7.2. Изменения в `pom.xml` не подхватываются

**Что делать.** **Maven → Reload All Maven Projects** (или кнопка ⟳ на панели
Maven). IntelliJ иногда не замечает изменения в `pom.xml` автоматически.

### 12.7.3. Зависимости подчёркнуты красным в редакторе

**Причина.** IntelliJ ещё не скачала зависимости (нет интернета, или она
не знает о вашем локальном репозитории).

**Что делать.** Maven → Reload All Maven Projects. Если не помогло —
*Settings → Build Tools → Maven → Maven home path* → выберите ваш Maven.

### 12.7.4. В консоли `mvn` в IntelliJ используется другая Java

**Что делать.** *Settings → Build Tools → Maven → Runner → JRE* → **Project
SDK** (то есть тот, что выбрали в Project Structure).

### 12.7.5. IntelliJ не показывает зелёную стрелку у `main`

**Причина.** Файл не распознан как «класс с точкой входа», либо модуль не
настроен.

**Что делать.** *File → Project Structure → Modules* → проверьте, что
`src/main/java` помечен как **Sources**, а `src/test/java` — как **Test
Sources**.

---

## 12.8. Проблемы с JaCoCo и отчётом о покрытии

### 12.8.1. `Unknown class file major version 71`

```
[WARNING] Unknown class file major version 71 (Java 27)
```

**Причина.** Вы на JDK 27, а JaCoCo 0.8.12 не знает такой версии.

**Что делать.** Использовать JDK 17–23 (глава 12.2). Либо обновить JaCoCo
до версии, поддерживающей новые JDK, и поднять версию Spring Boot.

### 12.8.2. `IllegalClassFormatException` / `Error while instrumenting class`

**Причина.** JaCoCo пытается инструментировать класс, сгенерированный
Mockito в рантайме.

**Решение.** В `pom.xml` уже есть исключение:

```xml
<excludes>
  <exclude>*$MockitoMock*</exclude>
</excludes>
```

Если ошибка вернулась — проверьте, что блок `<excludes>` стоит именно внутри
`<configuration>` элемента `prepare-agent`.

### 12.8.3. Отчёт не создаётся

**Что делать.** Отчёт создаётся в фазе `verify`, а не `test`. Используйте:

```powershell
mvn clean verify
```

а не `mvn test`.

### 12.8.4. В отчёте покрытие 0 %

**Что делать.** Проверьте, что `jacoco-maven-plugin` подключён в `pom.xml` и
запускался в том же прогоне. Также проверьте, что вы смотрите **свежий**
отчёт (после `clean` старая папка `target` удаляется).

---

## 12.9. Проблемы с логикой приложения

### 12.9.1. Товар не добавляется в корзину

**Проверьте по порядку:**

1. Открыта ли консоль браузера (F12)? Есть ли ошибка?
2. Что отвечает сервер? Вкладка **Network** → запрос `POST /cart/add` →
   код ответа:
   * **200/303** — сервер обработал, проблема во фронтенде;
   * **400** — «Некорректное количество»;
   * **404** — «Товар не найден» (товар скрыт или удалён);
   * **409** — «Недостаточно на складе»;
   * **500** — смотрите лог сервера в консоли.

### 12.9.2. В админке пустые списки

**Проверьте:**

1. Вошли ли вы как администратор (`admin@flower.local` / `admin123`);
2. Есть ли товары в базе (`SELECT count(*) FROM products;`);
3. Не скрыты ли все товары (`visible = false`).

### 12.9.3. Кнопки смены статуса не появляются

**Причина.** Кнопки рисуются по данным сервера — `o.nextStatuses`. Если
заказ в статусе «доставлен» или «отменён», переходов нет — это **правильное**
поведение (глава 6.8).

### 12.9.4. Ошибка в адресе формы (как в `pages.js`)

В `pages.js` в функции админских заказов есть опечатка:

```javascript
<form method="post" action="/api/admin/orders/${o.id}/status">
```

а сервер ждёт `/admin/orders/{id}/status` (без `/api`). Нажатие кнопки
приведёт к 404.

**Как найти такие ошибки:**

1. Откройте DevTools → **Network** → нажмите кнопку → посмотрите, какой
   запрос ушёл и какой код вернулся;
2. Сопоставьте адрес с таблицей маршрутов (глава 8.7);
3. Напишите тест в `AdminControllerTest` — он поймает опечатку в адресе
   до того, как её увидит преподаватель.

---

## 12.10. Проблемы с кодировкой текста

### 12.10.1. Вместо русских букв — «кракозябры» на странице

**Причина.** В HTML-файле нет `<meta charset="UTF-8">`.

**Решение:**

```html
<meta charset="UTF-8">
```

Все страницы проекта эту строку содержат.

### 12.10.2. В JSON вместо букв — «кракозябры»

**Причина.** Запрос отправлен без указания кодировки, либо настройки Tomcat.

**Решение.** Добавьте в `application.properties`:

```properties
server.servlet.encoding.charset=UTF-8
server.servlet.encoding.force=true
```

### 12.10.3. В консоли Maven вместо русских букв — «кракозябры»

```powershell
chcp 65001
$env:JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
```

---

## 12.11. Общий алгоритм диагностики

Когда что-то сломалось, идите по этому списку сверху вниз:

```
1. Что именно сломалось?
   └─ Сборка? Тесты? Запуск? Страница? База?

2. Прочитать сообщение об ошибке ЦЕЛИКОМ
   └─ Обычно ответ — в последней строке

3. Воспроизвести минимально
   └─ Один тест: mvn test "-Dtest=Класс#метод"
   └─ Один запрос в браузере: DevTools → Network

4. Проверить окружение
   └─ mvn -version        (какая Java у Maven?)
   └─ java -version       (какая Java в системе?)
   └─ Project Structure  (какая Java у IntelliJ?)

5. Проверить данные
   └─ SELECT count(*) FROM products;
   └─ Правильный ли пароль и база?

6. Проверить логи сервера
   └─ В окно, где запущено приложение
   └─ Там будет stack trace — он точно указывает на строку

7. Спросить у коллеги / преподавателя
   └─ Приложите: команду, полное сообщение об ошибке,
      вывод mvn -version и версию IntelliJ
```

> **Золотое правило отладки:** «не работает» — это не диагноз. Диагноз —
> это конкретное сообщение об ошибке. Ваша задача — дойти до него.

---

## 12.12. Главное из главы

1. `illegal character: '\ufeff'` — в файле BOM. Пересохраните в UTF-8 без BOM.
2. `Java 27 (71) is not supported by Byte Buddy` — не ошибка кода, а
   слишком новый JDK. Переключитесь на 17–23 или включите
   `net.bytebuddy.experimental`.
3. `Port 8080 was already in use` — найдите и убейте процесс
   (`netstat -ano | findstr :8080`).
4. `findAll(any())` неоднозначно — уточните тип: `any(Sort.class)`.
5. `extracting(p -> p.id)` — укажите тип: `(Product p) -> p.id` или
   `Product::getId`.
6. `order.total` vs `order.total()` — у `record` всегда скобки.
7. `UnnecessaryStubbingException` — вы настроили мок, но код его не вызвал.
8. Ошибки 500 в `@WebMvcTest` — не настроено поведение `@MockBean`.
9. Кириллица в `psql` — `psql -E UTF8 --client-encoding=UTF8`.
10. Диагностика идёт сверху вниз: сообщение → минимальное воспроизведение →
    окружение → данные → логи.

**Вопросы для самопроверки**

1. Почему ошибка Byte Buddy — это проблема окружения, а не кода? Как это
   проверить?
2. Что такое BOM, откуда он берётся и почему `javac` его не принимает?
3. Почему в тестах `@WebMvcTest` при не настроенном моке получается 500, а не
   404?
4. Вы написали `assertThat(order.total).isEqualTo("30.00")`, а сумма
   получилась `30.0`. Почему `isEqualTo` не подошёл и что надо было
   написать?
5. Составьте план диагностики для ситуации: «страница открывается, но товары
   не показываются».

---

*Дальше: [Глава 13. Итоги и задания](13-summary.md) — что вы теперь умеете
и что делать дальше.*
