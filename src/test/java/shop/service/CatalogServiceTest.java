package shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import shop.exception.ApiException;
import shop.model.Category;
import shop.model.Product;
import shop.repository.CategoryRepo;
import shop.repository.ProductRepo;
import shop.support.TestFixtures;

/**
 * Модульные тесты каталога: валидация данных товара, категории, видимость.
 */
@DisplayName("CatalogService — каталог товаров и категорий")
class CatalogServiceTest {

    private CategoryRepo categories;
    private ProductRepo products;
    private CatalogService catalog;
    private Category flowers;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepo.class);
        products = mock(ProductRepo.class);
        catalog = new CatalogService(categories, products);
        flowers = TestFixtures.category(1L, "Цветы");
        when(products.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));
        when(categories.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));
    }

    private CatalogService.ProductData data(String name, BigDecimal price, Integer stock, Long categoryId) {
        return new CatalogService.ProductData(name, categoryId, price, "Описание", "photo.jpg", stock, true);
    }

    private Product product(long id, String name, String price, int stock, boolean visible) {
        return TestFixtures.product(id, name, price, stock, visible, flowers);
    }

    // =====================================================================
    @Nested
    @DisplayName("Валидация данных товара")
    class Validation {

        @Test
        @DisplayName("пустое название отклоняется")
        void пустоеНазваниеОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> catalog.saveProduct(null, data("   ", BigDecimal.TEN, 5, 1L)));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Название не может быть пустым");
            verify(products, never()).save(any());
        }

        @Test
        @DisplayName("отрицательная цена отклоняется")
        void отрицательнаяЦенаОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> catalog.saveProduct(null, data("Розы", new BigDecimal("-1.00"), 5, 1L)));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Цена не может быть отрицательной");
            verify(products, never()).save(any());
        }

        @Test
        @DisplayName("нулевая цена допускается")
        void нулеваяЦенаДопускается() {
            when(categories.findById(1L)).thenReturn(Optional.of(flowers));

            Product saved = catalog.saveProduct(null, data("Подарок", BigDecimal.ZERO, 5, 1L));

            assertThat(saved.price).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("отсутствие цены отклоняется")
        void отсутствиеЦеныОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> catalog.saveProduct(null, data("Розы", null, 5, 1L)));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Укажите цену");
        }

        @Test
        @DisplayName("отрицательный остаток отклоняется")
        void отрицательныйОстатокОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> catalog.saveProduct(null, data("Розы", BigDecimal.TEN, -1, 1L)));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Остаток не может быть отрицательным");
        }

        @Test
        @DisplayName("отсутствие остатка отклоняется")
        void отсутствиеОстаткаОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> catalog.saveProduct(null, data("Розы", BigDecimal.TEN, null, 1L)));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Укажите остаток");
        }

        @Test
        @DisplayName("товар без категории отклоняется")
        void товарБезКатегорииОтклоняется() {
            ApiException error = (ApiException) catchThrowable(
                    () -> catalog.saveProduct(null, data("Розы", BigDecimal.TEN, 5, null)));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Выберите категорию");
        }

        @Test
        @DisplayName("несуществующая категория отклоняется")
        void несуществующаяКатегорияОтклоняется() {
            when(categories.findById(99L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(
                    () -> catalog.saveProduct(null, data("Розы", BigDecimal.TEN, 5, 99L)));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Выберите категорию");
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Сохранение товара")
    class SaveProduct {

        @Test
        @DisplayName("новый товар заполняется по данным формы")
        void новыйТоварЗаполняетсяПоДаннымФормы() {
            when(categories.findById(1L)).thenReturn(Optional.of(flowers));

            Product saved = catalog.saveProduct(null,
                    new CatalogService.ProductData("  Розы  ", 1L, new BigDecimal("10"), "Описание", "photo.jpg", 7, true));

            assertThat(saved.name).isEqualTo("Розы");
            assertThat(saved.category).isSameAs(flowers);
            assertThat(saved.description).isEqualTo("Описание");
            assertThat(saved.imageUrl).isEqualTo("photo.jpg");
            assertThat(saved.stock).isEqualTo(7);
            assertThat(saved.visible).isTrue();
        }

        @Test
        @DisplayName("цена округляется до двух знаков")
        void ценаОкругляетсяДоДвухЗнаков() {
            when(categories.findById(1L)).thenReturn(Optional.of(flowers));

            Product saved = catalog.saveProduct(null, data("Розы", new BigDecimal("10.005"), 5, 1L));

            assertThat(saved.price.scale()).isEqualTo(2);
            assertThat(saved.price).isEqualByComparingTo("10.01");
        }

        @Test
        @DisplayName("пустая ссылка на фото сохраняется как null")
        void пустаяСсылкаНаФотоСохраняетсяКакNull() {
            when(categories.findById(1L)).thenReturn(Optional.of(flowers));

            Product saved = catalog.saveProduct(null,
                    new CatalogService.ProductData("Розы", 1L, BigDecimal.TEN, "", "   ", 5, true));

            assertThat(saved.imageUrl).isNull();
        }

        @Test
        @DisplayName("редактирование обновляет существующий товар")
        void редактированиеОбновляетТовар() {
            Product existing = product(3L, "Старое название", "5.00", 1, true);
            when(products.findById(3L)).thenReturn(Optional.of(existing));
            when(categories.findById(1L)).thenReturn(Optional.of(flowers));

            Product saved = catalog.saveProduct(3L, data("Новое название", new BigDecimal("12"), 9, 1L));

            assertThat(saved.id).isEqualTo(3L);
            assertThat(saved.name).isEqualTo("Новое название");
            assertThat(saved.stock).isEqualTo(9);
        }

        @Test
        @DisplayName("редактирование несуществующего товара отклоняется")
        void редактированиеНесуществующегоТовараОтклоняется() {
            when(categories.findById(1L)).thenReturn(Optional.of(flowers));
            when(products.findById(404L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(
                    () -> catalog.saveProduct(404L, data("Розы", BigDecimal.TEN, 5, 1L)));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
            assertThat(error).hasMessage(ApiException.PRODUCT_NOT_FOUND);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Каталог для покупателя")
    class Visible {

        @Test
        @DisplayName("по умолчанию товары идут по порядку id")
        void поУмолчаниюТоварыИдутПоПорядкуId() {
            when(products.findByVisibleTrue()).thenReturn(List.of(product(2L, "Тюльпаны", "25.50", 5, true),
                    product(1L, "Розы", "10.00", 5, true)));

            List<Product> result = catalog.visibleProducts(null, null);

            assertThat(result).extracting(p -> p.id).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("сортировка price ставит дешёвые первыми")
        void сортировкаPriceСтавитДешёвыеПервыми() {
            when(products.findByVisibleTrue()).thenReturn(List.of(product(2L, "Тюльпаны", "25.50", 5, true),
                    product(1L, "Розы", "10.00", 5, true)));

            List<Product> result = catalog.visibleProducts(null, "price");

            assertThat(result).extracting(p -> p.id).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("сортировка price_desc ставит дорогие первыми")
        void сортировкаPriceDescСтавитДорогиеПервыми() {
            when(products.findByVisibleTrue()).thenReturn(List.of(product(1L, "Розы", "10.00", 5, true),
                    product(2L, "Тюльпаны", "25.50", 5, true)));

            List<Product> result = catalog.visibleProducts(null, "price_desc");

            assertThat(result).extracting(p -> p.id).containsExactly(2L, 1L);
        }

        @Test
        @DisplayName("каталог фильтруется по категории")
        void каталогФильтруетсяПоКатегории() {
            Category other = TestFixtures.category(2L, "Букеты");
            // переназначаем категорию второго товара
            Product bouquet = product(2L, "Букет", "95.00", 5, true);
            bouquet.category = other;
            when(products.findByVisibleTrue()).thenReturn(List.of(product(1L, "Розы", "10.00", 5, true), bouquet));

            List<Product> result = catalog.visibleProducts(2L, null);

            assertThat(result).extracting(p -> p.id).containsExactly(2L);
        }

        @Test
        @DisplayName("видимый товар открывается")
        void видимыйТоварОткрывается() {
            when(products.findById(1L)).thenReturn(Optional.of(product(1L, "Розы", "10.00", 5, true)));

            assertThat(catalog.visibleProduct(1L).name).isEqualTo("Розы");
        }

        @Test
        @DisplayName("скрытый товар покупателю не отдаётся")
        void скрытыйТоварНеОтдаётсяПокупателю() {
            when(products.findById(1L)).thenReturn(Optional.of(product(1L, "Скрытый", "10.00", 5, false)));

            ApiException error = (ApiException) catchThrowable(() -> catalog.visibleProduct(1L));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
        }

        @Test
        @DisplayName("несуществующий товар не отдаётся")
        void несуществующийТоварНеОтдаётся() {
            when(products.findById(404L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> catalog.visibleProduct(404L));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Видимость и удаление товара")
    class Visibility {

        @Test
        @DisplayName("переключение видимости инвертирует флаг")
        void переключениеВидимостиИнвертируетФлаг() {
            Product existing = product(1L, "Розы", "10.00", 5, true);
            when(products.findById(1L)).thenReturn(Optional.of(existing));

            catalog.toggleVisible(1L);

            assertThat(existing.visible).isFalse();
            verify(products).save(existing);
        }

        @Test
        @DisplayName("переключение видимости несуществующего товара отклоняется")
        void переключениеВидимостиНесуществующегоТовараОтклоняется() {
            when(products.findById(404L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> catalog.toggleVisible(404L));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
        }

        @Test
        @DisplayName("удаление товара удаляет его из базы")
        void удалениеТовараУдаляетЕгоИзБазы() {
            Product existing = product(1L, "Розы", "10.00", 5, true);
            when(products.findById(1L)).thenReturn(Optional.of(existing));

            catalog.deleteProduct(1L);

            verify(products).delete(existing);
        }

        @Test
        @DisplayName("удаление несуществующего товара отклоняется")
        void удалениеНесуществующегоТовараОтклоняется() {
            when(products.findById(404L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> catalog.deleteProduct(404L));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Категории")
    class Categories {

        @Test
        @DisplayName("список категорий берётся из репозитория")
        void списокКатегорийБерётсяИзРепозитория() {
            List<Category> all = List.of(flowers);
            when(categories.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(all);

            assertThat(catalog.allCategories()).isSameAs(all);
        }

        @Test
        @DisplayName("новая категория сохраняется с обрезанным названием")
        void новаяКатегорияСохраняетсяСОбрезаннымНазванием() {
            Category saved = catalog.saveCategory(null, "  Букеты  ");

            assertThat(saved.name).isEqualTo("Букеты");
            verify(categories).save(saved);
        }

        @Test
        @DisplayName("пустое название категории отклоняется")
        void пустоеНазваниеКатегорииОтклоняется() {
            ApiException error = (ApiException) catchThrowable(() -> catalog.saveCategory(null, "   "));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(400);
            assertThat(error).hasMessage("Название категории не может быть пустым");
        }

        @Test
        @DisplayName("переименование существующей категории работает")
        void переименованиеСуществующейКатегорииРаботает() {
            when(categories.findById(1L)).thenReturn(Optional.of(flowers));

            Category saved = catalog.saveCategory(1L, "  Розы  ");

            assertThat(saved.name).isEqualTo("Розы");
        }

        @Test
        @DisplayName("переименование несуществующей категории отклоняется")
        void переименованиеНесуществующейКатегорииОтклоняется() {
            when(categories.findById(404L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> catalog.saveCategory(404L, "Розы"));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
            assertThat(error).hasMessage(ApiException.CATEGORY_NOT_FOUND);
        }

        @Test
        @DisplayName("удаление пустой категории проходит")
        void удалениеПустойКатегорииПроходит() {
            when(categories.findById(1L)).thenReturn(Optional.of(flowers));
            when(products.existsByCategory_Id(1L)).thenReturn(false);

            catalog.deleteCategory(1L);

            verify(categories).delete(flowers);
        }

        @Test
        @DisplayName("удаление категории с товарами отклоняется")
        void удалениеКатегорииСТоварамиОтклоняется() {
            when(categories.findById(1L)).thenReturn(Optional.of(flowers));
            when(products.existsByCategory_Id(1L)).thenReturn(true);

            ApiException error = (ApiException) catchThrowable(() -> catalog.deleteCategory(1L));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(409);
            assertThat(error).hasMessageContaining("в которой есть товары");
            verify(categories, never()).delete(any());
        }

        @Test
        @DisplayName("удаление несуществующей категории отклоняется")
        void удалениеНесуществующейКатегорииОтклоняется() {
            when(categories.findById(404L)).thenReturn(Optional.empty());

            ApiException error = (ApiException) catchThrowable(() -> catalog.deleteCategory(404L));

            assertThat(error).isNotNull();
            assertThat(error.status).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("список всех товаров для админки берётся из репозитория")
    void списокВсехТоваровБерётсяИзРепозитория() {
        List<Product> all = List.of(product(1L, "Розы", "10.00", 5, true));
        when(products.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(all);

        assertThat(catalog.allProducts()).isSameAs(all);
    }
}
