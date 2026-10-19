package shop;

import static shop.ApiException.blank;
import static shop.ApiException.check;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Публичная часть: вход, каталог, корзина, заказы, обратная связь. */
@RestController
@RequestMapping("/api")
public class ApiController {
    public record Reg(String name, String email, String password) {}
    public record Login(String email, String password) {}
    public record Profile(String name, String phone, String address) {}
    public record CartAdd(Long productId, Integer qty) {}
    public record Qty(int qty) {}
    public record Message(String name, String contact, String message) {}

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
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

    // ----- вход -----
    @PostMapping("/auth/register")
    public User register(@RequestBody Reg r, HttpSession s) {
        return auth.register(s, r.name(), r.email(), r.password());
    }

    @PostMapping("/auth/login")
    public User login(@RequestBody Login r, HttpSession s) {
        return auth.login(s, r.email(), r.password());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession s) {
        auth.logout(s);
    }

    /** Гость → 204 (фронтенд превращает это в null). */
    @GetMapping("/auth/me")
    public ResponseEntity<User> me(HttpSession s) {
        User u = auth.current(s);
        return u == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(u);
    }

    @PutMapping("/me")
    public User update(@RequestBody Profile p, HttpSession s) {
        return auth.updateProfile(auth.require(s), p.name(), p.phone(), p.address());
    }

    // ----- каталог -----
    @GetMapping("/categories")
    public List<Category> categories() {
        return catalog.categories();
    }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(required = false) Long category,
                                  @RequestParam(required = false) String sort) {
        return catalog.products(category, sort);
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable Long id) {
        return catalog.product(id);
    }

    // ----- корзина (в сессии) -----
    @GetMapping("/cart")
    public CartService.View cart(HttpSession s) {
        return cart.view(s);
    }

    @PostMapping("/cart/items")
    public CartService.View add(@RequestBody CartAdd r, HttpSession s) {
        return cart.add(s, r.productId(), r.qty() == null ? 1 : r.qty());
    }

    @PutMapping("/cart/items/{id}")
    public CartService.View setQty(@PathVariable Long id, @RequestBody Qty r, HttpSession s) {
        return cart.setQty(s, id, r.qty());
    }

    @DeleteMapping("/cart/items/{id}")
    public CartService.View remove(@PathVariable Long id, HttpSession s) {
        return cart.remove(s, id);
    }

    // ----- заказы -----
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Order createOrder(@RequestBody OrderService.OrderReq r, HttpSession s) {
        Order o = orders.create(auth.current(s), cart.raw(s), r);
        cart.clear(s);
        return o;
    }

    @GetMapping("/me/orders")
    public List<Order> myOrders(HttpSession s) {
        return orders.mine(auth.require(s));
    }

    // ----- обратная связь (пишется в лог сервера) -----
    @PostMapping("/messages")
    public Map<String, Boolean> message(@RequestBody Message m) {
        check(!blank(m.message()), "Напишите сообщение");
        log.info("Сообщение от '{}' ({}): {}", m.name(), m.contact(), m.message());
        return Map.of("ok", true);
    }
}
