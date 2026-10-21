package shop.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import shop.model.User;

public interface UserRepo extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
