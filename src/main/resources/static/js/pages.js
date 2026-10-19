/* Логика отдельных страниц. Какая страница — определяется по <body data-page="..."> */
const app = () => $('#app');
const empty = (icon, text, link) => `<div class="empty"><div class="big">${icon}</div><p>${text}</p>${link || ''}</div>`;
const card = p => `<div class="card"><a class="pic" href="product.html?id=${p.id}">${pic(p)}</a><div class="in">
  <span class="muted">${esc(p.categoryName)}</span><h3><a href="product.html?id=${p.id}">${esc(p.name)}</a></h3>
  <span class="price">${money(p.price)}</span>
  ${p.stock > 0 ? `<button class="btn" data-add="${p.id}">В корзину</button>` : `<button class="btn" disabled>Нет в наличии</button>`}</div></div>`;
async function addHandler(e) {
  const id = e.target.dataset.add; if (!id) return;
  try { await API.addToCart(+id, +($('#qty')?.value || 1)); toast('Товар добавлен в корзину'); refreshCart(); }
  catch (x) { toast(x.message, true); }
}
document.addEventListener('click', addHandler);
const val = f => Object.fromEntries(new FormData(f));
function fieldErr(f, name, msg) { const el = f.querySelector(`[name=${name}]`); el.classList.add('invalid'); el.insertAdjacentHTML('afterend', `<div class="err">${msg}</div>`); }
function clearErr(f) { f.querySelectorAll('.err').forEach(e => e.remove()); f.querySelectorAll('.invalid').forEach(e => e.classList.remove('invalid')); }

const PAGES = {
  async home() {
    const [list, cats] = await Promise.all([API.products({sort: 'price_desc'}), API.categories()]);
    app().innerHTML = `<section class="hero"><h1>Живые цветы с доставкой на дом</h1><p>Розы, тюльпаны и готовые букеты. Оформите заказ без регистрации.</p>
      <a class="btn pink" href="catalog.html">Открыть каталог</a></section>
      <h2>Категории</h2><div class="chips">${cats.map(c => `<a class="chip" href="catalog.html?category=${c.id}">${esc(c.name)}</a>`).join('')}</div>
      <h2>Популярное</h2><div class="grid">${list.slice(0, 4).map(card).join('')}</div>`;
  },
  async catalog() {
    const cats = await API.categories(), q = new URLSearchParams(location.search);
    app().innerHTML = `<h1>Каталог</h1><div class="toolbar">
      <div><label for="cat">Категория</label><select id="cat"><option value="">Все</option>${cats.map(c => `<option value="${c.id}"${q.get('category') == c.id ? ' selected' : ''}>${esc(c.name)}</option>`).join('')}</select></div>
      <div><label for="sort">Сортировка</label><select id="sort"><option value="">По умолчанию</option><option value="price">Сначала дешёвые</option><option value="price_desc">Сначала дорогие</option></select></div></div>
      <div class="grid" id="list"></div>`;
    const load = async () => {
      const l = await API.products({category: $('#cat').value, sort: $('#sort').value});
      $('#list').outerHTML = `<div class="grid" id="list">${l.length ? l.map(card).join('') : ''}</div>`;
      if (!l.length) $('#list').outerHTML = empty('🥀', 'В этой категории пока нет товаров.');
    };
    $('#cat').onchange = $('#sort').onchange = load; load();
  },
  async product() {
    try {
      const p = await API.product(new URLSearchParams(location.search).get('id'));
      app().innerHTML = `<p><a href="catalog.html">← В каталог</a></p><div class="split"><div class="card"><div class="pic" style="height:380px;font-size:140px">${pic(p)}</div></div>
        <div><span class="muted">${esc(p.categoryName)}</span><h1>${esc(p.name)}</h1><p class="price" style="font-size:28px">${money(p.price)}</p>
        <p>${esc(p.description) || 'Описание скоро появится.'}</p>
        <p class="muted">${p.stock > 0 ? 'В наличии: ' + p.stock + ' шт.' : 'Нет в наличии'}</p>
        ${p.stock > 0 ? `<div class="toolbar"><div><label for="qty">Количество</label><input id="qty" type="number" min="1" max="${p.stock}" value="1" class="qty"></div><button class="btn" data-add="${p.id}">В корзину</button></div>` : '<button class="btn" disabled>Нет в наличии</button>'}</div></div>`;
    } catch { app().innerHTML = empty('🔍', 'Такого товара нет.', '<a class="btn" href="catalog.html">В каталог</a>'); }
  },
  async cart() {
    const draw = async () => {
      const c = await API.cart();
      if (!c.items.length) { app().innerHTML = '<h1>Корзина</h1>' + empty('🛒', 'Корзина пуста. Добавьте цветы из каталога.', '<a class="btn" href="catalog.html">Перейти в каталог</a>'); return; }
      app().innerHTML = `<h1>Корзина</h1><div class="tbox"><table><tr><th>Товар</th><th>Цена</th><th>Кол-во</th><th>Сумма</th><th></th></tr>
        ${c.items.map(i => `<tr><td><span class="thumb">${pic(i)}</span>${esc(i.name)}</td><td>${money(i.price)}</td>
        <td><input class="qty" type="number" min="1" max="${i.stock}" value="${i.qty}" data-qty="${i.productId}"></td><td>${money(i.sum)}</td>
        <td><button class="btn line sm" data-del="${i.productId}">Удалить</button></td></tr>`).join('')}</table></div>
        <div class="total">Итого: ${money(c.total)}</div><div class="row" style="justify-content:flex-end"><a class="btn line" href="catalog.html">Продолжить покупки</a><a class="btn pink" href="checkout.html">Оформить заказ</a></div>`;
      refreshCart();
    };
    app().addEventListener('change', async e => { if (!e.target.dataset.qty) return; try { await API.setQty(+e.target.dataset.qty, Math.max(1, +e.target.value)); } catch (x) { toast(x.message, true); } draw(); });
    app().addEventListener('click', async e => { if (e.target.dataset.del) { await API.removeItem(+e.target.dataset.del); draw(); } });
    draw();
  },
  async checkout() {
    const [c, me] = await Promise.all([API.cart(), API.me()]);
    if (!c.items.length) { app().innerHTML = '<h1>Оформление заказа</h1>' + empty('🛒', 'Корзина пуста — оформлять пока нечего.', '<a class="btn" href="catalog.html">В каталог</a>'); return; }
    const u = me || {};
    app().innerHTML = `<h1>Оформление заказа</h1><div class="split"><form class="form" id="f" novalidate>
      <div class="field"><label for="n">Имя</label><input id="n" name="customerName" value="${esc(u.name)}"></div>
      <div class="field"><label for="e">E-mail</label><input id="e" name="email" type="email" value="${esc(u.email)}"></div>
      <div class="field"><label for="t">Телефон</label><input id="t" name="phone" value="${esc(u.phone)}"></div>
      <div class="field"><label for="a">Адрес доставки</label><input id="a" name="address" value="${esc(u.address)}"></div>
      <div class="field"><label for="pm">Способ оплаты</label><select id="pm" name="payment"><option value="cash">Наличными курьеру</option><option value="card">Картой курьеру</option><option value="online">Онлайн (условно)</option></select></div>
      <button class="btn pink">Подтвердить заказ</button></form>
      <div><h3>Ваш заказ</h3><div class="tbox" style="margin-top:12px"><table>${c.items.map(i => `<tr><td>${esc(i.name)} × ${i.qty}</td><td class="r">${money(i.sum)}</td></tr>`).join('')}</table></div><div class="total">Итого: ${money(c.total)}</div></div></div>`;
    $('#f').onsubmit = async e => {
      e.preventDefault(); const f = e.target, d = val(f); clearErr(f); let bad = false;
      if (!d.customerName.trim()) { fieldErr(f, 'customerName', 'Укажите имя'); bad = true; }
      if (!d.email.trim() && !d.phone.trim()) { fieldErr(f, 'email', 'Укажите e-mail или телефон'); bad = true; }
      if (!d.address.trim()) { fieldErr(f, 'address', 'Укажите адрес доставки'); bad = true; }
      if (bad) return;
      try { const o = await API.createOrder(d); location.href = 'order-success.html?id=' + o.id; } catch (x) { toast(x.message, true); }
    };
  },
  success() { const id = new URLSearchParams(location.search).get('id'); if (id) $('#oid').textContent = '№ ' + id; },
  login() {
    $('#f').onsubmit = async e => { e.preventDefault(); const d = val(e.target); try { await API.login(d.email, d.password); location.href = 'account.html'; } catch (x) { toast(x.message, true); } };
  },
  register() {
    $('#f').onsubmit = async e => {
      e.preventDefault(); const f = e.target, d = val(f); clearErr(f); let bad = false;
      if (!d.name.trim()) { fieldErr(f, 'name', 'Укажите имя'); bad = true; }
      if (!/^\S+@\S+\.\S+$/.test(d.email)) { fieldErr(f, 'email', 'Введите корректный e-mail'); bad = true; }
      if (d.password.length < 6) { fieldErr(f, 'password', 'Пароль — не короче 6 символов'); bad = true; }
      if (d.password !== d.password2) { fieldErr(f, 'password2', 'Пароли не совпадают'); bad = true; }
      if (bad) return;
      try { await API.register({name: d.name, email: d.email, password: d.password}); location.href = 'account.html'; } catch (x) { toast(x.message, true); }
    };
  },
  async account() {
    const me = await API.me();
    if (!me) { app().innerHTML = empty('🔒', 'Войдите, чтобы увидеть личный кабинет.', '<a class="btn" href="login.html">Войти</a>'); return; }
    const orders = await API.myOrders();
    app().innerHTML = `<h1>Личный кабинет</h1><div class="split"><form class="form" id="f">
      <h3 style="margin-bottom:14px">Мои данные</h3>
      <div class="field"><label>E-mail</label><input value="${esc(me.email)}" disabled></div>
      <div class="field"><label for="n">Имя</label><input id="n" name="name" value="${esc(me.name)}"></div>
      <div class="field"><label for="t">Телефон</label><input id="t" name="phone" value="${esc(me.phone)}"></div>
      <div class="field"><label for="a">Адрес доставки</label><input id="a" name="address" value="${esc(me.address)}"></div>
      <button class="btn">Сохранить</button></form>
      <div><h3 style="margin-bottom:14px">История заказов</h3>${orders.length ? `<div class="tbox"><table><tr><th>№</th><th>Дата</th><th>Сумма</th><th>Статус</th><th></th></tr>
      ${orders.map(o => `<tr><td>${o.id}</td><td>${o.createdAt}</td><td>${money(o.total)}</td><td>${badge(o.status)}</td><td><button class="btn line sm" data-repeat="${o.id}">Повторить</button></td></tr>`).join('')}</table></div>` : empty('📦', 'Заказов пока нет.')}</div></div>`;
    $('#f').onsubmit = async e => { e.preventDefault(); try { await API.updateMe(val(e.target)); toast('Данные сохранены'); } catch (x) { toast(x.message, true); } };
    app().addEventListener('click', async e => {
      const id = e.target.dataset.repeat; if (!id) return;
      try { for (const i of orders.find(o => o.id == id).items) { const p = (await API.products()).find(p => p.name === i.name); if (p) await API.addToCart(p.id, i.qty); } location.href = 'cart.html'; }
      catch (x) { toast(x.message, true); }
    });
  },
  contacts() {
    $('#f').onsubmit = async e => {
      e.preventDefault(); const f = e.target, d = val(f); clearErr(f);
      if (!d.message.trim()) return fieldErr(f, 'message', 'Напишите сообщение');
      try { await API.sendMessage(d); f.reset(); toast('Сообщение отправлено'); } catch (x) { toast(x.message, true); }
    };
  },

  /* ---------- админ-панель ---------- */
  async products() {
    const cats = await API.categories();
    app().innerHTML = `<div class="toolbar" style="justify-content:space-between"><h1 style="margin:0">Товары</h1><button class="btn" id="add">Добавить товар</button></div><div class="tbox" id="tbl"></div>
      <dialog id="dlg"><form id="f" novalidate><h3 id="dt" style="margin-bottom:14px"></h3><input type="hidden" name="id">
      <div class="field"><label>Название</label><input name="name"></div>
      <div class="field"><label>Категория</label><select name="categoryId">${cats.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('')}</select></div>
      <div class="field"><label>Цена, Br</label><input name="price" type="number" step="0.01"></div>
      <div class="field"><label>Остаток на складе, шт.</label><input name="stock" type="number" min="0" value="0"></div>
      <div class="field"><label>Описание</label><textarea name="description" rows="3"></textarea></div>
      <div class="field"><label>Ссылка на изображение</label><input name="imageUrl"></div>
      <div class="field check"><input type="checkbox" name="visible" id="vis" checked><label for="vis" style="margin:0">Показывать на сайте</label></div>
      <div class="row"><button class="btn">Сохранить</button><button type="button" class="btn line" id="cancel">Отмена</button></div></form></dialog>`;
    let list = [];
    const draw = async () => {
      list = await API.admin.products();
      $('#tbl').innerHTML = `<table><tr><th>Товар</th><th>Категория</th><th>Цена</th><th>Остаток</th><th>На сайте</th><th></th></tr>${list.map(p => `<tr><td><span class="thumb">${pic(p)}</span>${esc(p.name)}</td><td>${esc(p.categoryName)}</td><td>${money(p.price)}</td><td>${p.stock}</td><td>${p.visible ? 'да' : 'скрыт'}</td>
        <td><div class="row"><button class="btn line sm" data-edit="${p.id}">Изменить</button><button class="btn line sm" data-vis="${p.id}">${p.visible ? 'Скрыть' : 'Показать'}</button><button class="btn pink sm" data-rm="${p.id}">Удалить</button></div></td></tr>`).join('')}</table>`;
    };
    const open = p => { const f = $('#f'); clearErr(f); f.reset(); $('#dt').textContent = p ? 'Изменить товар' : 'Новый товар';
      if (p) { f.id.value = p.id; f.name.value = p.name; f.categoryId.value = p.categoryId; f.price.value = p.price; f.stock.value = p.stock; f.description.value = p.description || ''; f.imageUrl.value = p.imageUrl || ''; f.visible.checked = p.visible; } $('#dlg').showModal(); };
    $('#add').onclick = () => open(); $('#cancel').onclick = () => $('#dlg').close();
    $('#f').onsubmit = async e => {
      e.preventDefault(); const f = e.target; clearErr(f); let bad = false;
      if (!f.name.value.trim()) { fieldErr(f, 'name', 'Название не может быть пустым'); bad = true; }
      if (f.price.value === '' || +f.price.value < 0) { fieldErr(f, 'price', 'Цена не может быть отрицательной'); bad = true; }
      if (+f.stock.value < 0) { fieldErr(f, 'stock', 'Остаток не может быть отрицательным'); bad = true; }
      if (bad) return;
      try { await API.admin.saveProduct({id: f.id.value ? +f.id.value : null, name: f.name.value.trim(), categoryId: +f.categoryId.value, price: +f.price.value, stock: +f.stock.value, description: f.description.value, imageUrl: f.imageUrl.value, visible: f.visible.checked}); $('#dlg').close(); toast('Товар сохранён'); draw(); }
      catch (x) { toast(x.message, true); }
    };
    app().addEventListener('click', async e => { const d = e.target.dataset;
      try {
        if (d.edit) open(list.find(p => p.id == d.edit));
        if (d.vis) { const p = list.find(p => p.id == d.vis); await API.admin.saveProduct({...p, visible: !p.visible}); draw(); }
        if (d.rm && confirm('Удалить товар?')) { await API.admin.deleteProduct(+d.rm); toast('Товар удалён'); draw(); }
      } catch (x) { toast(x.message, true); } });
    draw();
  },
  async categories() {
    app().innerHTML = `<h1>Категории</h1><form class="toolbar" id="f"><div><label for="nm">Новая категория</label><input id="nm" name="name"></div><button class="btn">Добавить</button></form><div class="tbox" id="tbl"></div>`;
    const draw = async () => { const l = await API.categories(); $('#tbl').innerHTML = `<table><tr><th>№</th><th>Название</th><th></th></tr>${l.map(c => `<tr><td>${c.id}</td><td>${esc(c.name)}</td><td><div class="row"><button class="btn line sm" data-ren="${c.id}" data-n="${esc(c.name)}">Переименовать</button><button class="btn pink sm" data-rm="${c.id}">Удалить</button></div></td></tr>`).join('')}</table>`; };
    $('#f').onsubmit = async e => { e.preventDefault(); try { await API.admin.saveCategory({name: $('#nm').value}); e.target.reset(); toast('Категория добавлена'); draw(); } catch (x) { toast(x.message, true); } };
    app().addEventListener('click', async e => { const d = e.target.dataset;
      try {
        if (d.ren) { const n = prompt('Новое название', d.n); if (n !== null) { await API.admin.saveCategory({id: +d.ren, name: n}); draw(); } }
        if (d.rm && confirm('Удалить категорию?')) { await API.admin.deleteCategory(+d.rm); draw(); }
      } catch (x) { toast(x.message, true); } });
    draw();
  },
  async orders() {
    app().innerHTML = '<h1>Заказы</h1><div class="tbox" id="tbl"></div>';
    const draw = async () => { const l = await API.admin.orders();
      $('#tbl').innerHTML = `<table><tr><th>№</th><th>Дата</th><th>Покупатель</th><th>Состав</th><th>Сумма</th><th>Статус</th><th>Сменить статус</th></tr>${l.map(o => `<tr><td>${o.id}</td><td>${o.createdAt}</td>
        <td>${esc(o.customerName)}<br><span class="muted">${esc(o.phone || o.email)}</span></td><td>${o.items.map(i => esc(i.name) + ' × ' + i.qty).join('<br>')}</td><td>${money(o.total)}</td><td>${badge(o.status)}</td>
        <td><div class="row">${Object.keys(STATUS).map(s => `<button class="btn line sm" data-id="${o.id}" data-s="${s}"${NEXT[o.status].includes(s) ? '' : ' disabled'}>${STATUS[s][0]}</button>`).join('')}</div></td></tr>`).join('')}</table>`; };
    app().addEventListener('click', async e => { const d = e.target.dataset; if (!d.s) return;
      try { await API.admin.setStatus(+d.id, d.s); toast('Статус обновлён'); draw(); } catch (x) { toast(x.message, true); } });
    draw();
  },
  async users() {
    const l = await API.admin.users();
    app().innerHTML = `<h1>Пользователи</h1><div class="tbox"><table><tr><th>№</th><th>Имя</th><th>E-mail</th><th>Телефон</th><th>Роль</th></tr>${l.map(u => `<tr><td>${u.id}</td><td>${esc(u.name)}</td><td>${esc(u.email)}</td><td>${esc(u.phone)}</td><td>${u.role === 'ADMIN' ? 'Администратор' : 'Покупатель'}</td></tr>`).join('')}</table></div>`;
  }
};

(async () => {
  await renderLayout();
  try { await PAGES[document.body.dataset.page]?.(); } catch (x) { console.error(x); app() && (app().innerHTML = empty('⚠️', 'Не удалось загрузить данные: ' + esc(x.message))); }
})();
