package shop.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Правила переходов статуса заказа — самая важная «таблица» бизнес-логики. */
@DisplayName("OrderStatus — статусы заказа и переходы между ними")
class OrderStatusTest {

    @Test
    @DisplayName("новый заказ можно перевести в обработку или отменить")
    void изНовогоДоступныОбработкаИОтмена() {
        assertThat(OrderStatus.NEW.next()).containsExactly(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("заказ в обработке можно доставить или отменить")
    void изОбработкиДоступныДоставкаИОтмена() {
        assertThat(OrderStatus.PROCESSING.next()).containsExactly(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("доставленный заказ больше не меняется")
    void доставленныйЗаказБольшеНеМеняется() {
        assertThat(OrderStatus.DELIVERED.next()).isEmpty();
    }

    @Test
    @DisplayName("отменённый заказ больше не меняется")
    void отменённыйЗаказБольшеНеМеняется() {
        assertThat(OrderStatus.CANCELLED.next()).isEmpty();
    }

    @Test
    @DisplayName("переход «новый → обработка» разрешён")
    void переходНовыйВОбработкуРазрешён() {
        assertThat(OrderStatus.NEW.canGo(OrderStatus.PROCESSING)).isTrue();
    }

    @Test
    @DisplayName("переход «новый → доставлен» запрещён")
    void переходНовыйВДоставленЗапрещён() {
        assertThat(OrderStatus.NEW.canGo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("переход «доставлен → новый» запрещён")
    void переходДоставленВНовыйЗапрещён() {
        assertThat(OrderStatus.DELIVERED.canGo(OrderStatus.NEW)).isFalse();
    }

    @Test
    @DisplayName("переход «в обработке → новый» запрещён")
    void переходОбработкаВНовыйЗапрещён() {
        assertThat(OrderStatus.PROCESSING.canGo(OrderStatus.NEW)).isFalse();
    }

    @Test
    @DisplayName("переход «отменён → новый» запрещён")
    void переходОтменёнВНовыйЗапрещён() {
        assertThat(OrderStatus.CANCELLED.canGo(OrderStatus.NEW)).isFalse();
    }

    @Test
    @DisplayName("переход в самого себя запрещён")
    void переходВСамогоСебяЗапрещён() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canGo(status)).as("переход %s → %s", status, status).isFalse();
        }
    }

    @Test
    @DisplayName("в JSON статус пишется строчными буквами")
    void вJsonСтатусПишетсяСтрочными() {
        assertThat(OrderStatus.NEW.toJson()).isEqualTo("new");
        assertThat(OrderStatus.PROCESSING.toJson()).isEqualTo("processing");
        assertThat(OrderStatus.DELIVERED.toJson()).isEqualTo("delivered");
        assertThat(OrderStatus.CANCELLED.toJson()).isEqualTo("cancelled");
    }

    @Test
    @DisplayName("у статуса есть ожидаемое число следующих состояний")
    void уСтатусаЕстьОжидаемоеЧислоСледующихСостояний() {
        assertThat(List.of(OrderStatus.NEW.next().size(), OrderStatus.PROCESSING.next().size(),
                OrderStatus.DELIVERED.next().size(), OrderStatus.CANCELLED.next().size()))
                .containsExactly(2, 2, 0, 0);
    }
}
