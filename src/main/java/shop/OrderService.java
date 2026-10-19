package shop;

import static shop.ApiException.blank;
import static shop.ApiException.check;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    public record OrderReq(String customerName, String email, String phone, String address, String payment) {}

    private final OrderRepo orders;
    private final ProductRepo products;

    public OrderService(OrderRepo orders, ProductRepo products) {
        this.orders = orders;
        this.products = products;
    }

    /** Заказ из содержимого корзины (productId → qty): проверка остатков, сумма, списание, статус «новый». */
    @Transactional
    public Order create(User user, Map<Long, Integer> cart, OrderReq r) {
        check(r != null && !blank(r.customerName()), "Укажите имя");
        check(!blank(r.email()) || !blank(r.phone()), "Укажите e-mail или телефон");
        check(!blank(r.address()), "Укажите адрес доставки");
        check(!cart.isEmpty(), "Корзина пуста");

        Order o = new Order();
        o.userId = user == null ? null : user.id;
        o.customerName = r.customerName().trim();
        o.email = blank(r.email()) ? "" : r.email().trim();
        o.phone = blank(r.phone()) ? "" : r.phone().trim();
        o.address = r.address().trim();
        o.payment = blank(r.payment()) ? "cash" : r.payment();
        o.status = OrderStatus.NEW;
        o.createdAt = LocalDate.now();

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : cart.entrySet()) {
            Product p = products.findById(e.getKey()).filter(x -> x.visible)
                    .orElseThrow(() -> new ApiException(404, "Товар из корзины больше не продаётся"));
            if (p.stock < e.getValue())
                throw new ApiException(409, "Недостаточно на складе: «" + p.name + "», доступно " + p.stock + " шт.");
            p.stock -= e.getValue();
            products.save(p);

            OrderItem it = new OrderItem();
            it.productId = p.id;
            it.name = p.name;
            it.qty = e.getValue();
            it.price = p.price;
            o.items.add(it);
            total = total.add(p.price.multiply(BigDecimal.valueOf(e.getValue())));
        }
        o.total = total;
        return orders.save(o);
    }

    public List<Order> mine(User u) {
        return orders.findByUserIdOrderByIdDesc(u.id);
    }

    public List<Order> all() {
        return orders.findAllByOrderByIdDesc();
    }

    @Transactional
    public Order setStatus(Long id, String status) {
        Order o = orders.findById(id).orElseThrow(() -> new ApiException(404, "Заказ не найден"));
        OrderStatus to;
        try {
            to = OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new ApiException(400, "Неизвестный статус");
        }
        if (!o.status.canGo(to)) throw new ApiException(409, "Недопустимый переход статуса");
        if (to == OrderStatus.CANCELLED) {          // при отмене возвращаем товар на склад
            for (OrderItem it : o.items)
                products.findById(it.productId).ifPresent(p -> { p.stock += it.qty; products.save(p); });
        }
        o.status = to;
        return orders.save(o);
    }
}
