package shop.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import shop.model.Order;

public interface OrderRepo extends JpaRepository<Order, Long> {
    /** Позиции нужны вместе с заказом — @EntityGraph тянет их одним запросом. */
    @EntityGraph(attributePaths = "items")
    List<Order> findByUserIdOrderByIdDesc(Long userId);

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByOrderByIdDesc();

    /** Один заказ конкретного пользователя — для «повторить заказ». */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdAndUserId(Long id, Long userId);
}
