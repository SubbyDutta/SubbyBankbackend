package backend.backend.model;



import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class LoanApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private double amount;
    private boolean approved = false;
    private String status;
}

