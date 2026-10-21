package shop.service;

import static shop.exception.ApiException.blank;
import static shop.exception.ApiException.check;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shop.exception.ApiException;
import shop.model.Order;
import shop.model.OrderItem;
import shop.model.OrderStatus;
import shop.model.Product;
import shop.model.User;
import shop.repository.OrderRepo;
import shop.repository.ProductRepo;

@Service
public class OrderService {
    /** Данные формы оформления заказа. */
    public record OrderData(String customerName, String email, String phone, String address, String payment) {}

    private final OrderRepo orders;
    private final ProductRepo products;

    public OrderService(OrderRepo orders, ProductRepo products) {
        this.orders = orders;
        this.products = products;
    }

    /**
     * Создаёт заказ из корзины (id товара → количество).
     * Проверяет остатки, считает сумму, списывает товар со склада, ставит статус «новый».
     * user == null — заказ гостя.
     */
    @Transactional
    public Order create(User user, Map<Long, Integer> cart, OrderData data) {
        check(!blank(data.customerName()), "Укажите имя");
        check(!blank(data.email()) || !blank(data.phone()), "Укажите e-mail или телефон");
        check(!blank(data.address()), "Укажите адрес доставки");
        check(!cart.isEmpty(), "Корзина пуста");

        Order order = new Order();
        order.userId = user == null ? null : user.id;
        order.customerName = data.customerName().trim();
        order.email = blank(data.email()) ? "" : data.email().trim();
        order.phone = blank(data.phone()) ? "" : data.phone().trim();
        order.address = data.address().trim();
        order.payment = blank(data.payment()) ? "cash" : data.payment();
        order.status = OrderStatus.NEW;
        order.createdAt = LocalDateTime.now();

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Product product = products.findById(entry.getKey()).filter(p -> p.visible)
                    .orElseThrow(() -> ApiException.notFound("Товар из корзины больше не продаётся"));
            int qty = entry.getValue();
            if (product.stock < qty)
                throw ApiException.conflict("Недостаточно на складе: «" + product.name + "», доступно " + product.stock + " шт.");

            // Товар уже загружен в этой транзакции — Hibernate сам сохранит новый остаток.
            product.stock = product.stock - qty;

            OrderItem item = new OrderItem();
            item.order = order;
            item.productId = product.id;
            item.name = product.name;
            item.qty = qty;
            item.price = product.price;
            order.items.add(item);

            total = total.add(product.price.multiply(BigDecimal.valueOf(qty)));
        }
        order.total = total;
        return orders.save(order);
    }

    public List<Order> ordersOf(User user) {
        return orders.findByUserIdOrderByIdDesc(user.id);
    }

    public List<Order> allOrders() {
        return orders.findAllByOrderByIdDesc();
    }

    /** Один заказ конкретного пользователя — для «повторить заказ». */
    public Order findOne(Long orderId, Long userId) {
        return orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> ApiException.notFound(ApiException.ORDER_NOT_FOUND));
    }

    /** Меняет статус по правилам OrderStatus.canGo. При отмене возвращает товар на склад. */
    @Transactional
    public Order changeStatus(Long orderId, String statusName) {
        Order order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound(ApiException.ORDER_NOT_FOUND));

        if (blank(statusName)) throw ApiException.badRequest("Неизвестный статус");
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(statusName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Неизвестный статус");
        }
        if (!order.status.canGo(newStatus)) throw ApiException.conflict("Недопустимый переход статуса");

        if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.items) {
                Product product = products.findById(item.productId).orElse(null);
                if (product != null) {
                    product.stock = product.stock + item.qty;
                }
            }
        }
        order.status = newStatus;
        return orders.save(order);
    }
}
