package shop.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Тесты обработчика ошибок: для запросов /api/... отдаём JSON,
 * для запросов из форм — редирект на нужную страницу с текстом ошибки в адресе.
 */
@DisplayName("ErrorHandler — единая обработка ошибок")
class ErrorHandlerTest {

    private final ErrorHandler handler = new ErrorHandler();

    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    private String decodedLocation(ResponseEntity<?> response) {
        return URLDecoder.decode(response.getHeaders().getFirst("Location"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("для /api/ отдаётся JSON с кодом ошибки")
    void дляApiОтдаётсяJsonСКодомОшибки() {
        ResponseEntity<?> response = handler.handle(ApiException.notFound("Товар не найден"), request("/api/products/1"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("message", "Товар не найден"));
    }

    @Test
    @DisplayName("для /api/ сохраняется исходный HTTP-код")
    void дляApiСохраняетсяИсходныйHttpКод() {
        ResponseEntity<?> response = handler.handle(ApiException.conflict("Недостаточно на складе"), request("/api/cart"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("для формы при ошибке 401 отправляем на страницу входа")
    void дляФормыПриОшибке401ОтправляемНаСтраницуВхода() {
        ResponseEntity<?> response = handler.handle(ApiException.unauthorized("Требуется авторизация"), request("/account/profile"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(decodedLocation(response)).isEqualTo("/login.html?error=Требуется авторизация");
    }

    @Test
    @DisplayName("для формы при ошибке 403 отправляем на страницу 403")
    void дляФормыПриОшибке403ОтправляемНаСтраницу403() {
        ResponseEntity<?> response = handler.handle(ApiException.forbidden("Доступ запрещён"), request("/admin/products"));

        assertThat(decodedLocation(response)).isEqualTo("/403.html?error=Доступ запрещён");
    }

    @Test
    @DisplayName("для формы при прочих ошибках отправляем на страницу 404")
    void дляФормыПриПрочихОшибкахОтправляемНаСтраницу404() {
        ResponseEntity<?> response = handler.handle(ApiException.badRequest("Укажите имя"), request("/checkout"));

        assertThat(decodedLocation(response)).isEqualTo("/404.html?error=Укажите имя");
    }

    @Test
    @DisplayName("текст ошибки экранируется, чтобы не сломать адрес")
    void текстОшибкиЭкранируется() {
        ResponseEntity<?> response = handler.handle(ApiException.badRequest("Цена & скидка = 100%"), request("/checkout"));

        String location = response.getHeaders().getFirst("Location");
        assertThat(location).doesNotContain(" ");
        assertThat(decodedLocation(response)).isEqualTo("/404.html?error=Цена & скидка = 100%");
    }

    @Test
    @DisplayName("ошибка разбора JSON даёт ответ 400")
    void ошибкаРазбораJsonДаётОтвет400() {
        ResponseEntity<?> response = handler.badRequest(new HttpMessageNotReadableException("битый json"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("message", "Некорректный запрос"));
    }

    @Test
    @DisplayName("пустой текст ошибки заменяется на «Ошибка»")
    void пустойТекстОшибкиЗаменяетсяНаОшибка() {
        ResponseEntity<?> response = handler.handle(new ApiException(500, null), request("/api/products"));

        assertThat(response.getBody()).isEqualTo(java.util.Map.of("message", "Ошибка"));
    }
}
