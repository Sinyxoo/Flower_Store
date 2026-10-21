// Общие мелочи для всех страниц: поиск элемента, экранирование, деньги, запрос к серверу.
// Файл подключается первым — остальные берут эти функции отсюда.

const $ = (selector) => document.querySelector(selector);

// Защита от подстановки чужого HTML в текст
const esc = (text) => String(text ?? '').replace(/[&<>"']/g,
  (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const money = (n) => Number(n).toLocaleString('ru-RU',
  { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' Br';

// Запрос данных у сервера. Гость (ответ 204) даёт null.
async function get(url) {
  const response = await fetch(url);
  if (response.status === 204) return null;
  if (!response.ok) throw new Error('Ошибка ' + response.status);
  return response.json();
}
