package shop.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

public enum OrderStatus {
    NEW, PROCESSING, DELIVERED, CANCELLED;

    /** В JSON статус уходит строчными буквами: new, processing, delivered, cancelled */
    @JsonValue
    public String toJson() { return name().toLowerCase(); }

    /** Статусы, в которые заказ ещё можно перевести из текущего. */
    public List<OrderStatus> next() {
        return switch (this) {
            case NEW -> List.of(PROCESSING, CANCELLED);
            case PROCESSING -> List.of(DELIVERED, CANCELLED);
            default -> List.of();   // «доставлен» и «отменён» менять нельзя
        };
    }

    /** Можно ли перейти из текущего статуса в статус «to» */
    public boolean canGo(OrderStatus to) {
        return next().contains(to);
    }
}
