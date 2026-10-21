package shop.exception;

/** Ошибка бизнес-логики с HTTP-кодом (400, 401, 403, 404, 409). */
public class ApiException extends RuntimeException {
    // Тексты ошибок, которые повторяются в разных местах.
    public static final String PRODUCT_NOT_FOUND = "Товар не найден";
    public static final String CATEGORY_NOT_FOUND = "Категория не найдена";
    public static final String ORDER_NOT_FOUND = "Заказ не найден";

    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** Если условие не выполнено — бросает ошибку 400 с сообщением. */
    public static void check(boolean ok, String message) {
        if (!ok) throw badRequest(message);
    }

    public static ApiException badRequest(String message) { return new ApiException(400, message); }
    public static ApiException unauthorized(String message) { return new ApiException(401, message); }
    public static ApiException forbidden(String message) { return new ApiException(403, message); }
    public static ApiException notFound(String message) { return new ApiException(404, message); }
    public static ApiException conflict(String message) { return new ApiException(409, message); }

    public static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
