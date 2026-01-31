package backend.backend.model;

import backend.backend.configuration.PiiConverter;
import jakarta.persistence.*;
import lombok.Data;



    @Entity
    @Data
    public class BankAccount{
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @OneToOne
        private User user;
        @Column(unique = true, nullable = false)
        private String accountNumber;
        private String type;
        private double balance;
        private boolean isBlocked;
        @Convert(converter = PiiConverter.class)
        private String adhar;
        @Convert(converter = PiiConverter.class)
        private String pan;
        private boolean isVerified;
    }

