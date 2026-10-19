package shop;

import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {
    NEW, PROCESSING, DELIVERED, CANCELLED;

    /** В JSON — строчными: new, processing, delivered, cancelled. */
    @JsonValue
    public String json() { return name().toLowerCase(); }

    /** новый → в обработке → доставлен; отменить можно из «новый» и «в обработке». */
    public boolean canGo(OrderStatus to) {
        return switch (this) {
            case NEW -> to == PROCESSING || to == CANCELLED;
            case PROCESSING -> to == DELIVERED || to == CANCELLED;
            default -> false;
        };
    }
}
