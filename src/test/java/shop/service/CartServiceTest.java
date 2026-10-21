package shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import shop.exception.ApiException;
import shop.model.Category;
import shop.model.Product;
import shop.repository.ProductRepo;
import shop.support.TestFixtures;

/**
 * Модульные тесты корзины. Базы данных здесь нет: репозиторий — мок,
 * сессия — обычная Map. Покрыты успешные пути, границы и ошибочные ситуации.
 */
@DisplayName("CartService — корзина покупателя")
class CartServiceTest {

    private ProductRepo products;
    private CartService cart;
    private HttpSession session;
    private Category flowers;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepo.class);
        cart = new CartService(products);
        session = TestFixtures.fakeSession();
        flowers = TestFixtures.category(1L, "Цветы");
    }

    private Product roses(int stock) {
        return TestFixtures.product(1L, "Розы", "10.00", stock, true, flowers);
    }

    private Product tulips() {
        return TestFixtures.product(2L, "Тюльпаны", "25.50", 10, true, flowers);
    }

    @Nested
    @DisplayName("Добавление товара в корзину")
    class Add {

        @Test
        @DisplayName("товар попадает в корзину с нужным количеством")
        void добавлениеТовараКладётЕгоВКорзину() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));

            cart.add(session, 1L, 2);

            assertThat(cart.raw(session)).containsEntry(1L, 2);
        }

        @Test
        @DisplayName("повторное добавление суммирует количество")
        void повторноеДобавлениеСуммируетКоличество() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));

            cart.add(session, 1L, 2);
            cart.add(session, 1L, 3);

            assertThat(cart.raw(session)).containsEntry(1L, 5);
        }

        @Test
        @DisplayName("товар, которого больше нет на складе, добавить нельзя")
        void добавлениеБольшеОстаткаОтклоняется() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(3)));

            ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 1L, 4));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
            assertThat(error).hasMessageContaining("Недостаточно на складе");
            assertThat(cart.raw(session)).isEmpty();
        }

        @Test
        @DisplayName("превышение остатка с учётом уже лежащего в корзине отклоняется")
        void добавлениеСПревышениемУжеДобавленногоОтклоняется() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            cart.add(session, 1L, 4);

            ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 1L, 2));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
            assertThat(cart.raw(session)).containsEntry(1L, 4);
        }

        @Test
        @DisplayName("несуществующий товар добавить нельзя")
        void добавлениеНесуществующегоТовараОтклоняется() {
            when(products.findById(99L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 99L, 1));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
            assertThat(error).hasMessage(ApiException.PRODUCT_NOT_FOUND);
        }

        @Test
        @DisplayName("скрытый товар покупателю не показываем")
        void добавлениеСкрытогоТовараОтклоняется() {
            Product hidden = TestFixtures.product(3L, "Скрытый", "5.00", 10, false, flowers);
            when(products.findById(3L)).thenReturn(Optional.of(hidden));

            ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 3L, 1));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
        }

        @Test
        @DisplayName("нулевое количество отклоняется")
        void добавлениеНулевогоКоличестваОтклоняется() {
            ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 1L, 0));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Некорректное количество");
        }

        @Test
        @DisplayName("отрицательное количество отклоняется")
        void добавлениеОтрицательногоКоличестваОтклоняется() {
            ApiException error = (ApiException) catchThrowable(() -> cart.add(session, 1L, -2));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("Расчёт суммы заказа")
    class Sums {

        @Test
        @DisplayName("сумма одной позиции = цена × количество")
        void суммаПоОднойПозиции() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            when(products.findAllById(any())).thenReturn(List.of(roses(5)));
            cart.add(session, 1L, 3);

            CartService.View view = cart.viewAndNormalize(session);

            assertThat(view.items()).hasSize(1);
            assertThat(view.items().get(0).sum()).isEqualByComparingTo("30.00");
            assertThat(view.total()).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("сумма нескольких позиций складывается")
        void суммаПоНесколькимПозициям() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            when(products.findById(2L)).thenReturn(Optional.of(tulips()));
            when(products.findAllById(any())).thenReturn(List.of(roses(5), tulips()));
            cart.add(session, 1L, 2);
            cart.add(session, 2L, 2);

            CartService.View view = cart.viewAndNormalize(session);

            assertThat(view.items()).hasSize(2);
            // 2 × 10.00 + 2 × 25.50 = 71.00
            assertThat(view.total()).isEqualByComparingTo("71.00");
        }

        @Test
        @DisplayName("пустая корзина даёт нулевую сумму")
        void пустаяКорзинаДаётНоль() {
            CartService.View view = cart.viewAndNormalize(session);

            assertThat(view.items()).isEmpty();
            assertThat(view.total()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("позиция содержит цену, количество, остаток и сумму")
        void позицияСодержитВсеДанные() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            when(products.findAllById(any())).thenReturn(List.of(roses(5)));
            cart.add(session, 1L, 2);

            CartService.Item item = cart.viewAndNormalize(session).items().get(0);

            assertThat(item.productId()).isEqualTo(1L);
            assertThat(item.name()).isEqualTo("Розы");
            assertThat(item.price()).isEqualByComparingTo("10.00");
            assertThat(item.qty()).isEqualTo(2);
            assertThat(item.stock()).isEqualTo(5);
            assertThat(item.sum()).isEqualByComparingTo("20.00");
        }
    }

    @Nested
    @DisplayName("Просмотр корзины приводит её в порядок")
    class Normalize {

        @Test
        @DisplayName("товар, исчезнувший из каталога, убирается из корзины")
        void пропавшийТоварУбирается() {
            when(products.findAllById(any())).thenReturn(List.of());
            cart.raw(session).put(1L, 1);

            CartService.View view = cart.viewAndNormalize(session);

            assertThat(view.items()).isEmpty();
            assertThat(cart.raw(session)).isEmpty();
        }

        @Test
        @DisplayName("скрытый товар убирается из корзины")
        void скрытыйТоварУбирается() {
            Product hidden = TestFixtures.product(1L, "Скрытый", "5.00", 10, false, flowers);
            when(products.findAllById(any())).thenReturn(List.of(hidden));
            cart.raw(session).put(1L, 1);

            assertThat(cart.viewAndNormalize(session).items()).isEmpty();
        }

        @Test
        @DisplayName("товар, закончившийся на складе, убирается из корзины")
        void закончившийсяТоварУбирается() {
            when(products.findAllById(any())).thenReturn(List.of(roses(0)));
            cart.raw(session).put(1L, 1);

            assertThat(cart.viewAndNormalize(session).items()).isEmpty();
        }

        @Test
        @DisplayName("количество урезается до остатка на складе")
        void количествоУрезаетсяДоОстатка() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            cart.add(session, 1L, 5);
            when(products.findAllById(any())).thenReturn(List.of(roses(2)));

            CartService.View view = cart.viewAndNormalize(session);

            assertThat(view.items().get(0).qty()).isEqualTo(2);
            assertThat(view.total()).isEqualByComparingTo("20.00");
        }
    }

    @Nested
    @DisplayName("Изменение количества")
    class SetQty {

        @Test
        @DisplayName("количество заменяется на новое значение")
        void количествоЗаменяется() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            cart.add(session, 1L, 2);

            cart.setQty(session, 1L, 4);

            assertThat(cart.raw(session)).containsEntry(1L, 4);
        }

        @Test
        @DisplayName("количество больше остатка отклоняется")
        void количествоБольшеОстаткаОтклоняется() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));

            ApiException error = (ApiException) catchThrowable(() -> cart.setQty(session, 1L, 6));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
        }

        @Test
        @DisplayName("нулевое количество отклоняется")
        void нулевоеКоличествоОтклоняется() {
            ApiException error = (ApiException) catchThrowable(() -> cart.setQty(session, 1L, 0));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
        }

        @Test
        @DisplayName("несуществующий товар изменить нельзя")
        void несуществующийТоварОтклоняется() {
            when(products.findById(99L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> cart.setQty(session, 99L, 1));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("Удаление и очистка корзины")
    class RemoveAndClear {

        @Test
        @DisplayName("товар убирается из корзины")
        void товарУбирается() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            cart.add(session, 1L, 2);

            cart.remove(session, 1L);

            assertThat(cart.raw(session)).doesNotContainKey(1L);
        }

        @Test
        @DisplayName("удаление отсутствующего товара не ломает корзину")
        void удалениеОтсутствующегоТовараНеЛомаетКорзину() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            cart.add(session, 1L, 2);

            cart.remove(session, 42L);

            assertThat(cart.raw(session)).containsEntry(1L, 2);
        }

        @Test
        @DisplayName("очистка корзины убирает все позиции")
        void очисткаУбираетВсеПозиции() {
            when(products.findById(1L)).thenReturn(Optional.of(roses(5)));
            when(products.findById(2L)).thenReturn(Optional.of(tulips()));
            cart.add(session, 1L, 2);
            cart.add(session, 2L, 1);

            cart.clear(session);

            assertThat(cart.raw(session)).isEmpty();
        }
    }

    @Test
    @DisplayName("сырая корзина создаётся в сессии при первом обращении")
    void сыраяКорзинаСоздаётсяВСессии() {
        assertThat(cart.raw(session)).isEmpty();
        verify(session).setAttribute(eq("cart"), any());
    }
}
