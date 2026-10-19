package shop;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Админ-панель: каждый метод сначала проверяет роль ADMIN (401/403). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    public record CategoryReq(String name) {}
    public record StatusReq(String status) {}

    private final AuthService auth;
    private final CatalogService catalog;
    private final OrderService orders;

    public AdminController(AuthService auth, CatalogService catalog, OrderService orders) {
        this.auth = auth;
        this.catalog = catalog;
        this.orders = orders;
    }

    // ----- товары -----
    @GetMapping("/products")
    public List<Product> products(HttpSession s) {
        auth.admin(s);
        return catalog.allProducts();
    }

    @PostMapping("/products")
    public Product addProduct(@RequestBody CatalogService.ProductReq r, HttpSession s) {
        auth.admin(s);
        return catalog.saveProduct(null, r);
    }

    @PutMapping("/products/{id}")
    public Product editProduct(@PathVariable Long id, @RequestBody CatalogService.ProductReq r, HttpSession s) {
        auth.admin(s);
        return catalog.saveProduct(id, r);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id, HttpSession s) {
        auth.admin(s);
        catalog.deleteProduct(id);
    }

    // ----- категории -----
    @PostMapping("/categories")
    public Category addCategory(@RequestBody CategoryReq r, HttpSession s) {
        auth.admin(s);
        return catalog.saveCategory(null, r.name());
    }

    @PutMapping("/categories/{id}")
    public Category editCategory(@PathVariable Long id, @RequestBody CategoryReq r, HttpSession s) {
        auth.admin(s);
        return catalog.saveCategory(id, r.name());
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id, HttpSession s) {
        auth.admin(s);
        catalog.deleteCategory(id);
    }

    // ----- заказы и пользователи -----
    @GetMapping("/orders")
    public List<Order> orders(HttpSession s) {
        auth.admin(s);
        return orders.all();
    }

    @PutMapping("/orders/{id}/status")
    public Order status(@PathVariable Long id, @RequestBody StatusReq r, HttpSession s) {
        auth.admin(s);
        return orders.setStatus(id, r.status());
    }

    @GetMapping("/users")
    public List<User> users(HttpSession s) {
        auth.admin(s);
        return auth.allUsers();
    }
}
