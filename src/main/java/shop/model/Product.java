package shop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public String name;
    @JsonIgnore @ManyToOne public Category category;
    @Column(nullable = false, precision = 10, scale = 2) public BigDecimal price;
    @Column(length = 2000) public String description;
    public String imageUrl;
    public int stock;
    public boolean visible = true;

    // Эти два метода нужны только для JSON: страницы получают categoryId и categoryName
    public Long getCategoryId() { return category == null ? null : category.id; }
    public String getCategoryName() { return category == null ? "" : category.name; }
}
