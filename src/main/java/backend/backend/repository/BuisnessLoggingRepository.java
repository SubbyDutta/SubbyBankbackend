package backend.backend.repository;
import backend.backend.model.AuditLog;
import backend.backend.model.BuisnessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuisnessLoggingRepository extends JpaRepository<BuisnessLog, Long>{
}
