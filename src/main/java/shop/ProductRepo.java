package shop;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepo extends JpaRepository<Product, Long> {
    List<Product> findByVisibleTrue();
    boolean existsByCategoryId(Long categoryId);
}
