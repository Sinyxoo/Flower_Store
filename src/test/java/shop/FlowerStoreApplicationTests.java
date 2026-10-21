package shop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import shop.repository.CategoryRepo;
import shop.repository.ProductRepo;
import shop.repository.UserRepo;

/**
 * Проверка, что приложение собирается целиком: контекст Spring поднимается,
 * репозитории работают, при первом запуске создаются администратор и демо-каталог.
 * База — встроенная H2 (см. src/test/resources/application.properties).
 */
@SpringBootTest
@DisplayName("Приложение — контекст и демо-данные")
class FlowerStoreApplicationTests {

    @Autowired
    private CategoryRepo categories;
    @Autowired
    private ProductRepo products;
    @Autowired
    private UserRepo users;

    @Test
    @DisplayName("контекст Spring собирается без ошибок")
    void контекстСобираетсяБезОшибок() {
        assertThat(categories).isNotNull();
        assertThat(products).isNotNull();
        assertThat(users).isNotNull();
    }

    @Test
    @DisplayName("при первом запуске создаются категории и товары")
    void приПервомЗапускеСоздаютсяКатегорииИТовары() {
        assertThat(categories.count()).isPositive();
        assertThat(products.count()).isPositive();
    }

    @Test
    @DisplayName("при первом запуске создаётся администратор")
    void приПервомЗапускеСоздаётсяАдминистратор() {
        assertThat(users.existsByEmailIgnoreCase("admin@flower.local")).isTrue();
    }
}
