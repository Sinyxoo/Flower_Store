package shop;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long productId;
    public String name;
    public int qty;
    public BigDecimal price;
}
