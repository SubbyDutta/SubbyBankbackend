package backend.backend.model;



import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class LoanApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private double amount;
    private double due_amount;
    private boolean approved = false;
    private String status;
    private int monthsRemaining = 6; // fixed tenure of 6 months

    private double monthlyEmi;

    private LocalDateTime approvedAt;

    private LocalDateTime nextDueDate;
}

