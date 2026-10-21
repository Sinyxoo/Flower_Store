// Общее для всех страниц: шапка, подвал, всплывающее сообщение.
// Здесь нет логики магазина — только показ.

// Пока layout.js не закончил, pages.js ждёт window.appReady —
// чтобы страница не рисовалась раньше шапки.
window.appReady = (async () => {
  // Сообщение после действия: сервер возвращает ?msg=... или ?error=...
  const params = new URLSearchParams(location.search);
  if (params.get('error')) toast(params.get('error'), true);
  else if (params.get('msg')) toast(params.get('msg'));
  params.delete('msg');
  params.delete('error');
  history.replaceState(null, '', location.pathname + (params.toString() ? '?' + params : ''));

  const page = document.body.dataset.page;
  const isAdminPage = document.body.hasAttribute('data-admin');
  const on = (name) => (page === name ? ' class="on"' : '');

  let me = null;
  let cartCount = 0;
  try {
    me = await get('/api/auth/me');
    if (!isAdminPage) {
      const cart = await get('/api/cart');
      cartCount = cart.items.reduce((sum, item) => sum + item.qty, 0);
    }
  } catch (e) { /* сервер недоступен — покажем страницу без данных */ }

  if (isAdminPage && (!me || me.role !== 'ADMIN')) {
    location.href = '/403.html';
    return false;   // уходим на 403 — страницу рисовать не надо
  }

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

  const footer = $('#site-footer');
  if (footer) footer.outerHTML = `<footer class="bottom">Flower Store · г. Минск, пр. Независимости, 10 ·
    +375 29 000-00-00 · info@flowerstore.local<br>Учебный проект · БНТУ</footer>`;

  const side = $('#side');
  if (side) {
    const links = [['products', 'Товары'], ['categories', 'Категории'], ['orders', 'Заказы'], ['users', 'Пользователи']];
    side.innerHTML = links.map(([name, title]) => `<a href="/admin/${name}.html"${on(name)}>${title}</a>`).join('')
      + '<a href="/index.html">← На сайт</a>';
  }

  return true;
})();

function toast(text, isError) {
  const box = document.createElement('div');
  box.className = 'toast' + (isError ? ' bad' : '');
  box.textContent = text;
  document.body.append(box);
  setTimeout(() => box.remove(), 3500);
}
