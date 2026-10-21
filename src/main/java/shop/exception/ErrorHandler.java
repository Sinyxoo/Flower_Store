package shop.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Сюда попадают ошибки, которые контроллеры не поймали сами (нет входа, нет прав и т.п.). */
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handle(ApiException e, HttpServletRequest request) {
        // Запрос данных (/api/...) — отвечаем JSON
        if (request.getRequestURI().startsWith("/api/")) {
            return ResponseEntity.status(e.status).body(Map.of("message", message(e)));
        }
        // Запрос из формы — отправляем на нужную страницу, текст ошибки — в адресе
        String page = "/404.html";
        if (e.status == 401) page = "/login.html";
        if (e.status == 403) page = "/403.html";
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("Location", page + "?error=" + encode(message(e)))
                .build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.status(400).body(Map.of("message", "Некорректный запрос"));
    }

    /** Текст ошибки не должен быть пустым — иначе Map.of бросит исключение. */
    private static String message(ApiException e) {
        return e.getMessage() == null ? "Ошибка" : e.getMessage();
    }

    /** Текст ошибки попадает в адрес страницы — экранируем, чтобы не сломать URL. */
    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
