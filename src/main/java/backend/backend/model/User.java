package backend.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String firstname;
    @Column(nullable = false)
    private String lastname;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false,unique = true)
    private String email;

    @Column(nullable = false,unique = true)
    private String mobile;

    @Column(nullable = false)
    private String role;


    private int creditScore;


    private LocalDateTime updatedAt = LocalDateTime.now();
}
