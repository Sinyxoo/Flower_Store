package shop;

import static shop.ApiException.blank;
import static shop.ApiException.check;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final String UID = "uid";
    private final UserRepo users;
    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

    public AuthService(UserRepo users) {
        this.users = users;
    }

    public User register(HttpSession s, String name, String email, String password) {
        check(!blank(name), "Укажите имя");
        check(!blank(email) && email.contains("@"), "Введите корректный e-mail");
        check(password != null && password.length() >= 6, "Пароль — не короче 6 символов");
        if (users.existsByEmailIgnoreCase(email.trim()))
            throw new ApiException(409, "Пользователь с таким e-mail уже есть");
        User u = create(name, email, password, "USER");
        s.setAttribute(UID, u.id);
        return u;
    }

    public User login(HttpSession s, String email, String password) {
        User u = blank(email) ? null : users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (u == null || password == null || !enc.matches(password, u.passwordHash))
            throw new ApiException(401, "Неверный e-mail или пароль");
        s.setAttribute(UID, u.id);
        return u;
    }

    public void logout(HttpSession s) {
        s.removeAttribute(UID);
    }

    /** Текущий пользователь или null (гость). */
    public User current(HttpSession s) {
        Object id = s.getAttribute(UID);
        return id == null ? null : users.findById((Long) id).orElse(null);
    }

    public User require(HttpSession s) {
        User u = current(s);
        if (u == null) throw new ApiException(401, "Требуется авторизация");
        return u;
    }

    public User admin(HttpSession s) {
        User u = require(s);
        if (!"ADMIN".equals(u.role)) throw new ApiException(403, "Доступ запрещён");
        return u;
    }

    public User updateProfile(User u, String name, String phone, String address) {
        check(!blank(name), "Укажите имя");
        u.name = name.trim();
        u.phone = phone == null ? "" : phone.trim();
        u.address = address == null ? "" : address.trim();
        return users.save(u);
    }

    public List<User> allUsers() {
        return users.findAll(Sort.by("id"));
    }

    public void ensureAdmin() {
        if (!users.existsByEmailIgnoreCase("admin@flower.local"))
            create("Администратор", "admin@flower.local", "admin123", "ADMIN");
    }

    private User create(String name, String email, String password, String role) {
        User u = new User();
        u.name = name.trim();
        u.email = email.trim();
        u.passwordHash = enc.encode(password);
        u.role = role;
        return users.save(u);
    }
}
