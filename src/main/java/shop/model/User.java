package shop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String name;
    @Column(unique = true, nullable = false) public String email;
    @JsonIgnore public String passwordHash;
    public String phone = "";
    public String address = "";
    /** USER или ADMIN */
    @Enumerated(EnumType.STRING) public Role role = Role.USER;
}
