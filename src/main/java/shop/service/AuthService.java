package shop.service;

import static shop.exception.ApiException.blank;
import static shop.exception.ApiException.check;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import shop.exception.ApiException;
import shop.model.Role;
import shop.model.User;
import shop.repository.UserRepo;

@Service
public class AuthService {
    /** Под этим именем id пользователя лежит в сессии. */
    public static final String USER_ID = "userId";

    private final UserRepo users;
    private final BCryptPasswordEncoder encoder;
    private final String adminEmail;
    private final String adminPassword;

    public AuthService(UserRepo users,
                       BCryptPasswordEncoder encoder,
                       @Value("${app.admin.email:admin@flower.local}") String adminEmail,
                       @Value("${app.admin.password:admin123}") String adminPassword) {
        this.users = users;
        this.encoder = encoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    public User register(HttpSession session, String name, String email, String password) {
        check(!blank(name), "Укажите имя");
        check(!blank(email) && email.contains("@"), "Введите корректный e-mail");
        check(password != null && password.length() >= 6, "Пароль — не короче 6 символов");
        if (users.existsByEmailIgnoreCase(email.trim()))
            throw ApiException.conflict("Пользователь с таким e-mail уже есть");

        User user = createUser(name, email, password, Role.USER);
        session.setAttribute(USER_ID, user.id);
        return user;
    }

    public User login(HttpSession session, String email, String password) {
        User user = blank(email) ? null : users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null || password == null || !encoder.matches(password, user.passwordHash))
            throw ApiException.unauthorized("Неверный e-mail или пароль");
        session.setAttribute(USER_ID, user.id);
        return user;
    }

    public void logout(HttpSession session) {
        session.removeAttribute(USER_ID);
    }

    /** Текущий пользователь или null, если это гость. */
    public User current(HttpSession session) {
        Object id = session.getAttribute(USER_ID);
        if (id == null) return null;
        return users.findById((Long) id).orElse(null);
    }

    /** Текущий пользователь; если не вошёл — ошибка 401. */
    public User require(HttpSession session) {
        User user = current(session);
        if (user == null) throw ApiException.unauthorized("Требуется авторизация");
        return user;
    }

    /** Текущий пользователь-администратор; иначе ошибка 401 или 403. */
    public User admin(HttpSession session) {
        User user = require(session);
        if (user.role != Role.ADMIN) throw ApiException.forbidden("Доступ запрещён");
        return user;
    }

    public User updateProfile(User user, String name, String phone, String address) {
        check(!blank(name), "Укажите имя");
        user.name = name.trim();
        user.phone = phone == null ? "" : phone.trim();
        user.address = address == null ? "" : address.trim();
        return users.save(user);
    }

    public List<User> allUsers() {
        return users.findAll(Sort.by("id"));
    }

    /** Создаёт администратора, если такого ещё нет. Логин и пароль — в application.properties. */
    public void ensureAdmin() {
        if (!users.existsByEmailIgnoreCase(adminEmail))
            createUser("Администратор", adminEmail, adminPassword, Role.ADMIN);
    }

    private User createUser(String name, String email, String password, Role role) {
        User user = new User();
        user.name = name.trim();
        user.email = email.trim();
        user.passwordHash = encoder.encode(password);
        user.role = role;
        return users.save(user);
    }
}
