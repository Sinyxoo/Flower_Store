package shop.controller;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shop.exception.ApiException;
import shop.service.AuthService;
import shop.service.CatalogService;
import shop.service.OrderService;

/**
 * Действия администратора из HTML-форм. В каждом методе первая строка — auth.admin(session):
 * если это не администратор, ErrorHandler отправит на страницу входа или на 403.html.
 */
@Controller
public class AdminController extends FormController {
    private final AuthService auth;
    private final CatalogService catalog;
    private final OrderService orders;

    public AdminController(AuthService auth, CatalogService catalog, OrderService orders) {
        this.auth = auth;
        this.catalog = catalog;
        this.orders = orders;
    }

    // ---------- товары ----------
    @PostMapping("/admin/products/save")
    public String saveProduct(@RequestParam(required = false) Long id,
                              @RequestParam(defaultValue = "") String name,
                              @RequestParam(required = false) Long categoryId,
                              @RequestParam(required = false) BigDecimal price,
                              @RequestParam(required = false) Integer stock,
                              @RequestParam(defaultValue = "") String description,
                              @RequestParam(defaultValue = "") String imageUrl,
                              @RequestParam(required = false) String visible,   // галочка: есть — включена
                              HttpSession session, RedirectAttributes redirect) {
        auth.admin(session);
        CatalogService.ProductData data = new CatalogService.ProductData(
                name, categoryId, price, description, imageUrl, stock, visible != null);
        try {
            catalog.saveProduct(id, data);
        } catch (ApiException e) {
            // Возвращаем на форму вместе с тем, что пользователь уже ввёл.
            redirect.addAttribute("error", e.getMessage());
            if (id != null) redirect.addAttribute("id", id);
            redirect.addAttribute("name", name);
            redirect.addAttribute("categoryId", categoryId);
            redirect.addAttribute("price", price);
            redirect.addAttribute("stock", stock);
            redirect.addAttribute("description", description);
            redirect.addAttribute("imageUrl", imageUrl);
            redirect.addAttribute("visible", visible != null);
            return "redirect:/admin/product-form.html";
        }
        redirect.addAttribute("msg", "Товар сохранён");
        return "redirect:/admin/products.html";
    }

    @PostMapping("/admin/products/{id}/toggle")
    public String toggleProduct(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
        auth.admin(session);
        return result(redirect, "/admin/products.html", "Товар обновлён", () -> catalog.toggleVisible(id));
    }

    @PostMapping("/admin/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
        auth.admin(session);
        return result(redirect, "/admin/products.html", "Товар удалён", () -> catalog.deleteProduct(id));
    }

    // ---------- категории ----------
    @PostMapping("/admin/categories")
    public String addCategory(@RequestParam(defaultValue = "") String name,
                              HttpSession session, RedirectAttributes redirect) {
        auth.admin(session);
        return result(redirect, "/admin/categories.html", "Категория добавлена",
                () -> catalog.saveCategory(null, name));
    }

    @PostMapping("/admin/categories/{id}")
    public String renameCategory(@PathVariable Long id, @RequestParam(defaultValue = "") String name,
                                 HttpSession session, RedirectAttributes redirect) {
        auth.admin(session);
        return result(redirect, "/admin/categories.html", "Категория переименована",
                () -> catalog.saveCategory(id, name));
    }

    @PostMapping("/admin/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, HttpSession session, RedirectAttributes redirect) {
        auth.admin(session);
        return result(redirect, "/admin/categories.html", "Категория удалена",
                () -> catalog.deleteCategory(id));
    }

    // ---------- заказы ----------
    @PostMapping("/admin/orders/{id}/status")
    public String changeOrderStatus(@PathVariable Long id, @RequestParam String status,
                                    HttpSession session, RedirectAttributes redirect) {
        auth.admin(session);
        return result(redirect, "/admin/orders.html", "Статус обновлён",
                () -> orders.changeStatus(id, status));
    }
}
