# Глава 9. Фронтенд

> **Что мы уже знаем:** сервер умеет отдавать JSON (`/api/...`) и обрабатывать
> формы (POST-адреса) (глава 8). Теперь разберёмся, как из этого собрана
> рабочая страница магазина — без единого фреймворка и сборщика.

Все файлы фронтенда лежат в `src/main/resources/static/`.

```
static/
├── index.html        главная
├── catalog.html      каталог
├── product.html      карточка товара
├── cart.html         корзина
├── checkout.html     оформление заказа
├── order-success.html подтверждение
├── login.html        вход
├── register.html     регистрация
├── account.html      личный кабинет
├── contacts.html     обратная связь
├── 403.html          доступ запрещён
├── 404.html          не найдено
├── admin/
│   ├── products.html       список товаров
│   ├── product-form.html   форма товара
│   ├── categories.html     категории
│   ├── orders.html         заказы
│   └── users.html          пользователи
├── css/style.css
└── js/
    ├── common.js     мелкие помощники
    ├── layout.js     шапка, подвал, сообщения
    └── pages.js      логика каждой страницы
```

---

## 9.1. Почему без фреймворков

Frontendl modernный обычно пишут на React, Vue или Angular. Но:

* это требует Node.js, сборщика и шага «собрать» перед запуском;
* учебный проект — про **сервер** и **тестирование**, а не про фронтенд;
* фреймворк прятал бы от читателя базовые вещи: как браузер общается с
  сервером, что такое HTTP-запрос, как устроены формы.

Поэтому здесь — **чистый HTML + CSS + JavaScript**. Общий объём — около
17 килобайт JavaScript. Ничего лишнего.

---

## 9.2. Как статические файлы попадают в Spring Boot

Spring Boot отдаёт файлы из `src/main/resources/static/` «как есть» по адресу
`/путь/к/файлу`:

```
static/index.html   →  http://localhost:8080/index.html
static/css/style.css → http://localhost:8080/css/style.css
static/js/pages.js   → http://localhost:8080/js/pages.js
```

Никаких контроллеров для этого не нужно — это встроенное поведение.

### «Почему не `/`?»

Главная страница лежит по адресу `/index.html`. Это слегка непривычно (обычно
главная — по адресу `/`), но зато все адреса явные и одинаковые:
`/catalog.html`, `/cart.html`, `/admin/orders.html`. Так удобнее отлаживать.

---

## 9.3. Устройство страницы

```html
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Каталог — Flower Store</title>
<link rel="stylesheet" href="/css/style.css">
</head>
<body data-page="catalog" data-load="catalog" >
<div id="site-header"></div>
<main class="container">
  <h1>Каталог</h1>
  <form class="toolbar" method="get" action="/catalog.html">
    <div>
      <label for="cat">Категория</label>
      <select id="cat" name="category"><option value="">Все</option></select>
    </div>
    <div>
      <label for="sort">Сортировка</label>
      <select id="sort" name="sort">
        <option value="">По умолчанию</option>
        <option value="price">Сначала дешёвые</option>
        <option value="price_desc">Сначала дорогие</option>
      </select>
    </div>
    <button class="btn">Показать</button>
  </form>
  <div class="grid" id="list"></div>
</main>
<div id="site-footer"></div>
<script src="/js/common.js"></script>
<script src="/js/layout.js"></script>
<script src="/js/pages.js"></script>
</body>
</html>
```

### Два «магических» атрибута на `<body>`

```html
<body data-page="catalog" data-load="catalog" >
```

* `data-page` — «какая это страница». По нему `layout.js` решает, какую
  вкладку подсветить в меню.
* `data-load` — «какую функцию запустить». По нему `pages.js` выбирает
  обработчик страницы.

Это мини-«роутер» на чистом JavaScript: страница сама сообщает, что её
загружать.

Атрибут `data-*` — стандартный способ HTML хранить **свои** данные в
элементе. Их можно прочитать из JavaScript:

```javascript
document.body.dataset.load   // → "catalog"
```

### Почему `<div id="site-header"></div>` пустой

Шапка (меню, кнопка «Войти», счётчик корзины) **одинакова на всех
страницах**. Чтобы не копировать её в 16 HTML-файлов, `layout.js` подставляет
её на место пустого `<div>`:

```javascript
$('#site-header').outerHTML = `<header class="top"><div class="bar">...</div></header>`;
```

`outerHTML` заменяет сам элемент целиком. Это «одно место правды» для шапки:
чтобы поменять меню, достаточно отредактировать один файл.

### Порядок подключения скриптов

```html
<script src="/js/common.js"></script>
<script src="/js/layout.js"></script>
<script src="/js/pages.js"></script>
```

Порядок важен:

1. `common.js` — объявляет вспомогательные функции (`$`, `esc`, `money`,
   `get`). Другие файлы их используют, значит, он должен загрузиться первым.
2. `layout.js` — запускает `window.appReady` (промис), который рисует шапку.
3. `pages.js` — в конце ждёт `window.appReady`, а потом рисует содержимое
   страницы.

---

## 9.4. `common.js` — четыре помощника

```javascript
const $ = (selector) => document.querySelector(selector);

const esc = (text) => String(text ?? '').replace(/[&<>"']/g,
  (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const money = (n) => Number(n).toLocaleString('ru-RU',
  { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' Br';

async function get(url) {
  const response = await fetch(url);
  if (response.status === 204) return null;
  if (!response.ok) throw new Error('Ошибка ' + response.status);
  return response.json();
}
```

### `$` — «найти элемент»

`document.querySelector('.card')` — длинно. Короткая запись `$` используется
везде: `$('#list')`, `$('#total')`.

Обратите внимание на `#` — это «id». `$('#list')` найдёт элемент с
`id="list"`.

### `esc` — защита от «подстановки чужого HTML»

Это **важнейшая** функция безопасности на фронтенде. Смотрите:

```javascript
$('#list').innerHTML = products.map(p => `<div class="card"><h3>${esc(p.name)}</h3></div>`).join('');
```

Если `p.name` = `<script>alert('взлом')</script>`, то без `esc` этот код
**выполнился бы** в браузере покупателя. С `esc` он превратится в текст:
`&lt;script&gt;...`.

Функция заменяет «опасные» символы на их HTML-эквиваленты:

| Символ | Замена | Смысл |
|--------|--------|-------|
| `&` | `&amp;` | «и» |
| `<` | `&lt;` | начало тега |
| `>` | `&gt;` | конец тега |
| `"` | `&quot;` | кавычка |
| `'` | `&#39;` | апостроф |

**Правило проекта:** всё, что пришло с сервера, вставляется в страницу
**только через `esc`**. Единственное исключение — числа и `null`
(`text ?? ''` превращает `null` в пустую строку).

### `money` — деньги по-русски

```javascript
const money = (n) => Number(n).toLocaleString('ru-RU',
  { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' Br';
```

`4500` → `4 500,00 Br`. `toLocaleString` с локалью `ru-RU` ставит пробелы
между разрядами и запятую перед копейками.

### `get` — спросить сервер

```javascript
async function get(url) {
  const response = await fetch(url);
  if (response.status === 204) return null;
  if (!response.ok) throw new Error('Ошибка ' + response.status);
  return response.json();
}
```

* `async` / `await` — «асинхронная функция»: `await fetch(...)` означает
  «подожди, пока сервер ответит, но не блокируй браузер». Пока ждём — страница
  не «замерзает».
* `response.ok` — «статус в диапазоне 200–299».
* `response.json()` — разобрать ответ как JSON.

---

## 9.5. `layout.js` — общая шапка

```javascript
window.appReady = (async () => {
  const params = new URLSearchParams(location.search);
  if (params.get('error')) toast(params.get('error'), true);
  else if (params.get('msg')) toast(params.get('msg'));
  params.delete('msg');
  params.delete('error');
  history.replaceState(null, '', location.pathname + (params.toString() ? '?' + params : ''));
  ...
})();
```

### Шаг 1. Показать сообщение

Сервер после действия перенаправляет на `/cart.html?msg=Товар добавлен в
корзину` или `/cart.html?error=Недостаточно на складе...`.
`layout.js` читает эти параметры и показывает всплывашку:

```javascript
function toast(text, isError) {
  const box = document.createElement('div');
  box.className = 'toast' + (isError ? ' bad' : '');
  box.textContent = text;
  document.body.append(box);
  setTimeout(() => box.remove(), 3500);
}
```

Обратите внимание: `box.textContent = text` — **без `innerHTML`**. Сообщение
вставляется как текст, поэтому даже если бы в него попалось что-то
«опасное», оно бы не выполнилось.

### Шаг 2. Убрать параметры из адреса

```javascript
params.delete('msg');
params.delete('error');
history.replaceState(null, '', location.pathname + (params.toString() ? '?' + params : ''));
```

Если этого не сделать, при обновлении страницы (F5) сообщение появится
снова. `history.replaceState` меняет адрес **без перезагрузки** страницы.

### Шаг 3. Кто я и сколько в корзине

```javascript
let me = null;
let cartCount = 0;
try {
  me = await get('/api/auth/me');
  if (!isAdminPage) {
    const cart = await get('/api/cart');
    cartCount = cart.items.reduce((sum, item) => sum + item.qty, 0);
  }
} catch (e) { /* сервер недоступен — покажем страницу без данных */ }
```

* `me` — «кто вошёл» (или `null` для гостя).
* `cartCount` — сумма количеств всех позиций (`reduce` — свёртка массива).
* `try/catch` — если сервер недоступен, страница всё равно покажется (только
  без данных). Это «graceful degradation» — аккуратное деградирование.

### Шаг 4. Защита админских страниц

```javascript
if (isAdminPage && (!me || me.role !== 'ADMIN')) {
  location.href = '/403.html';
  return false;   // уходим на 403 — страницу рисовать не надо
}
```

Клиентская проверка прав — **не защита** (глава 8.2), но она:
* экономит ненужный запрос к `/api/admin/...`;
* сразу показывает пользователю понятную страницу.

Настоящая защита — на сервере, в `auth.admin(session)`.

### Шаг 5. Нарисовать шапку

```javascript
const menu = isAdminPage
  ? '<nav><span class="muted">Панель администратора</span></nav>'
  : `<nav>
       <a href="/catalog.html"${on('catalog')}>Каталог</a>
       <a href="/cart.html"${on('cart')}>Корзина<span class="cnt">${cartCount}</span></a>
       <a href="/account.html"${on('account')}>Личный кабинет</a>
       <a href="/contacts.html"${on('contacts')}>Контакты</a>
     </nav>`;

const user = me
  ? `<span>${esc(me.name)}</span>
     ${me.role === 'ADMIN' ? '<a href="/admin/products.html">Админ-панель</a>' : ''}
     <form method="post" action="/logout"><button class="btn line sm">Выйти</button></form>`
  : '<a href="/login.html">Войти</a><a class="btn sm" href="/register.html">Регистрация</a>';

$('#site-header').outerHTML = `<header class="top"><div class="bar">
  <a class="logo" href="/index.html">🌸 Flower Store</a>${menu}<div class="user">${user}</div></div></header>`;
```

Заметьте: `esc(me.name)` — имя пользователя вставляется через `esc`.
А `cartCount` — число, его экранировать не нужно.

Форма выхода — обычная HTML-форма с `method="post"`:

```html
<form method="post" action="/logout"><button class="btn line sm">Выйти</button></form>
```

Никакого JavaScript: браузер сам отправит POST на `/logout`, а сервер
перенаправит на главную.

---

## 9.6. `pages.js` — логика страниц

### Карточка товара — общий кусочек

```javascript
const card = (p) => `
  <div class="card">
    <a class="pic" href="/product.html?id=${p.id}">${picture(p)}</a>
    <div class="in">
      <span class="muted">${esc(p.categoryName)}</span>
      <h3><a href="/product.html?id=${p.id}">${esc(p.name)}</a></h3>
      <span class="price">${money(p.price)}</span>
      ${p.stock > 0
        ? `<form method="post" action="/cart/add">${hidden('productId', p.id)}<button class="btn">В корзину</button></form>`
        : '<button class="btn" disabled>Нет в наличии</button>'}
    </div>
  </div>`;
```

Ключевые решения:

* **Картинка или эмодзи.** `picture(p)` покажет `<img>`, если есть `imageUrl`,
  иначе — цветочек 🌸. Товары всегда выглядят прилично.
* **Кнопка «В корзину» — это форма**, а не JS-код. Форма работает даже если
  JavaScript ещё не загрузился.
* **Нет в наличии → кнопка `disabled`.** Пользователь не может даже
  попробовать добавить. (И сервер всё равно проверит — «доверяй, но проверяй».)

### Фильтр каталога

```javascript
async catalog() {
  const params = new URLSearchParams(location.search);
  const categories = await get('/api/categories');
  $('#cat').innerHTML = categories.map((c) => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
  $('#cat').value = params.get('category') || '';
  $('#sort').value = params.get('sort') || '';

  const query = new URLSearchParams({ category: params.get('category') || '', sort: params.get('sort') || '' });
  const products = await get('/api/products?' + query);
  $('#list').innerHTML = products.length ? products.map(card).join('') : emptyBlock('🥀', 'В этой категории пока нет товаров.');
}
```

Логика:

1. Читаем параметры из адреса (кто выбрал категорию и сортировку).
2. Загружаем список категорий и подставляем в `<select>`.
3. **Восстанавливаем** выбранные значения (`$('#cat').value = ...`) — чтобы
   форма выглядела так, как её оставили.
4. Запрашиваем товары с фильтром.
5. Рисуем карточки или «пустое» сообщение.

Фильтр работает через `<form method="get" action="/catalog.html">` — то есть
**без JavaScript**: нажали «Показать» → страница перезагрузилась с новыми
параметрами → скрипт их прочитал.

### Корзина

```javascript
async cart() {
  const cart = await get('/api/cart');
  if (!cart.items.length) {
    $('#app').innerHTML = '<h1>Корзина</h1>' + emptyBlock('🛒', 'Корзина пуста. Добавьте цветы из каталога.',
      '<a class="btn" href="/catalog.html">Перейти в каталог</a>');
    return;
  }
  const rows = cart.items.map((i) => `
    <tr>
      <td><span class="thumb">${picture(i)}</span>${esc(i.name)}</td>
      <td>${money(i.price)}</td>
      <td>
        <form class="row" method="post" action="/cart/update">
          ${hidden('productId', i.productId)}
          <input class="qty" type="number" name="qty" min="1" max="${i.stock}" value="${i.qty}">
          <button class="btn line sm">Обновить</button>
        </form>
      </td>
      <td>${money(i.sum)}</td>
      <td><form method="post" action="/cart/remove">${hidden('productId', i.productId)}<button class="btn line sm">Удалить</button></form></td>
    </tr>`).join('');
  ...
}
```

Обратите внимание на `max="${i.stock}"` — максимум в поле количества равен
остатку на складе. Это **подсказка** пользователю; настоящую проверку делает
сервер.

Форма «Обновить» отправляет `productId` и `qty` на `/cart/update` — это
ровно тот метод, который мы разобрали в `ShopController`.

### Оформление заказа

```javascript
async checkout() {
  const [cart, me] = await Promise.all([get('/api/cart'), get('/api/auth/me')]);
  if (!cart.items.length) {
    $('#checkout').hidden = true;
    $('#empty').hidden = false;
    return;
  }
  if (me) {   // вошедшему подставляем его данные
    $('#n').value = me.name || '';
    $('#e').value = me.email || '';
    $('#t').value = me.phone || '';
    $('#a').value = me.address || '';
  }
  ...
}
```

`Promise.all([...])` — «выполнить оба запроса **одновременно** и дождаться
обоих». Это быстрее, чем ждать по очереди.

Поле `hidden` — стандартный HTML-атрибут: элемент с `hidden` не отображается.
Здесь переключаются две части страницы: форма или сообщение «корзина пуста».

### Личный кабинет

```javascript
async account() {
  const me = await get('/api/auth/me');
  if (!me) { $('#guest').hidden = false; return; }
  $('#cabinet').hidden = false;
  $('#email').value = me.email;
  ...
  const orders = await get('/api/me/orders');
  $('#orders').innerHTML = orders.length ? `...` : emptyBlock('📦', 'Заказов пока нет.');
}
```

Если пользователь не вошёл — показываем блок «Войдите, чтобы видеть заказы».
Иначе — кабинет и таблицу заказов.

### Админка

```javascript
async orders() {
  const orders = await get('/api/admin/orders');
  $('#tbl').innerHTML = table(
    ['№', 'Дата', 'Покупатель', 'Состав', 'Сумма', 'Статус', 'Сменить статус'],
    orders.map((o) => `
      <tr>
        <td>${o.id}</td><td>${o.createdAt}</td>
        <td>${esc(o.customerName)}<br><span class="muted">${esc(o.phone || o.email)}</span></td>
        <td>${o.items.map((i) => esc(i.name) + ' × ' + i.qty).join('<br>')}</td>
        <td>${money(o.total)}</td><td>${statusBadge(o.status)}</td>
        <td><div class="row">${(o.nextStatuses || []).map((s) => `
          <form method="post" action="/api/admin/orders/${o.id}/status">...
```

Смотрите на последнюю колонку: кнопки перехода статуса рисуются **по данным
сервера** (`o.nextStatuses`), а не по захардкоженному списку. Сервер прислал
`["processing", "cancelled"]` — значит, админ увидит ровно эти две кнопки.
Если правило переходов изменится в `OrderStatus`, интерфейс обновится
**сам**, без правок фронтенда.

Кстати, в этом кусочке есть опечатка (адрес `/api/admin/orders/...` вместо
`/admin/orders/...`) — отличная иллюстрация того, зачем нужны тесты: ошибка
в URL не видна при чтении кода, но видна при запуске.

### Роутер в конце файла

```javascript
// Какая функция запускается — написано в <body data-load="...">
(async () => {
  // Ждём шапку: если layout.js уводит на 403.html — рисовать страницу не нужно.
  if (window.appReady && !(await window.appReady)) return;

  try {
    await PAGES[document.body.dataset.load]?.();
  } catch (e) {
    console.error(e);
    const place = $('#app') || $('#tbl') || $('#list');
    if (place) place.innerHTML = emptyBlock('⚠️', 'Не удалось загрузить данные.');
  }
})();
```

* `window.appReady` — промис из `layout.js`. `await` на нём означает
  «дождись, пока шапка будет готова».
* `PAGES[document.body.dataset.load]?.()` — «возьми функцию по имени из
  `data-load` и вызови её». `?.` — «если такой функции нет, ничего не делай»
  (опциональная цепочка).
* `catch` — если данные не загрузились, показываем аккуратное сообщение
  вместо пустой страницы.

---

## 9.7. Формы: как они связаны с контроллерами

Полная карта «HTML-форма → метод контроллера»:

| Форма в HTML | Адрес | Метод контроллера |
|---------------|-------|-------------------|
| Кнопка «В корзину» | `POST /cart/add` | `ShopController.addToCart` |
| «Обновить» в корзине | `POST /cart/update` | `ShopController.updateCart` |
| «Удалить» в корзине | `POST /cart/remove` | `ShopController.removeFromCart` |
| Форма заказа | `POST /checkout` | `ShopController.checkout` |
| Форма входа | `POST /login` | `ShopController.login` |
| Форма регистрации | `POST /register` | `ShopController.register` |
| «Выйти» в шапке | `POST /logout` | `ShopController.logout` |
| Форма профиля | `POST /account/profile` | `ShopController.saveProfile` |
| «Повторить» в заказах | `POST /account/repeat/{id}` | `ShopController.repeatOrder` |
| Форма обратной связи | `POST /contacts` | `ShopController.sendMessage` |
| Форма товара | `POST /admin/products/save` | `AdminController.saveProduct` |
| «Скрыть/Показать» | `POST /admin/products/{id}/toggle` | `AdminController.toggleProduct` |
| «Удалить» товар | `POST /admin/products/{id}/delete` | `AdminController.deleteProduct` |
| Новая категория | `POST /admin/categories` | `AdminController.addCategory` |
| Переименование | `POST /admin/categories/{id}` | `AdminController.renameCategory` |
| Удаление категории | `POST /admin/categories/{id}/delete` | `AdminController.deleteCategory` |
| Смена статуса | `POST /admin/orders/{id}/status` | `AdminController.changeOrderStatus` |

**Правило проекта:** формы посылают `POST` и всегда содержат
`<input type="hidden">` с идентификатором объекта. Никакого JavaScript для
отправки данных — только HTML.

---

## 9.8. Стили

`css/style.css` — 5,4 килобайта обычного CSS: переменные, сетка, карточки,
кнопки, таблицы, адаптивность. Ничего особенного, но три вещи стоит отметить:

* **CSS-переменные** — цвета заданы один раз:
  ```css
  :root { --pink: #e8608f; --ink: #2b2b33; ... }
  ```
  и используются дальше как `var(--pink)`.
* **Адаптивность** — через `@media (max-width: 720px)`: на телефоне сетка
  становится в одну колонку.
* **Тёмная/светлая тема** — если есть, переключается через `prefers-color-scheme`.

---

## 9.9. Что здесь можно улучшить (честный разбор)

Учебный проект сознательно прост, и вот его слабые места:

1. **Нет защиты от «клика» по форме без JS** — формы работают и без
   JavaScript, но фильтр каталога требует перезагрузки страницы.
2. **Все страницы грузятся целиком** — нет ленивой загрузки изображений.
3. **Админка загружает все заказы сразу** — при тысячах заказов понадобилась
   бы пагинация (`Pageable` в Spring Data для этого и существует).
4. **Нет подтверждения опасных действий** — удаление товара происходит сразу.
   Обычно добавляют `confirm()`.
5. **Страница 404/403 статические** — при ошибке в API фронтенд показывает
   общее сообщение, а не текст с сервера.

Всё это — не ошибки, а **точки роста**. Именно так выглядит учебная версия;
в коммерческом проекте эти пункты закрываются.

---

## 9.10. Главное из главы

1. Фронтенд — статические файлы: HTML (скелет), CSS (внешний вид), JS
   (поведение).
2. Страница получает данные у сервера через `fetch` и рисует их через
   `innerHTML`.
3. **Всё, что пришло с сервера, вставляется через `esc`** — иначе возможна
   XSS-атака.
4. `layout.js` рисует общую шапку один раз для всех страниц; `data-page` и
   `data-load` — «маленький роутер».
5. Формы — обычный HTML; сервер отвечает редиректом с `?msg=` или `?error=`.
6. Кнопки смены статуса рисуются по данным сервера (`nextStatuses`) — правило
   не дублируется во фронтенде.
7. `Promise.all` — параллельные запросы; `await` — ожидание.

**Вопросы для самопроверки**

1. Что произойдёт, если убрать `esc` при выводе названия товара? Придумайте
   конкретную атаку.
2. Зачем после показа сообщения удалять `?msg=` из адреса?
3. Как работает фильтр каталога без JavaScript?
4. Почему в `layout.js` проверка `me.role !== 'ADMIN'` названа «не защитой»?
5. Зачем в поле количества стоит `max="${i.stock}"`, если сервер всё равно
   проверяет остаток?
6. В `pages.js` в функции `orders()` есть опечатка в адресе формы. Как бы вы
   нашли её без запуска приложения?

---

*Дальше: [Глава 10. Тестирование](10-testing.md) — главная глава курсовой
работы.*
