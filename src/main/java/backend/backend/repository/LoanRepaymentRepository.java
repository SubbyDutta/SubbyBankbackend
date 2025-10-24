package backend.backend.repository;

import backend.backend.model.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {
    List<LoanRepayment> findByUsernameOrderByPaymentDateDesc(String username);
    List<LoanRepayment> findByLoanIdOrderByPaymentDateDesc(Long loanId);
    void deleteByLoanId(Long loanId);

}