package shop;

import static shop.ApiException.check;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Корзина хранится в HTTP-сессии: productId → количество. */
@Service
public class CartService {
    public record Item(Long productId, String name, BigDecimal price, int qty, int stock,
                       String imageUrl, BigDecimal sum) {}
    public record View(List<Item> items, BigDecimal total) {}

    private final ProductRepo products;

    public CartService(ProductRepo products) {
        this.products = products;
    }

    @SuppressWarnings("unchecked")
    public Map<Long, Integer> raw(HttpSession s) {
        Map<Long, Integer> m = (Map<Long, Integer>) s.getAttribute("cart");
        if (m == null) {
            m = new LinkedHashMap<>();
            s.setAttribute("cart", m);
        }
        return m;
    }

    /** Состав корзины с пересчётом суммы; исчезнувшие/закончившиеся товары убираются. */
    public View view(HttpSession s) {
        List<Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Iterator<Map.Entry<Long, Integer>> it = raw(s).entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Integer> e = it.next();
            Product p = products.findById(e.getKey()).filter(x -> x.visible && x.stock > 0).orElse(null);
            if (p == null) { it.remove(); continue; }
            if (e.getValue() > p.stock) e.setValue(p.stock);
            BigDecimal sum = p.price.multiply(BigDecimal.valueOf(e.getValue()));
            items.add(new Item(p.id, p.name, p.price, e.getValue(), p.stock, p.imageUrl, sum));
            total = total.add(sum);
        }
        return new View(items, total);
    }

    public View add(HttpSession s, Long productId, int qty) {
        check(productId != null && qty > 0, "Некорректное количество");
        Product p = visible(productId);
        int want = raw(s).getOrDefault(productId, 0) + qty;
        if (want > p.stock) throw new ApiException(409, "Недостаточно на складе: доступно " + p.stock + " шт.");
        raw(s).put(productId, want);
        return view(s);
    }

    public View setQty(HttpSession s, Long productId, int qty) {
        check(qty > 0, "Некорректное количество");
        Product p = visible(productId);
        if (qty > p.stock) throw new ApiException(409, "Недостаточно на складе: доступно " + p.stock + " шт.");
        raw(s).put(productId, qty);
        return view(s);
    }

    public View remove(HttpSession s, Long productId) {
        raw(s).remove(productId);
        return view(s);
    }

    public void clear(HttpSession s) {
        raw(s).clear();
    }

    private Product visible(Long id) {
        return products.findById(id).filter(p -> p.visible)
                .orElseThrow(() -> new ApiException(404, "Товар не найден"));
    }
}
