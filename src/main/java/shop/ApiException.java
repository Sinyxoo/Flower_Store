package shop;

public class ApiException extends RuntimeException {
    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** Бросает 400, если условие не выполнено. */
    public static void check(boolean ok, String message) {
        if (!ok) throw new ApiException(400, message);
    }

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
