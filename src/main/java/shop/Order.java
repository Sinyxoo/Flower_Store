package shop;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long userId;
    public String customerName;
    public String email;
    public String phone;
    public String address;
    public String payment;
    @Enumerated(EnumType.STRING) public OrderStatus status = OrderStatus.NEW;
    @Column(precision = 12, scale = 2) public BigDecimal total;
    public LocalDate createdAt;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    public List<OrderItem> items = new ArrayList<>();
}
