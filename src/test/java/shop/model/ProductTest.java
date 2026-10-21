package shop.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import shop.support.TestFixtures;

/** Товар отдаётся в JSON с id и названием категории. */
@DisplayName("Product — товар каталога")
class ProductTest {

    @Test
    @DisplayName("id и название категории берутся из самой категории")
    void idИНазваниеКатегорииБерутсяИзКатегории() {
        Product product = TestFixtures.product(1L, "Розы", "10.00", 5, true,
                TestFixtures.category(3L, "Цветы"));

        assertThat(product.getCategoryId()).isEqualTo(3L);
        assertThat(product.getCategoryName()).isEqualTo("Цветы");
    }

    @Test
    @DisplayName("товар без категории отдаёт пустые значения")
    void товарБезКатегорииОтдаётПустыеЗначения() {
        Product product = TestFixtures.product(1L, "Розы", "10.00", 5, true, null);

        assertThat(product.getCategoryId()).isNull();
        assertThat(product.getCategoryName()).isEmpty();
    }

    @Test
    @DisplayName("новый товар по умолчанию видим")
    void новыйТоварПоУмолчаниюВидим() {
        Product product = new Product();

        assertThat(product.visible).isTrue();
    }
}
