package shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import shop.exception.ApiException;
import shop.model.Category;
import shop.model.Order;
import shop.model.OrderItem;
import shop.model.OrderStatus;
import shop.model.Product;
import shop.model.Role;
import shop.model.User;
import shop.repository.OrderRepo;
import shop.repository.ProductRepo;
import shop.support.TestFixtures;

/**
 * Модульные тесты оформления заказа и работы со статусами.
 * База данных не используется: репозитории — моки.
 */
@DisplayName("OrderService — заказы")
class OrderServiceTest {

    private OrderRepo orders;
    private ProductRepo products;
    private OrderService service;
    private Category flowers;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepo.class);
        products = mock(ProductRepo.class);
        service = new OrderService(orders, products);
        flowers = TestFixtures.category(1L, "Цветы");
        // save() просто возвращает то, что ему передали (как делает настоящий JPA)
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
    }

    private Product roses(int stock) {
        return TestFixtures.product(1L, "Розы", "10.00", stock, true, flowers);
    }

    private Product tulips() {
        return TestFixtures.product(2L, "Тюльпаны", "25.50", 10, true, flowers);
    }

    private Map<Long, Integer> cart(Object... pairs) {
        Map<Long, Integer> cart = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            cart.put((Long) pairs[i], (Integer) pairs[i + 1]);
        }
        return cart;
    }

    private OrderService.OrderData form() {
        return new OrderService.OrderData("Иван", "ivan@mail.local", "+375291112233", "Минск, ул. Тестовая, 1", "cash");
    }

    // =====================================================================
    @Nested
    @DisplayName("Расчёт суммы заказа")
    class Sums {

        @Test
        @DisplayName("сумма одной позиции = цена × количество")
        void суммаПоОднойПозиции() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(10)));

            Order order = service.create(null, cart(1L, 3), form());

            assertThat(order.total).isEqualByComparingTo("30.00");
            assertThat(order.items).hasSize(1);
        }

        @Test
        @DisplayName("сумма нескольких позиций складывается")
        void суммаПоНесколькимПозициям() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(10)));
            when(products.findById(2L)).thenReturn(Optional.of(tulips()));

            Order order = service.create(null, cart(1L, 2, 2L, 2), form());

            // 2 × 10.00 + 2 × 25.50 = 71.00
            assertThat(order.total).isEqualByComparingTo("71.00");
            assertThat(order.items).hasSize(2);
        }

        @Test
        @DisplayName("пустая корзина не создаёт заказ")
        void пустаяКорзинаНеСоздаётЗаказ() {
            ApiException error = (ApiException) catchThrowable(() -> service.create(null, cart(), form()));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Корзина пуста");
            verify(orders, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Оформление заказа")
    class Create {

        @Test
        @DisplayName("остаток списывается со склада")
        void остатокСписываетсяСоСклада() {
            Product product = roses(10);
            when(products.findById(1L)).thenReturn(Optional.of(product));

            service.create(null, cart(1L, 3), form());

            assertThat(product.stock).isEqualTo(7);
        }

        @Test
        @DisplayName("у каждой позиции списывается свой остаток")
        void уКаждойПозицииСписываетсяСвойОстаток() {
            Product roses = roses(10);
            Product tulips = tulips();
            when(products.findById(1L)).thenReturn(Optional.of(roses));
            when(products.findById(2L)).thenReturn(Optional.of(tulips));

            service.create(null, cart(1L, 3, 2L, 2), form());

            assertThat(roses.stock).isEqualTo(7);
            assertThat(tulips.stock).isEqualTo(8);
        }

        @Test
        @DisplayName("новый заказ получает статус «новый»")
        void новыйЗаказПолучаетСтатусНовый() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(10)));

            Order order = service.create(null, cart(1L, 1), form());

            assertThat(order.status).isEqualTo(OrderStatus.NEW);
            assertThat(order.createdAt).isNotNull();
        }

        @Test
        @DisplayName("позиция заказа хранит снимок цены")
        void позицияХранитСнимокЦены() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(10)));

            Order order = service.create(null, cart(1L, 2), form());

            OrderItem item = order.items.get(0);
            assertThat(item.productId).isEqualTo(1L);
            assertThat(item.name).isEqualTo("Розы");
            assertThat(item.qty).isEqualTo(2);
            assertThat(item.price).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("заказ гостя остаётся без пользователя")
        void заказГостяОстаётсяБезПользователя() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(10)));

            Order order = service.create(null, cart(1L, 1), form());

            assertThat(order.userId).isNull();
        }

        @Test
        @DisplayName("заказ пользователя привязывается к его id")
        void заказПользователяПривязываетсяКЕгоId() {
            User user = TestFixtures.user(7L, "ivan@mail.local", Role.USER);
            when(products.findById(1L)).thenReturn(Optional.of(roses(10)));

            Order order = service.create(user, cart(1L, 1), form());

            assertThat(order.userId).isEqualTo(7L);
        }

        @Test
        @DisplayName("при нехватке остатка заказ не создаётся")
        void приНехваткеОстаткаЗаказНеСоздаётся() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(2)));

            ApiException error = (ApiException) catchThrowable(() -> service.create(null, cart(1L, 3), form()));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
            assertThat(error).hasMessageContaining("Недостаточно на складе");
            verify(orders, never()).save(any());
        }

        @Test
        @DisplayName("товар, которого больше нет в продаже, блокирует оформление")
        void товарКоторогоНетВПродажеБлокируетОформление() {
            when(products.findById(1L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> service.create(null, cart(1L, 1), form()));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
            assertThat(error).hasMessageContaining("больше не продаётся");
        }

        @Test
        @DisplayName("скрытый товар блокирует оформление")
        void скрытыйТоварБлокируетОформление() {
            Product hidden = TestFixtures.product(1L, "Скрытый", "10.00", 10, false, flowers);
            when(products.findById(1L)).thenReturn(Optional.of(hidden));

            ApiException error = (ApiException) catchThrowable(() -> service.create(null, cart(1L, 1), form()));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
        }

        @Test
        @DisplayName("без имени заказ оформить нельзя")
        void безИмениЗаказОформитьНельзя() {
            ApiException error = (ApiException) catchThrowable(() -> service.create(
                    null, cart(1L, 1),
                    new OrderService.OrderData("  ", "ivan@mail.local", "", "Минск", "cash")));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Укажите имя");
        }

        @Test
        @DisplayName("нужен e-mail или телефон")
        void нуженEmailИлиТелефон() {
            ApiException error = (ApiException) catchThrowable(() -> service.create(
                    null, cart(1L, 1),
                    new OrderService.OrderData("Иван", "", "", "Минск", "cash")));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Укажите e-mail или телефон");
        }

        @Test
        @DisplayName("нужен адрес доставки")
        void нуженАдресДоставки() {
            ApiException error = (ApiException) catchThrowable(() -> service.create(
                    null, cart(1L, 1),
                    new OrderService.OrderData("Иван", "ivan@mail.local", "", "  ", "cash")));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Укажите адрес доставки");
        }

        @Test
        @DisplayName("пустой способ оплаты заменяется наличными")
        void пустойСпособОплатыЗаменяетсяНаличными() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(10)));

            Order order = service.create(null, cart(1L, 1),
                    new OrderService.OrderData("Иван", "ivan@mail.local", "", "Минск", "   "));

            assertThat(order.payment).isEqualTo("cash");
        }

        @Test
        @DisplayName("данные заказа обрезаются от лишних пробелов")
        void данныеЗаказаОбрезаются() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(10)));

            Order order = service.create(null, cart(1L, 1),
                    new OrderService.OrderData("  Иван  ", "  ivan@mail.local ", " ", "  Минск  ", "card"));

            assertThat(order.customerName).isEqualTo("Иван");
            assertThat(order.email).isEqualTo("ivan@mail.local");
            assertThat(order.address).isEqualTo("Минск");
            assertThat(order.phone).isEmpty();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Смена статуса заказа")
    class ChangeStatus {

        private Order order;

        @BeforeEach
        void prepare() {
            order = TestFixtures.order(5L, OrderStatus.NEW);
        }

        @Test
        @DisplayName("переход «новый → в обработке» разрешён")
        void переходВОбработкуРазрешён() {
            when(orders.findById(5L)).thenReturn(Optional.of(order));

            Order result = service.changeStatus(5L, "processing");

            assertThat(result.status).isEqualTo(OrderStatus.PROCESSING);
            verify(orders).save(order);
        }

        @Test
        @DisplayName("переход «в обработке → доставлен» разрешён")
        void переходВДоставленРазрешён() {
            order.status = OrderStatus.PROCESSING;
            when(orders.findById(5L)).thenReturn(Optional.of(order));

            Order result = service.changeStatus(5L, "delivered");

            assertThat(result.status).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("переход «новый → доставлен» запрещён")
        void переходВДоставленИзНовогоЗапрещён() {
            when(orders.findById(5L)).thenReturn(Optional.of(order));

            ApiException error = (ApiException) catchThrowable(() -> service.changeStatus(5L, "delivered"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
            assertThat(error).hasMessage("Недопустимый переход статуса");
            assertThat(order.status).isEqualTo(OrderStatus.NEW);
        }

        @Test
        @DisplayName("переход «доставлен → новый» запрещён")
        void переходИзДоставленВНовыйЗапрещён() {
            order.status = OrderStatus.DELIVERED;
            when(orders.findById(5L)).thenReturn(Optional.of(order));

            ApiException error = (ApiException) catchThrowable(() -> service.changeStatus(5L, "new"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
            assertThat(order.status).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("переход «в обработке → новый» запрещён")
        void переходВНовыйИзОбработкиЗапрещён() {
            order.status = OrderStatus.PROCESSING;
            when(orders.findById(5L)).thenReturn(Optional.of(order));

            ApiException error = (ApiException) catchThrowable(() -> service.changeStatus(5L, "new"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
        }

        @Test
        @DisplayName("отменённый заказ менять статус нельзя")
        void отменённыйЗаказНельзяМенять() {
            order.status = OrderStatus.CANCELLED;
            when(orders.findById(5L)).thenReturn(Optional.of(order));

            ApiException error = (ApiException) catchThrowable(() -> service.changeStatus(5L, "processing"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
        }

        @Test
        @DisplayName("отмена возвращает товар на склад")
        void отменаВозвращаетТоварНаСклад() {
            TestFixtures.item(order, 1L, "Розы", 3, "10.00");
            TestFixtures.item(order, 2L, "Тюльпаны", 2, "25.50");
            Product roses = roses(7);
            Product tulips = tulips();
            when(orders.findById(5L)).thenReturn(Optional.of(order));
            when(products.findById(1L)).thenReturn(Optional.of(roses));
            when(products.findById(2L)).thenReturn(Optional.of(tulips));

            service.changeStatus(5L, "cancelled");

            assertThat(roses.stock).isEqualTo(10);
            assertThat(tulips.stock).isEqualTo(12);
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("отмена не ломается, если товара уже нет в базе")
        void отменаНеЛомаетсяЕслиТовараНетВБазе() {
            TestFixtures.item(order, 1L, "Розы", 3, "10.00");
            when(orders.findById(5L)).thenReturn(Optional.of(order));
            when(products.findById(1L)).thenReturn(Optional.empty());

            Order result = service.changeStatus(5L, "cancelled");

            assertThat(result.status).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("неизвестный статус отклоняется")
        void неизвестныйСтатусОтклоняется() {
            when(orders.findById(5L)).thenReturn(Optional.of(order));

            ApiException error = (ApiException) catchThrowable(() -> service.changeStatus(5L, "вывезен"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Неизвестный статус");
        }

        @Test
        @DisplayName("пустой статус отклоняется")
        void пустойСтатусОтклоняется() {
            when(orders.findById(5L)).thenReturn(Optional.of(order));

            ApiException error = (ApiException) catchThrowable(() -> service.changeStatus(5L, "  "));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Неизвестный статус");
        }

        @Test
        @DisplayName("смена статуса несуществующего заказа отклоняется")
        void сменаСтатусаНесуществующегоЗаказаОтклоняется() {
            when(orders.findById(404L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> service.changeStatus(404L, "processing"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
            assertThat(error).hasMessage(ApiException.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("статус можно писать в любом регистре")
        void статусМожноПисатьВЛюбомРегистре() {
            when(orders.findById(5L)).thenReturn(Optional.of(order));

            assertThat(service.changeStatus(5L, "PROCESSING").status).isEqualTo(OrderStatus.PROCESSING);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Просмотр заказов")
    class View {

        @Test
        @DisplayName("заказы пользователя приходят из репозитория")
        void заказыПользователяПриходятИзРепозитория() {
            User user = TestFixtures.user(7L, "ivan@mail.local", Role.USER);
            List<Order> found = List.of(TestFixtures.order(1L, OrderStatus.NEW));
            when(orders.findByUserIdOrderByIdDesc(7L)).thenReturn(found);

            assertThat(service.ordersOf(user)).isSameAs(found);
        }

        @Test
        @DisplayName("все заказы приходят из репозитория")
        void всеЗаказыПриходятИзРепозитория() {
            List<Order> found = List.of(TestFixtures.order(1L, OrderStatus.NEW));
            when(orders.findAllByOrderByIdDesc()).thenReturn(found);

            assertThat(service.allOrders()).isSameAs(found);
        }

        @Test
        @DisplayName("свой заказ находится по id и id пользователя")
        void свойЗаказНаходитсяПоId() {
            Order found = TestFixtures.order(3L, OrderStatus.DELIVERED);
            when(orders.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(found));

            assertThat(service.findOne(3L, 7L)).isSameAs(found);
        }

        @Test
        @DisplayName("чужой заказ не находится")
        void чужойЗаказНеНаходится() {
            when(orders.findByIdAndUserId(3L, 7L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> service.findOne(3L, 7L));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
            assertThat(error).hasMessage(ApiException.ORDER_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("сумма заказа хранится с двумя знаками после запятой")
    void суммаЗаказаХранитсяСДвумяЗнаками() {
        when(products.findById(1L)).thenReturn(Optional.of(roses(10)));

        Order order = service.create(null, cart(1L, 3), form());

        assertThat(order.total).isEqualByComparingTo(BigDecimal.valueOf(30));
    }
}
