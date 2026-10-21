package shop.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import shop.model.Product;

public interface ProductRepo extends JpaRepository<Product, Long> {
    List<Product> findByVisibleTrue();

    /** Есть ли товары в категории. Category_Id — переход по связи category. */
    boolean existsByCategory_Id(Long categoryId);
}
