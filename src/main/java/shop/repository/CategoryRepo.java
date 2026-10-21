package shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shop.model.Category;

public interface CategoryRepo extends JpaRepository<Category, Long> {}
