package shop.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import shop.model.Category;
import shop.model.Order;
import shop.model.OrderItem;
import shop.model.OrderStatus;
import shop.model.Product;
import shop.model.Role;
import shop.model.User;

/**
 * Общая оснастка для модульных тестов: готовые сущности и «настоящая» сессия.
 * Ни базы, ни контейнера здесь нет — только обычные объекты и моки,
 * поэтому тесты сервисного слоя выполняются быстро и независимо друг от друга.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    // ---------- категории и товары ----------

    public static Category category(long id, String name) {
        Category category = new Category();
        category.id = id;
        category.name = name;
        return category;
    }

    public static Product product(long id, String name, String price, int stock, boolean visible, Category category) {
        Product product = new Product();
        product.id = id;
        product.name = name;
        product.price = new BigDecimal(price);
        product.stock = stock;
        product.visible = visible;
        product.category = category;
        return product;
    }

    // ---------- пользователи ----------

    public static User user(long id, String email, Role role) {
        User user = new User();
        user.id = id;
        user.name = "Пользователь " + id;
        user.email = email;
        user.passwordHash = "hash-of-" + email;
        user.role = role;
        return user;
    }

    // ---------- заказы ----------

    public static Order order(long id, OrderStatus status) {
        Order order = new Order();
        order.id = id;
        order.status = status;
        order.customerName = "Покупатель";
        order.email = "buyer@mail.local";
        order.phone = "+375291112233";
        order.address = "Минск, ул. Тестовая, 1";
        order.payment = "cash";
        order.total = BigDecimal.ZERO;
        return order;
    }

    /** Позиция заказа: сохраняет ссылку на заказ и добавляет себя в его список. */
    public static OrderItem item(Order order, long productId, String name, int qty, String price) {
        OrderItem item = new OrderItem();
        item.order = order;
        item.productId = productId;
        item.name = name;
        item.qty = qty;
        item.price = new BigDecimal(price);
        order.items.add(item);
        return item;
    }

    // ---------- сессия ----------

    /**
     * HttpSession, который хранит атрибуты в обычной Map.
     * Нужен там, где важно само содержимое сессии (корзина, id пользователя).
     */
    public static HttpSession fakeSession() {
        Map<String, Object> attributes = new HashMap<>();
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0, String.class)));
        doAnswer(call -> {
            attributes.put(call.getArgument(0, String.class), call.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        doAnswer(call -> {
            attributes.remove(call.getArgument(0, String.class));
            return null;
        }).when(session).removeAttribute(anyString());
        return session;
    }
}
