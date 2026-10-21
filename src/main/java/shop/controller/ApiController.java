package shop.controller;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shop.model.Category;
import shop.model.Order;
import shop.model.Product;
import shop.model.User;
import shop.service.AuthService;
import shop.service.CartService;
import shop.service.CatalogService;
import shop.service.OrderService;

/** Только чтение данных для страниц (JSON). Все действия — в ShopController и AdminController. */
@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuthService auth;
    private final CatalogService catalog;
    private final CartService cart;
    private final OrderService orders;

    public ApiController(AuthService auth, CatalogService catalog, CartService cart, OrderService orders) {
        this.auth = auth;
        this.catalog = catalog;
        this.cart = cart;
        this.orders = orders;
    }

    /** Кто сейчас вошёл. Гость — ответ 204 (пустой). */
    @GetMapping("/auth/me")
    public ResponseEntity<User> me(HttpSession session) {
        User user = auth.current(session);
        if (user == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(user);
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return catalog.allCategories();
    }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(required = false) Long category,
                                  @RequestParam(required = false) String sort) {
        return catalog.visibleProducts(category, sort);
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable Long id) {
        return catalog.visibleProduct(id);
    }

    @GetMapping("/cart")
    public CartService.View cart(HttpSession session) {
        return cart.viewAndNormalize(session);
    }

    @GetMapping("/me/orders")
    public List<Order> myOrders(HttpSession session) {
        return orders.ordersOf(auth.require(session));
    }

    // ---------- данные для админки ----------
    @GetMapping("/admin/products")
    public List<Product> adminProducts(HttpSession session) {
        auth.admin(session);
        return catalog.allProducts();
    }

    @GetMapping("/admin/orders")
    public List<Order> adminOrders(HttpSession session) {
        auth.admin(session);
        return orders.allOrders();
    }

    @GetMapping("/admin/users")
    public List<User> adminUsers(HttpSession session) {
        auth.admin(session);
        return auth.allUsers();
    }
}
