package shop;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepo extends JpaRepository<Product, Long> {
    List<Product> findByVisibleTrue();
    @Query ("select count(p) > 0 from Product p where p.category.id = :id")
    boolean existsByCategory(@Param ("id") Long id);
}
