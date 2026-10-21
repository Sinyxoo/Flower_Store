package shop.service;

import static shop.exception.ApiException.check;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import shop.exception.ApiException;
import shop.model.Product;
import shop.repository.ProductRepo;

/** Корзина лежит в HTTP-сессии как таблица: id товара → количество. */
@Service
public class CartService {
    public record Item(Long productId, String name, BigDecimal price, int qty, int stock,
                       String imageUrl, BigDecimal sum) {}
    public record View(List<Item> items, BigDecimal total) {}

    private final ProductRepo products;

    public CartService(ProductRepo products) {
        this.products = products;
    }

    /** Сырая корзина из сессии (создаётся пустой, если её ещё нет). */
    @SuppressWarnings("unchecked")
    public Map<Long, Integer> raw(HttpSession session) {
        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute("cart");
        if (cart == null) {
            cart = new LinkedHashMap<>();
            session.setAttribute("cart", cart);
        }
        return cart;
    }

    /**
     * Состав корзины с актуальными ценами и суммой.
     * Пропавшие или закончившиеся товары убираются, количество урезается до остатка.
     * Метод меняет корзину в сессии — поэтому называется viewAndNormalize.
     */
    public View viewAndNormalize(HttpSession session) {
        Map<Long, Integer> cart = raw(session);
        if (cart.isEmpty()) return new View(List.of(), BigDecimal.ZERO);

        // Все товары корзины забираем одним запросом, а не по одному на позицию.
        Map<Long, Product> byId = new HashMap<>();
        for (Product product : products.findAllById(cart.keySet())) byId.put(product.id, product);

        List<Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        Iterator<Map.Entry<Long, Integer>> iterator = cart.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Integer> entry = iterator.next();
            Product product = byId.get(entry.getKey());
            if (product == null || !product.visible || product.stock <= 0) {
                iterator.remove();
                continue;
            }
            if (entry.getValue() > product.stock) entry.setValue(product.stock);

            BigDecimal sum = product.price.multiply(BigDecimal.valueOf(entry.getValue()));
            items.add(new Item(product.id, product.name, product.price, entry.getValue(),
                    product.stock, product.imageUrl, sum));
            total = total.add(sum);
        }
        return new View(items, total);
    }

    public void add(HttpSession session, Long productId, int qty) {
        check(productId != null && qty > 0, "Некорректное количество");
        Product product = findVisible(productId);
        Map<Long, Integer> cart = raw(session);
        int wanted = cart.getOrDefault(productId, 0) + qty;
        checkStock(product, wanted);
        cart.put(productId, wanted);
    }

    public void setQty(HttpSession session, Long productId, int qty) {
        check(qty > 0, "Некорректное количество");
        Product product = findVisible(productId);
        checkStock(product, qty);
        raw(session).put(productId, qty);
    }

    public void remove(HttpSession session, Long productId) {
        raw(session).remove(productId);
    }

    public void clear(HttpSession session) {
        raw(session).clear();
    }

    private Product findVisible(Long id) {
        return products.findById(id).filter(p -> p.visible)
                .orElseThrow(() -> ApiException.notFound(ApiException.PRODUCT_NOT_FOUND));
    }

    private void checkStock(Product product, int wanted) {
        if (wanted > product.stock)
            throw ApiException.conflict("Недостаточно на складе: доступно " + product.stock + " шт.");
    }
}
