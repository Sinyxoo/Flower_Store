// Страницы только ПОКАЗЫВАЮТ данные, которые сервер отдаёт в /api/...
// Все действия (корзина, заказ, вход, админка) — обычные HTML-формы: method="post".

const STATUS = { new: 'Новый', processing: 'В обработке', delivered: 'Доставлен', cancelled: 'Отменён' };
const STATUS_COLOR = { new: 'pink', processing: 'amber', delivered: 'green', cancelled: 'grey' };

const picture = (p) => (p.imageUrl ? `<img src="${esc(p.imageUrl)}" alt="">` : '<span>🌸</span>');
const emptyBlock = (icon, text, link = '') => `<div class="empty"><div class="big">${icon}</div><p>${text}</p>${link}</div>`;
const statusBadge = (s) => `<span class="st ${STATUS_COLOR[s] || ''}">${STATUS[s] || s}</span>`;

// Таблица: сначала заголовки, потом готовые строки.
const table = (headers, rows) =>
  `<table><tr>${headers.map((h) => `<th>${h}</th>`).join('')}</tr>${rows.join('')}</table>`;

// Скрытое поле для форм
const hidden = (name, value) => `<input type="hidden" name="${name}" value="${esc(value)}">`;

// Карточка товара: кнопка «В корзину» — это форма
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

const PAGES = {
  async home() {
    const [categories, products] = await Promise.all([get('/api/categories'), get('/api/products?sort=price_desc')]);
    $('#cats').innerHTML = categories.map((c) => `<a class="chip" href="/catalog.html?category=${c.id}">${esc(c.name)}</a>`).join('');
    $('#list').innerHTML = products.slice(0, 4).map(card).join('');
  },

  async catalog() {
    const params = new URLSearchParams(location.search);
    const categories = await get('/api/categories');
    $('#cat').innerHTML = categories.map((c) => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
    $('#cat').value = params.get('category') || '';
    $('#sort').value = params.get('sort') || '';

    const query = new URLSearchParams({ category: params.get('category') || '', sort: params.get('sort') || '' });
    const products = await get('/api/products?' + query);
    $('#list').innerHTML = products.length ? products.map(card).join('') : emptyBlock('🥀', 'В этой категории пока нет товаров.');
  },

  async product() {
    const id = new URLSearchParams(location.search).get('id');
    let p = null;
    if (id) {
      try { p = await get('/api/products/' + id); } catch (e) { /* такого товара нет */ }
    }
    if (!p) {
      $('#app').innerHTML = emptyBlock('🔍', 'Такого товара нет.', '<a class="btn" href="/catalog.html">В каталог</a>');
      return;
    }
    $('#app').innerHTML = `
      <p><a href="/catalog.html">← В каталог</a></p>
      <div class="split">
        <div class="card"><div class="pic" style="height:380px;font-size:140px">${picture(p)}</div></div>
        <div>
          <span class="muted">${esc(p.categoryName)}</span>
          <h1>${esc(p.name)}</h1>
          <p class="price" style="font-size:28px">${money(p.price)}</p>
          <p>${esc(p.description) || 'Описание скоро появится.'}</p>
          <p class="muted">${p.stock > 0 ? 'В наличии: ' + p.stock + ' шт.' : 'Нет в наличии'}</p>
          ${p.stock > 0 ? `
            <form class="toolbar" method="post" action="/cart/add">
              ${hidden('productId', p.id)}
              <div><label for="qty">Количество</label>
                <input id="qty" name="qty" type="number" min="1" max="${p.stock}" value="1" class="qty"></div>
              <button class="btn">В корзину</button>
            </form>` : '<button class="btn" disabled>Нет в наличии</button>'}
        </div>
      </div>`;
  },

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
    $('#app').innerHTML = `
      <h1>Корзина</h1>
      <div class="tbox"><table>
        <tr><th>Товар</th><th>Цена</th><th>Кол-во</th><th>Сумма</th><th></th></tr>${rows}
      </table></div>
      <div class="total">Итого: ${money(cart.total)}</div>
      <div class="row" style="justify-content:flex-end">
        <a class="btn line" href="/catalog.html">Продолжить покупки</a>
        <a class="btn pink" href="/checkout.html">Оформить заказ</a>
      </div>`;
  },

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
    $('#sum').innerHTML = cart.items.map((i) =>
      `<tr><td>${esc(i.name)} × ${i.qty}</td><td class="r">${money(i.sum)}</td></tr>`).join('');
    $('#total').textContent = 'Итого: ' + money(cart.total);
  },

  success() {
    const id = new URLSearchParams(location.search).get('id');
    if (id) $('#oid').textContent = '№ ' + id;
  },

  async account() {
    const me = await get('/api/auth/me');
    if (!me) { $('#guest').hidden = false; return; }
    $('#cabinet').hidden = false;
    $('#email').value = me.email;
    $('#n').value = me.name || '';
    $('#t').value = me.phone || '';
    $('#a').value = me.address || '';

    const orders = await get('/api/me/orders');
    $('#orders').innerHTML = orders.length ? `<div class="tbox"><table>
      <tr><th>№</th><th>Дата</th><th>Сумма</th><th>Статус</th><th></th></tr>
      ${orders.map((o) => `<tr><td>${o.id}</td><td>${o.createdAt}</td><td>${money(o.total)}</td><td>${statusBadge(o.status)}</td>
        <td><form method="post" action="/account/repeat/${o.id}"><button class="btn line sm">Повторить</button></form></td></tr>`).join('')}
      </table></div>` : emptyBlock('📦', 'Заказов пока нет.');
  },

  // ---------- админка ----------
  async products() {
    const products = await get('/api/admin/products');
    $('#tbl').innerHTML = table(
      ['Товар', 'Категория', 'Цена', 'Остаток', 'На сайте', ''],
      products.map((p) => `<tr>
        <td><span class="thumb">${picture(p)}</span>${esc(p.name)}</td>
        <td>${esc(p.categoryName)}</td><td>${money(p.price)}</td><td>${p.stock}</td><td>${p.visible ? 'да' : 'скрыт'}</td>
        <td><div class="row">
          <a class="btn line sm" href="/admin/product-form.html?id=${p.id}">Изменить</a>
          <form method="post" action="/admin/products/${p.id}/toggle"><button class="btn line sm">${p.visible ? 'Скрыть' : 'Показать'}</button></form>
          <form method="post" action="/admin/products/${p.id}/delete"><button class="btn pink sm">Удалить</button></form>
        </div></td></tr>`));
  },

  // Форма товара: список категорий и (при редактировании) текущие значения
  async productForm() {
    const categories = await get('/api/categories');
    $('#categoryId').innerHTML = categories.map((c) => `<option value="${c.id}">${esc(c.name)}</option>`).join('');

    const params = new URLSearchParams(location.search);

    // Вернулись с ошибкой — значения уже введены, берём их из адреса.
    if (params.has('name')) {
      $('#title').textContent = params.get('id') ? 'Изменить товар' : 'Новый товар';
      $('#pid').value = params.get('id') || '';
      $('#name').value = params.get('name') || '';
      $('#categoryId').value = params.get('categoryId') || '';
      $('#price').value = params.get('price') || '';
      $('#stock').value = params.get('stock') || '';
      $('#description').value = params.get('description') || '';
      $('#imageUrl').value = params.get('imageUrl') || '';
      $('#visible').checked = params.get('visible') === 'true';
      return;
    }

    const id = params.get('id');
    if (!id) return;
    const p = (await get('/api/admin/products')).find((x) => x.id == id);
    if (!p) return;
    $('#title').textContent = 'Изменить товар';
    $('#pid').value = p.id;
    $('#name').value = p.name;
    $('#categoryId').value = p.categoryId;
    $('#price').value = p.price;
    $('#stock').value = p.stock;
    $('#description').value = p.description || '';
    $('#imageUrl').value = p.imageUrl || '';
    $('#visible').checked = p.visible;
  },

  async categories() {
    const categories = await get('/api/categories');
    $('#tbl').innerHTML = table(['№', 'Название', ''], categories.map((c) => `<tr><td>${c.id}</td>
      <td><form class="row" method="post" action="/admin/categories/${c.id}">
        <input name="name" value="${esc(c.name)}" style="width:auto" required><button class="btn line sm">Переименовать</button></form></td>
      <td><form method="post" action="/admin/categories/${c.id}/delete"><button class="btn pink sm">Удалить</button></form></td></tr>`));
  },

  async orders() {
    const orders = await get('/api/admin/orders');
    $('#tbl').innerHTML = table(
      ['№', 'Дата', 'Покупатель', 'Состав', 'Сумма', 'Статус', 'Сменить статус'],
      orders.map((o) => `<tr>
        <td>${o.id}</td><td>${o.createdAt}</td>
        <td>${esc(o.customerName)}<br><span class="muted">${esc(o.phone || o.email)}</span></td>
        <td>${o.items.map((i) => esc(i.name) + ' × ' + i.qty).join('<br>')}</td>
        <td>${money(o.total)}</td><td>${statusBadge(o.status)}</td>
        <td><div class="row">${(o.nextStatuses || []).map((s) => `
          <form method="post" action="/admin/orders/${o.id}/status">${hidden('status', s)}<button class="btn line sm">${STATUS[s] || s}</button></form>`).join('') || '—'}
        </div></td></tr>`));
  },

  async users() {
    const users = await get('/api/admin/users');
    $('#tbl').innerHTML = table(['№', 'Имя', 'E-mail', 'Телефон', 'Роль'], users.map((u) => `<tr><td>${u.id}</td><td>${esc(u.name)}</td><td>${esc(u.email)}</td><td>${esc(u.phone)}</td>
      <td>${u.role === 'ADMIN' ? 'Администратор' : 'Покупатель'}</td></tr>`));
  }
};

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
