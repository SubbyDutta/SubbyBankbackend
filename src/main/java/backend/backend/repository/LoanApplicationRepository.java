package backend.backend.repository;

import backend.backend.model.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanApplicationRepository
        extends JpaRepository<LoanApplication, Long>, JpaSpecificationExecutor<LoanApplication> {



    List<LoanApplication> findByUsername(String username);
    List<LoanApplication> findByUsernameAndStatus(String username, String status);


    @Query("""
           SELECT l FROM LoanApplication l
           WHERE (:username IS NULL OR LOWER(l.username) LIKE LOWER(CONCAT('%', :username, '%')))
           AND (:minAmount IS NULL OR l.amount >= :minAmount)
           """)
    Page<LoanApplication> searchLoans(
            @Param("username") String username,
            @Param("minAmount") Double minAmount,
            Pageable pageable
    );

    Page<LoanApplication> findByApprovedFalseAndStatusNotIgnoreCase(String status, Pageable pageable);
}
