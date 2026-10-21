package shop;

import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import shop.model.Category;
import shop.model.Product;
import shop.repository.CategoryRepo;
import shop.repository.ProductRepo;
import shop.service.AuthService;

/**
 * Демо-данные: при пустой базе создаёт администратора и каталог.
 * Если база не нужна — удалите этот класс.
 */
@Component
public class SeedData implements CommandLineRunner {
    private final AuthService auth;
    private final CategoryRepo categories;
    private final ProductRepo products;

    public SeedData(AuthService auth, CategoryRepo categories, ProductRepo products) {
        this.auth = auth;
        this.categories = categories;
        this.products = products;
    }

    @Override
    public void run(String... args) {
        auth.ensureAdmin();
        if (categories.count() > 0) return;

        Category roses = newCategory("Розы");
        Category tulips = newCategory("Тюльпаны");
        Category bouquets = newCategory("Букеты");
        Category pot = newCategory("Горшечные");

        newProduct("Роза красная, 60 см", roses, "4.50", 100, "Классическая красная роза с длинным стеблем.", true);
        newProduct("Роза кремовая, 50 см", roses, "3.80", 40, "Нежный кремовый оттенок.", true);
        newProduct("Тюльпаны микс, 15 шт.", tulips, "36.00", 12, "Весенний букет из разноцветных тюльпанов.", true);
        newProduct("Букет «Нежность»", bouquets, "95.00", 5, "Пионовидные розы, эустома, эвкалипт.", true);
        newProduct("Букет «Летний луг»", bouquets, "70.00", 0, "Полевые цветы в крафтовой упаковке.", true);
        newProduct("Орхидея фаленопсис", pot, "85.00", 8, "Орхидея в керамическом кашпо.", true);
        newProduct("Подсолнухи, 5 шт.", bouquets, "45.00", 15, "Яркий солнечный букет.", true);
        newProduct("Гортензия белая", pot, "60.00", 0, "Скрыта с сайта.", false);
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.name = name;
        return categories.save(category);
    }

    private void newProduct(String name, Category category, String price,
                            int stock, String description, boolean visible) {
        Product product = new Product();
        product.name = name;
        product.category = category;
        product.price = new BigDecimal(price);
        product.stock = stock;
        product.description = description;
        product.visible = visible;
        products.save(product);
    }
}
