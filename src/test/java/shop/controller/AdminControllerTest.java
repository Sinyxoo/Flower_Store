package shop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import shop.exception.ApiException;
import shop.model.Role;
import shop.model.User;
import shop.service.AuthService;
import shop.service.CatalogService;
import shop.service.OrderService;
import shop.support.TestFixtures;

/**
 * Тесты действий администратора из HTML-форм.
 * Отдельно проверяем, что гость и обычный пользователь к админке не допускаются.
 */
@WebMvcTest(AdminController.class)
@DisplayName("AdminController — управление каталогом и заказами")
class AdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService auth;
    @MockBean
    private CatalogService catalog;
    @MockBean
    private OrderService orders;

    @BeforeEach
    void setUp() {
        when(auth.admin(any())).thenReturn(TestFixtures.user(1L, "admin@flower.local", Role.ADMIN));
    }

    private static String redirectTo(MvcResult result) {
        return URLDecoder.decode(result.getResponse().getRedirectedUrl(), StandardCharsets.UTF_8);
    }

    private static String locationOf(MvcResult result) {
        return URLDecoder.decode(result.getResponse().getHeader("Location"), StandardCharsets.UTF_8);
    }

    // =====================================================================
    @Test
    @DisplayName("сохранённый товар возвращает на страницу списка")
    void сохранённыйТоварВозвращаетНаСтраницуСписка() throws Exception {
        MvcResult result = mvc.perform(post("/admin/products/save")
                        .param("name", "Розы")
                        .param("categoryId", "1")
                        .param("price", "10.00")
                        .param("stock", "5")
                        .param("visible", "on"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/admin/products.html?msg=Товар сохранён");
    }

    @Test
    @DisplayName("при ошибке товар возвращается на форму вместе с введёнными данными")
    void приОшибкеТоварВозвращаетсяНаФорму() throws Exception {
        doThrow(ApiException.badRequest("Цена не может быть отрицательной"))
                .when(catalog).saveProduct(any(), any());

        MvcResult result = mvc.perform(post("/admin/products/save")
                        .param("id", "3")
                        .param("name", "Розы")
                        .param("categoryId", "1")
                        .param("price", "-5")
                        .param("stock", "5"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String url = redirectTo(result);
        assertThat(url).startsWith("/admin/product-form.html?error=");
        assertThat(url).contains("name=Розы");
        assertThat(url).contains("price=-5");
        assertThat(url).contains("stock=5");
        assertThat(url).contains("id=3");
    }

    @Test
    @DisplayName("видимость товара переключается")
    void видимостьТовараПереключается() throws Exception {
        MvcResult result = mvc.perform(post("/admin/products/1/toggle"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/admin/products.html?msg=Товар обновлён");
        verify(catalog).toggleVisible(1L);
    }

    @Test
    @DisplayName("товар удаляется")
    void товарУдаляется() throws Exception {
        MvcResult result = mvc.perform(post("/admin/products/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/admin/products.html?msg=Товар удалён");
        verify(catalog).deleteProduct(1L);
    }

    // =====================================================================
    @Test
    @DisplayName("категория добавляется")
    void категорияДобавляется() throws Exception {
        MvcResult result = mvc.perform(post("/admin/categories").param("name", "Букеты"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/admin/categories.html?msg=Категория добавлена");
        verify(catalog).saveCategory(any(), eq("Букеты"));
    }

    @Test
    @DisplayName("категория переименовывается")
    void категорияПереименовывается() throws Exception {
        MvcResult result = mvc.perform(post("/admin/categories/1").param("name", "Цветы"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/admin/categories.html?msg=Категория переименована");
        verify(catalog).saveCategory(eq(1L), eq("Цветы"));
    }

    @Test
    @DisplayName("категория удаляется")
    void категорияУдаляется() throws Exception {
        MvcResult result = mvc.perform(post("/admin/categories/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/admin/categories.html?msg=Категория удалена");
        verify(catalog).deleteCategory(1L);
    }

    // =====================================================================
    @Test
    @DisplayName("статус заказа меняется")
    void статусЗаказаМеняется() throws Exception {
        MvcResult result = mvc.perform(post("/admin/orders/5/status").param("status", "processing"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/admin/orders.html?msg=Статус обновлён");
        verify(orders).changeStatus(5L, "processing");
    }

    @Test
    @DisplayName("недопустимая смена статуса показывает ошибку")
    void недопустимаяСменаСтатусаПоказываетОшибку() throws Exception {
        doThrow(ApiException.conflict("Недопустимый переход статуса"))
                .when(orders).changeStatus(any(), any());

        MvcResult result = mvc.perform(post("/admin/orders/5/status").param("status", "new"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(redirectTo(result)).isEqualTo("/admin/orders.html?error=Недопустимый переход статуса");
    }

    // =====================================================================
    @Test
    @DisplayName("гость отправляется на страницу входа")
    void гостьОтправляетсяНаСтраницуВхода() throws Exception {
        when(auth.admin(any())).thenThrow(ApiException.unauthorized("Требуется авторизация"));

        MvcResult result = mvc.perform(post("/admin/products/1/toggle"))
                .andExpect(status().isSeeOther())
                .andReturn();

        assertThat(locationOf(result)).isEqualTo("/login.html?error=Требуется авторизация");
    }

    @Test
    @DisplayName("обычный пользователь отправляется на страницу 403")
    void обычныйПользовательОтправляетсяНаСтраницу403() throws Exception {
        when(auth.admin(any())).thenThrow(ApiException.forbidden("Доступ запрещён"));

        MvcResult result = mvc.perform(post("/admin/products/1/toggle"))
                .andExpect(status().isSeeOther())
                .andReturn();

        assertThat(locationOf(result)).isEqualTo("/403.html?error=Доступ запрещён");
    }
}
