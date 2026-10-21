package shop.service;

import static shop.exception.ApiException.blank;
import static shop.exception.ApiException.check;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import shop.exception.ApiException;
import shop.model.Category;
import shop.model.Product;
import shop.repository.CategoryRepo;
import shop.repository.ProductRepo;

@Service
public class CatalogService {
    /** Данные формы товара из админки. */
    public record ProductData(String name, Long categoryId, BigDecimal price, String description,
                              String imageUrl, Integer stock, boolean visible) {}

    private final CategoryRepo categories;
    private final ProductRepo products;

    public CatalogService(CategoryRepo categories, ProductRepo products) {
        this.categories = categories;
        this.products = products;
    }

    // ---------- категории ----------
    public List<Category> allCategories() {
        return categories.findAll(Sort.by("id"));
    }

    /** id == null — новая категория, иначе переименование существующей. */
    public Category saveCategory(Long id, String name) {
        check(!blank(name), "Название категории не может быть пустым");
        Category category = id == null
                ? new Category()
                : categories.findById(id).orElseThrow(() -> ApiException.notFound(ApiException.CATEGORY_NOT_FOUND));
        category.name = name.trim();
        return categories.save(category);
    }

    public void deleteCategory(Long id) {
        Category category = categories.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiException.CATEGORY_NOT_FOUND));
        if (products.existsByCategory_Id(id))
            throw ApiException.conflict("Нельзя удалить категорию, в которой есть товары");
        categories.delete(category);
    }

    // ---------- товары ----------
    /** Каталог для покупателей: только видимые товары. sort: "price" (дешёвые первыми) или "price_desc". */
    public List<Product> visibleProducts(Long categoryId, String sort) {
        Stream<Product> stream = products.findByVisibleTrue().stream();
        if (categoryId != null) {
            stream = stream.filter(p -> p.category != null && categoryId.equals(p.category.id));
        }
        Comparator<Product> byPrice = Comparator.comparing((Product p) -> p.price);
        if ("price".equals(sort)) {
            stream = stream.sorted(byPrice);
        } else if ("price_desc".equals(sort)) {
            stream = stream.sorted(byPrice.reversed());
        } else {
            stream = stream.sorted(Comparator.comparing((Product p) -> p.id));
        }
        return stream.toList();
    }

    public Product visibleProduct(Long id) {
        return products.findById(id).filter(p -> p.visible)
                .orElseThrow(() -> ApiException.notFound(ApiException.PRODUCT_NOT_FOUND));
    }

    public List<Product> allProducts() {
        return products.findAll(Sort.by("id"));
    }

    /** id == null — новый товар, иначе редактирование. */
    public Product saveProduct(Long id, ProductData data) {
        check(!blank(data.name()), "Название не может быть пустым");
        check(data.price() != null, "Укажите цену");
        check(data.price().signum() >= 0, "Цена не может быть отрицательной");
        check(data.stock() != null, "Укажите остаток");
        check(data.stock() >= 0, "Остаток не может быть отрицательным");
        Category category = null;
        if (data.categoryId() != null) {
            category = categories.findById(data.categoryId()).orElse(null);
        }
        check(category != null, "Выберите категорию");

        Product product = id == null ? new Product() : findProduct(id);
        product.name = data.name().trim();
        product.category = category;
        product.price = data.price().setScale(2, RoundingMode.HALF_UP);
        product.description = data.description();
        product.imageUrl = blank(data.imageUrl()) ? null : data.imageUrl().trim();
        product.stock = data.stock();
        product.visible = data.visible();
        return products.save(product);
    }

    public void toggleVisible(Long id) {
        Product product = findProduct(id);
        product.visible = !product.visible;
        products.save(product);
    }

    public void deleteProduct(Long id) {
        products.delete(findProduct(id));
    }

    private Product findProduct(Long id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound(ApiException.PRODUCT_NOT_FOUND));
    }
}
