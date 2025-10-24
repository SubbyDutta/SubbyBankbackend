package backend.backend.repository;

import backend.backend.model.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    List<LoanApplication> findByUsername(String username);
    List<LoanApplication> findByUsernameAndStatus(String username, String status);

}
