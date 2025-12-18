package backend.backend.repository;

import backend.backend.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface  TransactionRepository extends JpaRepository<Transaction, Long> {


    Optional<Transaction> findTopByUserIdOrderByTimestampDesc(int userId);


    Page<Transaction> findByUserIdOrderByTimestampDesc(int userId, Pageable pageable);


    Page<Transaction> findAllByOrderByTimestampDesc(Pageable pageable);

    List<Transaction> findByUserId(int userId);

    List<Transaction> findBySenderAccountOrReceiverAccountOrderByTimestampDesc(
            String senderAccount,
            String receiverAccount
    );
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId ORDER BY t.timestamp DESC")
    List<Transaction> findRecentTransactions(@Param("userId") int userId, Pageable pageable);

    //  Advanced: paginated + filtered search for a user
    @Query("""
           SELECT t
           FROM Transaction t
           WHERE t.userId = :userId
             AND (:from IS NULL OR t.timestamp >= :from)
             AND (:to IS NULL OR t.timestamp <= :to)
             AND (:minAmount IS NULL OR t.amount >= :minAmount)
             AND (:maxAmount IS NULL OR t.amount <= :maxAmount)
           ORDER BY t.timestamp DESC
           """)
    Page<Transaction> searchByUserWithFilters(
            @Param("userId") int userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("minAmount") Double minAmount,
            @Param("maxAmount") Double maxAmount,
            Pageable pageable
    );
}
