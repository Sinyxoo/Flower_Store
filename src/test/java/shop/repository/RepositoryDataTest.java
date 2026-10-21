package shop.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import shop.model.Category;
import shop.model.Order;
import shop.model.OrderItem;
import shop.model.OrderStatus;
import shop.model.Product;
import shop.model.Role;
import shop.model.User;

/**
 * Тесты слоя доступа к данным на встроенной базе H2 (режим PostgreSQL).
 * Здесь проверяются сами запросы репозиториев: производные имена методов,
 * @EntityGraph и связи между таблицами.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Репозитории — работа с базой данных")
class RepositoryDataTest {

    @Autowired
    private CategoryRepo categories;
    @Autowired
    private ProductRepo products;
    @Autowired
    private UserRepo users;
    @Autowired
    private OrderRepo orders;

    private Category flowers;
    private Product roses;
    private Product hidden;

    @BeforeEach
    void setUp() {
        flowers = new Category();
        flowers.name = "Цветы";
        flowers = categories.save(flowers);

        roses = new Product();
        roses.name = "Розы";
        roses.category = flowers;
        roses.price = new BigDecimal("10.00");
        roses.stock = 5;
        roses.visible = true;
        roses = products.save(roses);

        hidden = new Product();
        hidden.name = "Скрытый";
        hidden.category = flowers;
        hidden.price = new BigDecimal("20.00");
        hidden.stock = 1;
        hidden.visible = false;
        hidden = products.save(hidden);
    }

    private User user(String email, Role role) {
        User user = new User();
        user.name = "Пользователь";
        user.email = email;
        user.passwordHash = "hash";
        user.role = role;
        return users.save(user);
    }

    private Order order(User owner, OrderStatus status, int items) {
        Order order = new Order();
        order.userId = owner == null ? null : owner.id;
        order.customerName = "Покупатель";
        order.email = "buyer@mail.local";
        order.phone = "+375291112233";
        order.address = "Минск";
        order.payment = "cash";
        order.status = status;
        order.total = new BigDecimal("30.00");
        order.createdAt = LocalDateTime.now();
        for (int i = 0; i < items; i++) {
            OrderItem item = new OrderItem();
            item.order = order;
            item.productId = roses.id;
            item.name = "Розы";
            item.qty = 2;
            item.price = new BigDecimal("15.00");
            order.items.add(item);
        }
        return orders.save(order);
    }

    // ---------- категории и товары ----------

    @Test
    @DisplayName("товар сохраняется и находится по id")
    void товарСохраняетсяИНаходитсяПоId() {
        assertThat(products.findById(roses.id)).hasValueSatisfying(p -> {
            assertThat(p.name).isEqualTo("Розы");
            assertThat(p.category.name).isEqualTo("Цветы");
            assertThat(p.price).isEqualByComparingTo("10.00");
        });
    }

    @Test
    @DisplayName("видимые товары не включают скрытые")
    void видимыеТоварыНеВключаютСкрытые() {
        List<Product> visible = products.findByVisibleTrue();

        assertThat(visible).extracting(p -> p.name).containsExactly("Розы");
    }

    @Test
    @DisplayName("наличие товаров в категории определяется верно")
    void наличиеТоваровВКатегорииОпределяетсяВерно() {
        assertThat(products.existsByCategory_Id(flowers.id)).isTrue();

        Category empty = new Category();
        empty.name = "Пустая";
        empty = categories.save(empty);

        assertThat(products.existsByCategory_Id(empty.id)).isFalse();
    }

    @Test
    @DisplayName("категория удаляется, когда в ней нет товаров")
    void категорияУдаляетсяКогдаВнейНетТоваров() {
        Category empty = new Category();
        empty.name = "Пустая";
        empty = categories.save(empty);

        categories.delete(empty);

        assertThat(categories.findById(empty.id)).isEmpty();
    }

    // ---------- пользователи ----------

    @Test
    @DisplayName("пользователь находится по e-mail без учёта регистра")
    void пользовательНаходитсяПоEmailБезУчётаРегистра() {
        user("ivan@mail.local", Role.USER);

        assertThat(users.findByEmailIgnoreCase("IVAN@MAIL.LOCAL")).isPresent();
        assertThat(users.findByEmailIgnoreCase("ivan@mail.local")).isPresent();
    }

    @Test
    @DisplayName("проверка занятости e-mail нечувствительна к регистру")
    void проверкаЗанятостиEmailНечувствительнаКРегистру() {
        user("ivan@mail.local", Role.USER);

        assertThat(users.existsByEmailIgnoreCase("Ivan@Mail.Local")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("other@mail.local")).isFalse();
    }

    // ---------- заказы ----------

    @Test
    @DisplayName("заказы пользователя возвращаются по убыванию id")
    void заказыПользователяВозвращаютсяПоУбываниюId() {
        User owner = user("ivan@mail.local", Role.USER);
        Order first = order(owner, OrderStatus.NEW, 1);
        Order second = order(owner, OrderStatus.DELIVERED, 1);

        List<Order> found = orders.findByUserIdOrderByIdDesc(owner.id);

        assertThat(found).extracting(o -> o.id).containsExactly(second.id, first.id);
    }

    @Test
    @DisplayName("заказы разных пользователей не смешиваются")
    void заказыРазныхПользователейНеСмешиваются() {
        User first = user("first@mail.local", Role.USER);
        User second = user("second@mail.local", Role.USER);
        order(first, OrderStatus.NEW, 1);
        order(second, OrderStatus.NEW, 1);

        assertThat(orders.findByUserIdOrderByIdDesc(first.id)).hasSize(1);
        assertThat(orders.findByUserIdOrderByIdDesc(second.id)).hasSize(1);
    }

    @Test
    @DisplayName("позиции заказа подгружаются вместе с заказом и не дублируются")
    void позицииЗаказаПодгружаютсяВместеСЗаказом() {
        User owner = user("ivan@mail.local", Role.USER);
        Order created = order(owner, OrderStatus.NEW, 2);

        List<Order> found = orders.findByUserIdOrderByIdDesc(owner.id);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).items).hasSize(2);
        assertThat(found.get(0).items.get(0).name).isEqualTo("Розы");
        assertThat(created.items).hasSize(2);
    }

    @Test
    @DisplayName("свой заказ находится по id и id пользователя")
    void свойЗаказНаходитсяПоIdИIdПользователя() {
        User owner = user("ivan@mail.local", Role.USER);
        User other = user("other@mail.local", Role.USER);
        Order created = order(owner, OrderStatus.NEW, 1);

        assertThat(orders.findByIdAndUserId(created.id, owner.id)).isPresent();
        assertThat(orders.findByIdAndUserId(created.id, other.id)).isEmpty();
    }

    @Test
    @DisplayName("все заказы возвращаются по убыванию id")
    void всеЗаказыВозвращаютсяПоУбываниюId() {
        User owner = user("ivan@mail.local", Role.USER);
        Order first = order(owner, OrderStatus.NEW, 1);
        Order second = order(owner, OrderStatus.DELIVERED, 1);

        List<Order> found = orders.findAllByOrderByIdDesc();

        assertThat(found).extracting(o -> o.id).containsExactly(second.id, first.id);
    }
}
