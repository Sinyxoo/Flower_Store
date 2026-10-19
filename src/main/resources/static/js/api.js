/* Единственное место, где страницы общаются с бэкендом.
   Есть MOCK (заглушка) — работаем с ней, нет — идём на /api/... */
const API = (() => {
  const M = window.MOCK, mock = !!M;
  const S = JSON.parse(sessionStorage.getItem('mockState') || '{"cart":[],"user":null}');
  const save = () => sessionStorage.setItem('mockState', JSON.stringify(S));
  const fail = m => { throw new Error(m); };
  const find = id => M.products.find(p => p.id == id);
  const catName = id => (M.categories.find(c => c.id == id) || {}).name || '';
  const withCat = p => ({...p, categoryName: catName(p.categoryId)});
  const j = async (url, opt) => {
    const r = await fetch(url, opt);
    if (!r.ok) { let m = 'Ошибка ' + r.status; try { m = (await r.json()).message || m; } catch {} const e = new Error(m); e.status = r.status; throw e; }
    return r.status === 204 ? null : r.json();
  };
  const send = (url, body, method = 'POST') => j(url, {method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
  const cartView = () => { const items = S.cart.map(i => { const p = find(i.productId); return {productId:p.id,name:p.name,price:p.price,qty:i.qty,stock:p.stock,emoji:p.emoji,imageUrl:p.imageUrl,sum:p.price*i.qty}; }); return {items, total: items.reduce((s,i)=>s+i.sum,0)}; };
  const validate = p => { if (!String(p.name||'').trim()) fail('Название не может быть пустым'); if (!(Number(p.price) >= 0)) fail('Цена не может быть отрицательной'); };

  return {
    products: async (q = {}) => {
      if (!mock) return j('/api/products?' + new URLSearchParams(q));
      let l = M.products.filter(p => p.visible).map(withCat);
      if (q.category) l = l.filter(p => p.categoryId == q.category);
      if (q.sort === 'price') l.sort((a,b) => a.price-b.price);
      if (q.sort === 'price_desc') l.sort((a,b) => b.price-a.price);
      return l;
    },
    product: async id => mock ? (find(id) ? withCat(find(id)) : fail('Товар не найден')) : j('/api/products/' + id),
    categories: async () => mock ? M.categories : j('/api/categories'),
    cart: async () => mock ? cartView() : j('/api/cart'),
    addToCart: async (id, qty = 1) => {
      if (!mock) return send('/api/cart/items', {productId: id, qty});
      const p = find(id) || fail('Товар не найден'); const it = S.cart.find(i => i.productId == id); const want = (it ? it.qty : 0) + qty;
      if (want > p.stock) fail('Недостаточно на складе: доступно ' + p.stock + ' шт.');
      it ? it.qty = want : S.cart.push({productId: p.id, qty}); save(); return cartView();
    },
    setQty: async (id, qty) => {
      if (!mock) return send('/api/cart/items/' + id, {qty}, 'PUT');
      const p = find(id); if (qty > p.stock) fail('Недостаточно на складе: доступно ' + p.stock + ' шт.');
      S.cart.find(i => i.productId == id).qty = qty; save(); return cartView();
    },
    removeItem: async id => { if (!mock) return j('/api/cart/items/' + id, {method: 'DELETE'}); S.cart = S.cart.filter(i => i.productId != id); save(); return cartView(); },
    createOrder: async d => {
      if (!mock) return send('/api/orders', d);
      const c = cartView(); if (!c.items.length) fail('Корзина пуста');
      const o = {id: Math.max(...M.orders.map(o => o.id)) + 1, ...d, createdAt: new Date().toISOString().slice(0,10), status: 'new', items: c.items.map(i => ({name:i.name,qty:i.qty,price:i.price})), total: c.total};
      c.items.forEach(i => find(i.productId).stock -= i.qty); M.orders.unshift(o); S.cart = []; save(); return o;
    },
    me: async () => { if (!mock) { try { return await j('/api/auth/me'); } catch { return null; } } return S.user; },
    login: async (email, password) => {
      if (!mock) return send('/api/auth/login', {email, password});
      const u = M.users.find(u => u.email === email); if (!u) fail('Неверный e-mail или пароль'); S.user = u; save(); return u;
    },
    register: async d => {
      if (!mock) return send('/api/auth/register', d);
      if (M.users.some(u => u.email === d.email)) fail('Пользователь с таким e-mail уже есть');
      const u = {id: M.users.length + 1, name: d.name, email: d.email, phone: '', address: '', role: 'USER'}; M.users.push(u); S.user = u; save(); return u;
    },
    logout: async () => { if (!mock) return send('/api/auth/logout', {}); S.user = null; save(); },
    updateMe: async d => { if (!mock) return send('/api/me', d, 'PUT'); Object.assign(S.user, d); save(); return S.user; },
    myOrders: async () => mock ? M.orders.filter(o => S.user && o.email === S.user.email) : j('/api/me/orders'),
    sendMessage: async d => mock ? null : send('/api/messages', d),
    admin: {
      products: async () => mock ? M.products.map(withCat) : j('/api/admin/products'),
      saveProduct: async p => {
        if (!mock) return p.id ? send('/api/admin/products/' + p.id, p, 'PUT') : send('/api/admin/products', p);
        validate(p); if (p.id) Object.assign(find(p.id), p); else M.products.push({...p, id: Math.max(...M.products.map(x => x.id)) + 1, emoji: '🌸'});
      },
      deleteProduct: async id => { if (!mock) return j('/api/admin/products/' + id, {method: 'DELETE'}); M.products = M.products.filter(p => p.id != id); },
      saveCategory: async c => {
        if (!mock) return c.id ? send('/api/admin/categories/' + c.id, c, 'PUT') : send('/api/admin/categories', c);
        if (!c.name.trim()) fail('Название категории не может быть пустым');
        c.id ? M.categories.find(x => x.id == c.id).name = c.name : M.categories.push({id: Math.max(...M.categories.map(x => x.id)) + 1, name: c.name});
      },
      deleteCategory: async id => { if (!mock) return j('/api/admin/categories/' + id, {method: 'DELETE'}); if (M.products.some(p => p.categoryId == id)) fail('Нельзя удалить категорию, в которой есть товары'); M.categories = M.categories.filter(c => c.id != id); },
      orders: async () => mock ? M.orders : j('/api/admin/orders'),
      setStatus: async (id, status) => { if (!mock) return send('/api/admin/orders/' + id + '/status', {status}, 'PUT'); M.orders.find(o => o.id == id).status = status; },
      users: async () => mock ? M.users : j('/api/admin/users')
    }
  };
})();
