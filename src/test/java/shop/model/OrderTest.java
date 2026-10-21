package shop.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import shop.support.TestFixtures;

/** Заказ и список статусов, которые администратор может выбрать в форме. */
@DisplayName("Order — заказ и доступные статусы")
class OrderTest {

    @Test
    @DisplayName("для нового заказа предлагаются обработка и отмена")
    void дляНовогоЗаказаПредлагаютсяОбработкаИОтмена() {
        Order order = TestFixtures.order(1L, OrderStatus.NEW);

        assertThat(order.getNextStatuses()).containsExactly(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("для заказа в обработке предлагаются доставка и отмена")
    void дляЗаказаВОбработкеПредлагаютсяДоставкаИОтмена() {
        Order order = TestFixtures.order(1L, OrderStatus.PROCESSING);

        assertThat(order.getNextStatuses()).containsExactly(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("для доставленного заказа статусов нет")
    void дляДоставленногоЗаказаСтатусовНет() {
        Order order = TestFixtures.order(1L, OrderStatus.DELIVERED);

        assertThat(order.getNextStatuses()).isEmpty();
    }

    @Test
    @DisplayName("у заказа без статуса список статусов пуст")
    void уЗаказаБезСтатусаСписокСтатусовПуст() {
        Order order = TestFixtures.order(1L, OrderStatus.NEW);
        order.status = null;

        assertThat(order.getNextStatuses()).isEmpty();
    }

    @Test
    @DisplayName("новый заказ по умолчанию имеет статус «новый»")
    void новыйЗаказПоУмолчаниюИмеетСтатусНовый() {
        Order order = new Order();

        assertThat(order.status).isEqualTo(OrderStatus.NEW);
    }

    @Test
    @DisplayName("позиции заказа попадают в его список")
    void позицииЗаказаПопадаютВЕгоСписок() {
        Order order = TestFixtures.order(1L, OrderStatus.NEW);

        TestFixtures.item(order, 1L, "Розы", 2, "10.00");

        assertThat(order.items).hasSize(1);
        assertThat(order.items.get(0).name).isEqualTo("Розы");
        assertThat(order.items.get(0).order).isSameAs(order);
    }
}
