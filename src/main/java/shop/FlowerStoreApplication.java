package shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class FlowerStoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowerStoreApplication.class, args);
    }

    /** Кодировщик паролей — отдельный bean, чтобы его можно было настроить в одном месте. */
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
