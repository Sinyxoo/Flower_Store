package shop.controller;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shop.exception.ApiException;
import shop.model.Order;
import shop.model.OrderItem;
import shop.model.User;
import shop.service.AuthService;
import shop.service.CartService;
import shop.service.OrderService;

/**
 * Действия покупателя из HTML-форм. Каждый метод:
 * 1) принимает поля формы, 2) вызывает сервис, 3) делает redirect на страницу.
 * Сообщение для страницы передаётся в адресе: ?msg=... (успех) или ?error=... (ошибка).
 */
@Controller
public class ShopController extends FormController {
    private static final Logger log = LoggerFactory.getLogger(ShopController.class);

    private final AuthService auth;
    private final CartService cart;
    private final OrderService orders;

    public ShopController(AuthService auth, CartService cart, OrderService orders) {
        this.auth = auth;
        this.cart = cart;
        this.orders = orders;
    }

    // ---------- корзина ----------
    @PostMapping("/cart/add")
    public String addToCart(@RequestParam Long productId, @RequestParam(defaultValue = "1") int qty,
                            HttpSession session, RedirectAttributes redirect) {
        return result(redirect, "/cart.html", "Товар добавлен в корзину",
                () -> cart.add(session, productId, qty));
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam Long productId, @RequestParam int qty,
                             HttpSession session, RedirectAttributes redirect) {
        return result(redirect, "/cart.html", "Корзина обновлена",
                () -> cart.setQty(session, productId, qty));
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long productId, HttpSession session, RedirectAttributes redirect) {
        return result(redirect, "/cart.html", "Товар убран из корзины",
                () -> cart.remove(session, productId));
    }

    // ---------- оформление заказа ----------
    // Здесь try/catch написан вручную: в ответе нужен ещё и номер созданного заказа.
    @PostMapping("/checkout")
    public String checkout(@RequestParam String customerName,
                           @RequestParam(defaultValue = "") String email,
                           @RequestParam(defaultValue = "") String phone,
                           @RequestParam String address,
                           @RequestParam(defaultValue = "cash") String payment,
                           HttpSession session, RedirectAttributes redirect) {
        try {
            OrderService.OrderData data = new OrderService.OrderData(customerName, email, phone, address, payment);
            Order order = orders.create(auth.current(session), cart.raw(session), data);
            cart.clear(session);
            redirect.addAttribute("id", order.id);
            return "redirect:/order-success.html";
        } catch (ApiException e) {
            redirect.addAttribute("error", e.getMessage());
            return "redirect:/checkout.html";
        }
    }

    // ---------- вход, регистрация, выход ----------
    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password,
                        HttpSession session, RedirectAttributes redirect) {
        return result(redirect, "/account.html", "/login.html", "Вы вошли",
                () -> auth.login(session, email, password));
    }

    @PostMapping("/register")
    public String register(@RequestParam String name, @RequestParam String email,
                           @RequestParam String password, @RequestParam String password2,
                           HttpSession session, RedirectAttributes redirect) {
        return result(redirect, "/account.html", "/register.html", "Аккаунт создан", () -> {
            if (!password.equals(password2)) throw ApiException.badRequest("Пароли не совпадают");
            auth.register(session, name, email, password);
        });
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        auth.logout(session);
        return "redirect:/index.html";
    }

    // ---------- личный кабинет ----------
    @PostMapping("/account/profile")
    public String saveProfile(@RequestParam String name,
                              @RequestParam(defaultValue = "") String phone,
                              @RequestParam(defaultValue = "") String address,
                              HttpSession session, RedirectAttributes redirect) {
        User user = auth.require(session);   // не вошёл — ErrorHandler отправит на страницу входа
        return result(redirect, "/account.html", "Данные сохранены",
                () -> auth.updateProfile(user, name, phone, address));
    }

    /** «Повторить заказ»: кладёт все товары старого заказа в корзину. */
    @PostMapping("/account/repeat/{orderId}")
    public String repeatOrder(@PathVariable Long orderId, HttpSession session, RedirectAttributes redirect) {
        User user = auth.require(session);
        Order order = orders.findOne(orderId, user.id);

        String error = null;
        for (OrderItem item : order.items) {
            try {
                cart.add(session, item.productId, item.qty);
            } catch (ApiException e) {
                error = e.getMessage();   // товара нет или не хватает — добавим остальные
            }
        }
        if (error != null) redirect.addAttribute("error", error);
        return "redirect:/cart.html";
    }

    // ---------- обратная связь (сообщение пишется в лог сервера) ----------
    @PostMapping("/contacts")
    public String sendMessage(@RequestParam(defaultValue = "") String name,
                              @RequestParam(defaultValue = "") String contact,
                              @RequestParam(defaultValue = "") String message,
                              RedirectAttributes redirect) {
        if (message.isBlank()) {
            redirect.addAttribute("error", "Напишите сообщение");
        } else {
            log.info("Сообщение от '{}' ({}): {}", name, contact, message);
            redirect.addAttribute("msg", "Сообщение отправлено");
        }
        return "redirect:/contacts.html";
    }
}
