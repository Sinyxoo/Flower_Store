package shop.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import shop.exception.ApiException;
import shop.model.Order;
import shop.model.OrderStatus;
import shop.model.Role;
import shop.model.User;
import shop.service.AuthService;
import shop.service.CartService;
import shop.service.CatalogService;
import shop.service.OrderService;
import shop.support.TestFixtures;

/**
 * Тесты JSON-API: только контроллер и заглушки сервисов.
 * Проверяем маршруты, коды ответов и передачу параметров в сервисы.
 */
@WebMvcTest(ApiController.class)
@DisplayName("ApiController — данные для страниц")
class ApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService auth;
    @MockBean
    private CatalogService catalog;
    @MockBean
    private CartService cart;
    @MockBean
    private OrderService orders;

    private User user() {
        return TestFixtures.user(7L, "ivan@mail.local", Role.USER);
    }

    private User admin() {
        return TestFixtures.user(1L, "admin@flower.local", Role.ADMIN);
    }

    @Test
    @DisplayName("гостю отвечают пустым кодом 204")
    void гостюОтвечаютПустымКодом204() throws Exception {
        when(auth.current(any())).thenReturn(null);

        mvc.perform(get("/api/auth/me")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("вошедшему пользователю отдают его данные")
    void вошедшемуПользователюОтдаютЕгоДанные() throws Exception {
        when(auth.current(any())).thenReturn(user());

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ivan@mail.local"))
                .andExpect(jsonPath("$.name").value("Пользователь 7"));
    }

    @Test
    @DisplayName("пароль в ответе не показывается")
    void парольВОтветеНеПоказывается() throws Exception {
        when(auth.current(any())).thenReturn(user());

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("список категорий берётся из каталога")
    void списокКатегорийБерётсяИзКаталога() throws Exception {
        when(catalog.allCategories()).thenReturn(List.of(TestFixtures.category(1L, "Цветы")));

        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Цветы"));
    }

    @Test
    @DisplayName("фильтр и сортировка каталога передаются в сервис")
    void фильтрИСортировкаПередаютсяВСервис() throws Exception {
        when(catalog.visibleProducts(any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/products").param("category", "2").param("sort", "price_desc"))
                .andExpect(status().isOk());

        verify(catalog).visibleProducts(eq(2L), eq("price_desc"));
    }

    @Test
    @DisplayName("карточка товара отдаётся покупателю")
    void карточкаТовараОтдаётся() throws Exception {
        when(catalog.visibleProduct(1L))
                .thenReturn(TestFixtures.product(1L, "Розы", "10.00", 5, true, TestFixtures.category(1L, "Цветы")));

        mvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Розы"))
                .andExpect(jsonPath("$.categoryName").value("Цветы"));
    }

    @Test
    @DisplayName("несуществующий товар даёт ошибку 404 в JSON")
    void несуществующийТоварДаётОшибку404() throws Exception {
        when(catalog.visibleProduct(404L)).thenThrow(ApiException.notFound(ApiException.PRODUCT_NOT_FOUND));

        mvc.perform(get("/api/products/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ApiException.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("корзина отдаётся с позициями и суммой")
    void корзинаОтдаётся() throws Exception {
        CartService.Item item = new CartService.Item(1L, "Розы", new BigDecimal("10.00"), 2, 5, null, new BigDecimal("20.00"));
        when(cart.viewAndNormalize(any()))
                .thenReturn(new CartService.View(List.of(item), new BigDecimal("20.00")));

        mvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Розы"))
                .andExpect(jsonPath("$.total").value(20.00));
    }

    @Test
    @DisplayName("список своих заказов без входа даёт ошибку 401")
    void списокСвоихЗаказовБезВходаДаёт401() throws Exception {
        when(auth.require(any())).thenThrow(ApiException.unauthorized("Требуется авторизация"));

        mvc.perform(get("/api/me/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Требуется авторизация"));
    }

    @Test
    @DisplayName("список своих заказов возвращается из сервиса")
    void списокСвоихЗаказовВозвращается() throws Exception {
        when(auth.require(any())).thenReturn(user());
        Order order = TestFixtures.order(5L, OrderStatus.NEW);
        when(orders.ordersOf(any())).thenReturn(List.of(order));

        mvc.perform(get("/api/me/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].status").value("new"));
    }

    @Test
    @DisplayName("админские данные недоступны обычному пользователю")
    void админскиеДанныеНедоступныОбычномуПользователю() throws Exception {
        when(auth.admin(any())).thenThrow(ApiException.forbidden("Доступ запрещён"));

        mvc.perform(get("/api/admin/products"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Доступ запрещён"));
    }

    @Test
    @DisplayName("администратору отдаётся список всех товаров")
    void администраторуОтдаётсяСписокВсехТоваров() throws Exception {
        when(auth.admin(any())).thenReturn(admin());
        when(catalog.allProducts())
                .thenReturn(List.of(TestFixtures.product(1L, "Розы", "10.00", 5, true, TestFixtures.category(1L, "Цветы"))));

        mvc.perform(get("/api/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("администратору отдаётся список всех заказов")
    void администраторуОтдаётсяСписокВсехЗаказов() throws Exception {
        when(auth.admin(any())).thenReturn(admin());
        when(orders.allOrders()).thenReturn(List.of(TestFixtures.order(5L, OrderStatus.DELIVERED)));

        mvc.perform(get("/api/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));
    }

    @Test
    @DisplayName("администратору отдаётся список пользователей")
    void администраторуОтдаётсяСписокПользователей() throws Exception {
        when(auth.admin(any())).thenReturn(admin());
        when(auth.allUsers()).thenReturn(List.of(user()));

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ivan@mail.local"));
    }
}
