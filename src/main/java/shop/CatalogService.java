package shop;

import static shop.ApiException.blank;
import static shop.ApiException.check;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    public record ProductReq(String name, Long categoryId, BigDecimal price, String description,
                             String imageUrl, Integer stock, Boolean visible) {}

    private final CategoryRepo categories;
    private final ProductRepo products;

    public CatalogService(CategoryRepo categories, ProductRepo products) {
        this.categories = categories;
        this.products = products;
    }

    // ----- категории -----
    public List<Category> categories() {
        return categories.findAll(Sort.by("id"));
    }

    public Category saveCategory(Long id, String name) {
        check(!blank(name), "Название категории не может быть пустым");
        Category c = id == null ? new Category() : categories.findById(id)
                .orElseThrow(() -> new ApiException(404, "Категория не найдена"));
        c.name = name.trim();
        return categories.save(c);
    }

    public void deleteCategory(Long id) {
        if (!categories.existsById(id)) throw new ApiException(404, "Категория не найдена");
        if (products.existsByCategory(id))
            throw new ApiException(409, "Нельзя удалить категорию, в которой есть товары");
        categories.deleteById(id);
    }

    // ----- товары -----
    /** Публичный каталог: только видимые. sort: price (дешёвые первыми) | price_desc. */
    public List<Product> products(Long categoryId, String sort) {
        Stream<Product> s = products.findByVisibleTrue().stream();
        if (categoryId != null)
            s = s.filter(p -> p.category != null && categoryId.equals(p.category.id));
        Comparator<Product> byPrice = Comparator.comparing((Product p) -> p.price);
        if ("price".equals(sort)) s = s.sorted(byPrice);
        else if ("price_desc".equals(sort)) s = s.sorted(byPrice.reversed());
        else s = s.sorted(Comparator.comparing((Product p) -> p.id));
        return s.toList();
    }

    public Product product(Long id) {
        return products.findById(id).filter(p -> p.visible)
                .orElseThrow(() -> new ApiException(404, "Товар не найден"));
    }

    public List<Product> allProducts() {
        return products.findAll(Sort.by("id"));
    }

    public Product saveProduct(Long id, ProductReq r) {
        check(r != null && !blank(r.name()), "Название не может быть пустым");
        check(r.price() != null && r.price().signum() >= 0, "Цена не может быть отрицательной");
        check(r.stock() != null && r.stock() >= 0, "Остаток не может быть отрицательным");
        Category c = r.categoryId() == null ? null : categories.findById(r.categoryId()).orElse(null);
        check(c != null, "Выберите категорию");
        Product p = id == null ? new Product() : products.findById(id)
                .orElseThrow(() -> new ApiException(404, "Товар не найден"));
        p.name = r.name().trim();
        p.category = c;
        p.price = r.price().setScale(2, RoundingMode.HALF_UP);
        p.description = r.description();
        p.imageUrl = blank(r.imageUrl()) ? null : r.imageUrl().trim();
        p.stock = r.stock();
        p.visible = r.visible() == null || r.visible();
        return products.save(p);
    }

    public void deleteProduct(Long id) {
        if (!products.existsById(id)) throw new ApiException(404, "Товар не найден");
        products.deleteById(id);
    }
}
