/* Общие утилиты + шапка/подвал/боковое меню, подставляются на все страницы */
const ROOT = document.body.dataset.root || '';
const $ = (s, r = document) => r.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money = n => Number(n).toLocaleString('ru-RU', {minimumFractionDigits: 2, maximumFractionDigits: 2}) + ' Br';
const STATUS = {new:['Новый','pink'], processing:['В обработке','amber'], delivered:['Доставлен','green'], cancelled:['Отменён','grey']};
const NEXT = {new:['processing','cancelled'], processing:['delivered','cancelled'], delivered:[], cancelled:[]};
const badge = s => `<span class="st ${STATUS[s][1]}">${STATUS[s][0]}</span>`;
const pic = p => p.imageUrl ? `<img src="${esc(p.imageUrl)}" alt="">` : `<span>${p.emoji || '🌸'}</span>`;

function toast(msg, bad) {
  const t = Object.assign(document.createElement('div'), {className: 'toast' + (bad ? ' bad' : ''), textContent: msg, role: 'status'});
  document.body.append(t); setTimeout(() => t.remove(), 3200);
}

async function renderLayout() {
  const page = document.body.dataset.page, admin = document.body.hasAttribute('data-admin');
  const user = await API.me();
  if (admin && !window.MOCK && (!user || user.role !== 'ADMIN')) { location.href = ROOT + '403.html'; return; }
  const on = p => page === p ? ' class="on"' : '';
  const nav = admin ? '' : `<nav>
      <a href="${ROOT}catalog.html"${on('catalog')}>Каталог</a>
      <a href="${ROOT}cart.html"${on('cart')}>Корзина<span class="cnt" id="cartCnt">0</span></a>
      <a href="${ROOT}account.html"${on('account')}>Личный кабинет</a>
      <a href="${ROOT}contacts.html"${on('contacts')}>Контакты</a></nav>`;
  const who = user
    ? `<span>${esc(user.name)}</span>${user.role === 'ADMIN' ? `<a href="${ROOT}admin/products.html">Админ-панель</a>` : ''}<button class="btn line sm" id="logout">Выйти</button>`
    : `<a href="${ROOT}login.html">Войти</a><a class="btn sm" href="${ROOT}register.html">Регистрация</a>`;
  $('#site-header').outerHTML = `<header class="top"><div class="bar"><a class="logo" href="${ROOT}index.html">🌸 Flower Store</a>${admin ? '<nav><span class="muted">Панель администратора</span></nav>' : nav}<div class="user">${who}</div></div></header>`;
  const f = $('#site-footer');
  if (f) f.outerHTML = `<footer class="bottom">Flower Store · г. Минск, пр. Независимости, 10 · +375 29 000-00-00 · info@flowerstore.local<br>Учебный проект · БНТУ</footer>`;
  const s = $('#side');
  if (s) { const l = [['products','Товары'],['categories','Категории'],['orders','Заказы'],['users','Пользователи']];
    s.innerHTML = l.map(([k, t]) => `<a href="${k}.html"${on(k)}>${t}</a>`).join('') + `<a href="${ROOT}index.html">← На сайт</a>`; }
  $('#logout')?.addEventListener('click', async () => { await API.logout(); location.href = ROOT + 'index.html'; });
  refreshCart();
}
async function refreshCart() {
  const el = $('#cartCnt'); if (!el) return;
  try { el.textContent = (await API.cart()).items.reduce((s, i) => s + i.qty, 0); } catch {}
}
