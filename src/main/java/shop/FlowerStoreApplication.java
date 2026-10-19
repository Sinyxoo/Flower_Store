package shop;

import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FlowerStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowerStoreApplication.class, args);
    }

    /** Админ + демо-каталог при пустой БД. Блок с каталогом можно удалить. */
    @Bean
    CommandLineRunner seed(AuthService auth, CategoryRepo cats, ProductRepo prods) {
        return a -> {
            auth.ensureAdmin();
            if (cats.count() > 0) return;
            Category roses = cat(cats, "Розы"), bouquets = cat(cats, "Букеты");
            prod(prods, "Роза красная, 60 см", roses, "4.50", 100);
            prod(prods, "Роза кремовая, 50 см", roses, "3.80", 40);
            prod(prods, "Букет «Нежность»", bouquets, "95.00", 5);
            prod(prods, "Подсолнухи, 5 шт.", bouquets, "45.00", 15);
        };
    }

    private static Category cat(CategoryRepo r, String name) {
        Category c = new Category();
        c.name = name;
        return r.save(c);
    }

    private static void prod(ProductRepo r, String name, Category c, String price, int stock) {
        Product p = new Product();
        p.name = name; p.category = c; p.price = new BigDecimal(price); p.stock = stock;
        r.save(p);
    }
}
