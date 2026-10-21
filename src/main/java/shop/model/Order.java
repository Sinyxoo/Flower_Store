package shop.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    public LocalDateTime createdAt;
    /**
     * Позиции заказа. Ленивая загрузка + @EntityGraph в OrderRepo:
     * список заказов тянет все позиции одним запросом, а не по одному на заказ.
     * cascade = ALL — вместе с заказом удаляются и его позиции.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    public List<OrderItem> items = new ArrayList<>();

    /** Статусы, в которые заказ ещё можно перевести — для кнопок в админке. */
    public List<OrderStatus> getNextStatuses() {
        return status == null ? List.of() : status.next();
    }
}
