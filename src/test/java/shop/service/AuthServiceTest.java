package shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import shop.exception.ApiException;
import shop.model.Role;
import shop.model.User;
import shop.repository.UserRepo;
import shop.support.TestFixtures;

/**
 * Модульные тесты регистрации, входа и прав доступа.
 * Настоящий BCryptPasswordEncoder (он не обращается к базе), репозиторий — мок.
 */
@DisplayName("AuthService — пользователи, вход, права")
class AuthServiceTest {

    private UserRepo users;
    private BCryptPasswordEncoder encoder;
    private AuthService auth;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        users = mock(UserRepo.class);
        encoder = new BCryptPasswordEncoder();
        auth = new AuthService(users, encoder, "admin@flower.local", "admin123");
        session = TestFixtures.fakeSession();
        when(users.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            if (user.id == null) user.id = 100L;
            return user;
        });
    }

    private User storedUser(String email, String password, Role role) {
        User user = TestFixtures.user(5L, email, role);
        user.passwordHash = encoder.encode(password);
        return user;
    }

    // =====================================================================
    @Nested
    @DisplayName("Регистрация")
    class Register {

        @Test
        @DisplayName("создаётся пользователь с ролью USER")
        void создаётсяПользовательСРольюUSER() {
            User user = auth.register(session, "Иван", "ivan@mail.local", "secret1");

            assertThat(user.role).isEqualTo(Role.USER);
            assertThat(user.email).isEqualTo("ivan@mail.local");
            assertThat(user.name).isEqualTo("Иван");
        }

        @Test
        @DisplayName("пароль сохраняется в виде хеша, а не открытым текстом")
        void парольСохраняетсяВВидеХеша() {
            User user = auth.register(session, "Иван", "ivan@mail.local", "secret1");

            assertThat(user.passwordHash).isNotEqualTo("secret1");
            assertThat(encoder.matches("secret1", user.passwordHash)).isTrue();
        }

        @Test
        @DisplayName("после регистрации пользователь запоминается в сессии")
        void послеРегистрацииПользовательЗапоминается() {
            User user = auth.register(session, "Иван", "ivan@mail.local", "secret1");

            verify(session).setAttribute(AuthService.USER_ID, user.id);
        }

        @Test
        @DisplayName("пустое имя отклоняется")
        void пустоеИмяОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> auth.register(session, "  ", "ivan@mail.local", "secret1"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Укажите имя");
            verify(users, never()).save(any());
        }

        @Test
        @DisplayName("некорректный e-mail отклоняется")
        void некорректныйEmailОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> auth.register(session, "Иван", "ivan-at-mail", "secret1"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Введите корректный e-mail");
        }

        @Test
        @DisplayName("пустой e-mail отклоняется")
        void пустойEmailОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> auth.register(session, "Иван", "", "secret1"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
        }

        @Test
        @DisplayName("короткий пароль отклоняется")
        void короткийПарольОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> auth.register(session, "Иван", "ivan@mail.local", "12345"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Пароль — не короче 6 символов");
        }

        @Test
        @DisplayName("занятый e-mail отклоняется")
        void занятыйEmailОтклоняется() {
            when(users.existsByEmailIgnoreCase("ivan@mail.local")).thenReturn(true);

            ApiException error = (ApiException) catchThrowable(
                    () -> auth.register(session, "Иван", "ivan@mail.local", "secret1"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
            assertThat(error).hasMessage("Пользователь с таким e-mail уже есть");
            verify(users, never()).save(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Вход и выход")
    class Login {

        @Test
        @DisplayName("при верном пароле пользователь запоминается в сессии")
        void приВерномПаролеПользовательЗапоминается() {
            User stored = storedUser("ivan@mail.local", "secret1", Role.USER);
            when(users.findByEmailIgnoreCase("ivan@mail.local")).thenReturn(Optional.of(stored));

            User user = auth.login(session, "ivan@mail.local", "secret1");

            assertThat(user).isSameAs(stored);
            verify(session).setAttribute(AuthService.USER_ID, 5L);
        }

        @Test
        @DisplayName("e-mail нечувствителен к регистру")
        void emailНечувствителенКРегистру() {
            User stored = storedUser("ivan@mail.local", "secret1", Role.USER);
            when(users.findByEmailIgnoreCase("IVAN@mail.local")).thenReturn(Optional.of(stored));

            assertThat(auth.login(session, "IVAN@mail.local", "secret1")).isSameAs(stored);
        }

        @Test
        @DisplayName("неверный пароль отклоняется")
        void неверныйПарольОтклоняется() {
            User stored = storedUser("ivan@mail.local", "secret1", Role.USER);
            when(users.findByEmailIgnoreCase("ivan@mail.local")).thenReturn(Optional.of(stored));

            ApiException error = (ApiException) catchThrowable(() -> auth.login(session, "ivan@mail.local", "wrong"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(401);
            assertThat(error).hasMessage("Неверный e-mail или пароль");
            verify(session, never()).setAttribute(anyString(), any());
        }

        @Test
        @DisplayName("неизвестный e-mail отклоняется")
        void неизвестныйEmailОтклоняется() {
            when(users.findByEmailIgnoreCase("nobody@mail.local")).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> auth.login(session, "nobody@mail.local", "secret1"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(401);
        }

        @Test
        @DisplayName("пустой e-mail отклоняется")
        void пустойEmailОтклоняется() {
            ApiException error = (ApiException) catchThrowable(() -> auth.login(session, "", "secret1"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(401);
            verify(users, never()).findByEmailIgnoreCase(anyString());
        }

        @Test
        @DisplayName("выход убирает пользователя из сессии")
        void выходУбираетПользователяИзСессии() {
            auth.logout(session);

            verify(session).removeAttribute(AuthService.USER_ID);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Текущий пользователь и права")
    class CurrentUser {

        @Test
        @DisplayName("для гостя текущего пользователя нет")
        void дляГостяТекущегоПользователяНет() {
            assertThat(auth.current(session)).isNull();
        }

        @Test
        @DisplayName("вошедший пользователь берётся из базы по id из сессии")
        void вошедшийПользовательБерётсяИзБазы() {
            User stored = TestFixtures.user(5L, "ivan@mail.local", Role.USER);
            when(users.findById(5L)).thenReturn(Optional.of(stored));
            session.setAttribute(AuthService.USER_ID, 5L);

            assertThat(auth.current(session)).isSameAs(stored);
        }

        @Test
        @DisplayName("если пользователь удалён из базы — текущего нет")
        void еслиПользовательУдалёнТекущегоНет() {
            when(users.findById(5L)).thenReturn(Optional.empty());
            session.setAttribute(AuthService.USER_ID, 5L);

            assertThat(auth.current(session)).isNull();
        }

        @Test
        @DisplayName("без входа require отдаёт ошибку 401")
        void безВходаRequireОтдаётОшибку401() {
            ApiException error = (ApiException) catchThrowable(() -> auth.require(session));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(401);
            assertThat(error).hasMessage("Требуется авторизация");
        }

        @Test
        @DisplayName("вошедший пользователь проходит require")
        void вошедшийПользовательПроходитRequire() {
            User stored = TestFixtures.user(5L, "ivan@mail.local", Role.USER);
            when(users.findById(5L)).thenReturn(Optional.of(stored));
            session.setAttribute(AuthService.USER_ID, 5L);

            assertThat(auth.require(session)).isSameAs(stored);
        }

        @Test
        @DisplayName("обычный пользователь не проходит проверку администратора")
        void обычныйПользовательНеПроходитПроверкуАдминистратора() {
            User stored = TestFixtures.user(5L, "ivan@mail.local", Role.USER);
            when(users.findById(5L)).thenReturn(Optional.of(stored));
            session.setAttribute(AuthService.USER_ID, 5L);

            ApiException error = (ApiException) catchThrowable(() -> auth.admin(session));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(403);
            assertThat(error).hasMessage("Доступ запрещён");
        }

        @Test
        @DisplayName("гость не проходит проверку администратора")
        void гостьНеПроходитПроверкуАдминистратора() {
            ApiException error = (ApiException) catchThrowable(() -> auth.admin(session));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(401);
        }

        @Test
        @DisplayName("администратор проходит проверку администратора")
        void администраторПроходитПроверку() {
            User admin = TestFixtures.user(1L, "admin@flower.local", Role.ADMIN);
            when(users.findById(1L)).thenReturn(Optional.of(admin));
            session.setAttribute(AuthService.USER_ID, 1L);

            assertThat(auth.admin(session)).isSameAs(admin);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Профиль, список пользователей и администратор")
    class Profile {

        @Test
        @DisplayName("данные профиля сохраняются")
        void данныеПрофиляСохраняются() {
            User user = TestFixtures.user(5L, "ivan@mail.local", Role.USER);

            User updated = auth.updateProfile(user, "  Иван Петров  ", "  +375291112233  ", "  Минск  ");

            assertThat(updated.name).isEqualTo("Иван Петров");
            assertThat(updated.phone).isEqualTo("+375291112233");
            assertThat(updated.address).isEqualTo("Минск");
            verify(users).save(user);
        }

        @Test
        @DisplayName("пустое имя профиля отклоняется")
        void пустоеИмяПрофиляОтклоняется() {
            User user = TestFixtures.user(5L, "ivan@mail.local", Role.USER);

            ApiException error = (ApiException) catchThrowable(() -> auth.updateProfile(user, "  ", "", ""));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Укажите имя");
            verify(users, never()).save(any());
        }

        @Test
        @DisplayName("пустые телефон и адрес сохраняются пустыми строками")
        void пустыеТелефонИАдресСохраняютсяПустымиСтроками() {
            User user = TestFixtures.user(5L, "ivan@mail.local", Role.USER);

            User updated = auth.updateProfile(user, "Иван", null, null);

            assertThat(updated.phone).isEmpty();
            assertThat(updated.address).isEmpty();
        }

        @Test
        @DisplayName("список пользователей берётся из репозитория")
        void списокПользователейБерётсяИзРепозитория() {
            List<User> all = List.of(TestFixtures.user(1L, "admin@flower.local", Role.ADMIN));
            when(users.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(all);

            assertThat(auth.allUsers()).isSameAs(all);
        }

        @Test
        @DisplayName("администратор создаётся, если его ещё нет")
        void администраторСоздаётсяЕслиЕгоНет() {
            when(users.existsByEmailIgnoreCase("admin@flower.local")).thenReturn(false);

            auth.ensureAdmin();

            verify(users).save(any(User.class));
        }

        @Test
        @DisplayName("администратор не создаётся повторно")
        void администраторНеСоздаётсяПовторно() {
            when(users.existsByEmailIgnoreCase("admin@flower.local")).thenReturn(true);

            auth.ensureAdmin();

            verify(users, never()).save(any());
        }
    }
}
