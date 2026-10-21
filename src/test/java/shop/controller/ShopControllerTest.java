package shop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import shop.exception.ApiException;
import shop.model.Order;
import shop.model.OrderStatus;
import shop.model.Role;
import shop.model.User;
import shop.service.AuthService;
import shop.service.CartService;
import shop.service.OrderService;
import shop.support.TestFixtures;

/**
 * Тесты действий покупателя из HTML-форм.
 * Сервисы — заглушки: проверяем маршруты, редиректы и тексты сообщений.
 */
@WebMvcTest(ShopController.class)
@DisplayName("ShopController — корзина, заказ, вход, профиль")
class ShopControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService auth;
    @MockBean
    private CartService cart;
    @MockBean
    private OrderService orders;

    /** Адрес редиректа, приведённый к читаемому виду (без %-кодирования). */
    private static String redirectTo(MvcResult result) {
        return URLDecoder.decode(result.getResponse().getRedirectedUrl(), StandardCharsets.UTF_8);
    }

    /** Адрес, который ErrorHandler отдал в заголовке Location. */
    private static String locationOf(MvcResult result) {
        return URLDecoder.decode(result.getResponse().getHeader("Location"), StandardCharsets.UTF_8);
    }

    private User user() {
        return TestFixtures.user(7L, "ivan@mail.local", Role.USER);
    }

    // =====================================================================
    @Test
    @DisplayName("товар добавляется в корзину")
    void товарДобавляетсяВКорзину() throws Exception {
        MvcResult result = mvc.perform(post("/cart/add").param("productId", "1").param("qty", "2"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/cart.html?msg=Товар добавлен в корзину");
        verify(cart).add(any(), eq(1L), eq(2));
    }

    @Test
    @DisplayName("при ошибке корзина показывает текст ошибки")
    void приОшибкеКорзинаПоказываетТекстОшибки() throws Exception {
        doThrow(ApiException.conflict("Недостаточно на складе: доступно 1 шт."))
                .when(cart).add(any(), any(), anyInt());

        MvcResult result = mvc.perform(post("/cart/add").param("productId", "1").param("qty", "5"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/cart.html?error=Недостаточно на складе: доступно 1 шт.");
    }

    @Test
    @DisplayName("количество в корзине меняется")
    void количествоВКорзинеМеняется() throws Exception {
        MvcResult result = mvc.perform(post("/cart/update").param("productId", "1").param("qty", "3"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/cart.html?msg=Корзина обновлена");
        verify(cart).setQty(any(), eq(1L), eq(3));
    }

    @Test
    @DisplayName("товар убирается из корзины")
    void товарУбираетсяИзКорзины() throws Exception {
        MvcResult result = mvc.perform(post("/cart/remove").param("productId", "1"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/cart.html?msg=Товар убран из корзины");
        verify(cart).remove(any(), eq(1L));
    }

    // =====================================================================
    @Test
    @DisplayName("оформленный заказ ведёт на страницу успеха с его номером")
    void оформленныйЗаказВедётНаСтраницуУспеха() throws Exception {
        when(orders.create(any(), any(), any())).thenReturn(TestFixtures.order(5L, OrderStatus.NEW));

        MvcResult result = mvc.perform(post("/checkout")
                        .param("customerName", "Иван")
                        .param("email", "ivan@mail.local")
                        .param("phone", "")
                        .param("address", "Минск, ул. Тестовая, 1")
                        .param("payment", "cash"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/order-success.html?id=5");
        verify(cart).clear(any());
    }

    @Test
    @DisplayName("ошибка оформления возвращает на страницу оформления")
    void ошибкаОформленияВозвращаетНаСтраницуОформления() throws Exception {
        when(orders.create(any(), any(), any())).thenThrow(ApiException.conflict("Недостаточно на складе"));

        MvcResult result = mvc.perform(post("/checkout")
                        .param("customerName", "Иван")
                        .param("email", "ivan@mail.local")
                        .param("phone", "")
                        .param("address", "Минск")
                        .param("payment", "cash"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/checkout.html?error=Недостаточно на складе");
        verify(cart, org.mockito.Mockito.never()).clear(any());
    }

    // =====================================================================
    @Test
    @DisplayName("удачный вход ведёт в личный кабинет")
    void удачныйВходВедётВЛичныйКабинет() throws Exception {
        MvcResult result = mvc.perform(post("/login").param("email", "ivan@mail.local").param("password", "secret1"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/account.html?msg=Вы вошли");
    }

    @Test
    @DisplayName("неудачный вход возвращает на страницу входа")
    void неудачныйВходВозвращаетНаСтраницуВхода() throws Exception {
        doThrow(ApiException.unauthorized("Неверный e-mail или пароль"))
                .when(auth).login(any(), any(), any());

        MvcResult result = mvc.perform(post("/login").param("email", "ivan@mail.local").param("password", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/login.html?error=Неверный e-mail или пароль");
    }

    @Test
    @DisplayName("удачная регистрация ведёт в личный кабинет")
    void удачнаяРегистрацияВедётВЛичныйКабинет() throws Exception {
        MvcResult result = mvc.perform(post("/register")
                        .param("name", "Иван")
                        .param("email", "ivan@mail.local")
                        .param("password", "secret1")
                        .param("password2", "secret1"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/account.html?msg=Аккаунт создан");
    }

    @Test
    @DisplayName("несовпадающие пароли возвращают на страницу регистрации")
    void несовпадающиеПаролиВозвращаютНаСтраницуРегистрации() throws Exception {
        MvcResult result = mvc.perform(post("/register")
                        .param("name", "Иван")
                        .param("email", "ivan@mail.local")
                        .param("password", "secret1")
                        .param("password2", "secret2"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/register.html?error=Пароли не совпадают");
    }

    @Test
    @DisplayName("выход ведёт на главную страницу")
    void выходВедётНаГлавную() throws Exception {
        MvcResult result = mvc.perform(post("/logout"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/index.html");
        verify(auth).logout(any());
    }

    // =====================================================================
    @Test
    @DisplayName("данные профиля сохраняются")
    void данныеПрофиляСохраняются() throws Exception {
        when(auth.require(any())).thenReturn(user());

        MvcResult result = mvc.perform(post("/account/profile")
                        .param("name", "Иван")
                        .param("phone", "+375291112233")
                        .param("address", "Минск"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/account.html?msg=Данные сохранены");
    }

    @Test
    @DisplayName("без входа сохранение профиля отправляет на страницу входа")
    void безВходаСохранениеПрофиляОтправляетНаСтраницуВхода() throws Exception {
        when(auth.require(any())).thenThrow(ApiException.unauthorized("Требуется авторизация"));

        MvcResult result = mvc.perform(post("/account/profile").param("name", "Иван"))
                .andExpect(status().isSeeOther())
                .andReturn();

        assertThat(locationOf(result)).isEqualTo("/login.html?error=Требуется авторизация");
    }

    @Test
    @DisplayName("повтор заказа возвращает товары в корзину")
    void повторЗаказаВозвращаетТоварыВКорзину() throws Exception {
        User user = user();
        Order order = TestFixtures.order(5L, OrderStatus.DELIVERED);
        TestFixtures.item(order, 1L, "Розы", 2, "10.00");
        when(auth.require(any())).thenReturn(user);
        when(orders.findOne(5L, 7L)).thenReturn(order);

        MvcResult result = mvc.perform(post("/account/repeat/5"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/cart.html");
        verify(cart).add(any(), eq(1L), eq(2));
    }

    @Test
    @DisplayName("повтор чужого заказа даёт ошибку 404")
    void повторЧужогоЗаказаДаётОшибку404() throws Exception {
        when(auth.require(any())).thenReturn(user());
        when(orders.findOne(5L, 7L)).thenThrow(ApiException.notFound(ApiException.ORDER_NOT_FOUND));

        MvcResult result = mvc.perform(post("/account/repeat/5"))
                .andExpect(status().isSeeOther())
                .andReturn();

        assertThat(locationOf(result)).isEqualTo("/404.html?error=Заказ не найден");
    }

    @Test
    @DisplayName("повтор заказа сообщает о товаре, которого больше нет")
    void повторЗаказаСообщаетОТовареКоторогоБольшеНет() throws Exception {
        User user = user();
        Order order = TestFixtures.order(5L, OrderStatus.DELIVERED);
        TestFixtures.item(order, 1L, "Розы", 2, "10.00");
        when(auth.require(any())).thenReturn(user);
        when(orders.findOne(5L, 7L)).thenReturn(order);
        doThrow(ApiException.notFound(ApiException.PRODUCT_NOT_FOUND)).when(cart).add(any(), any(), anyInt());

        MvcResult result = mvc.perform(post("/account/repeat/5"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/cart.html?error=Товар не найден");
    }

    // =====================================================================
    @Test
    @DisplayName("пустое сообщение обратной связи не отправляется")
    void пустоеСообщениеОбратнойСвязиНеОтправляется() throws Exception {
        MvcResult result = mvc.perform(post("/contacts").param("name", "Иван").param("contact", "ivan@mail.local"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/contacts.html?error=Напишите сообщение");
    }

    @Test
    @DisplayName("сообщение обратной связи отправляется")
    void сообщениеОбратнойСвязиОтправляется() throws Exception {
        MvcResult result = mvc.perform(post("/contacts")
                        .param("name", "Иван")
                        .param("contact", "ivan@mail.local")
                        .param("message", "Когда доставят?"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/contacts.html?msg=Сообщение отправлено");
    }
}
