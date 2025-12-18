package backend.backend.service;

import backend.backend.model.BuisnessLog;
import backend.backend.repository.BuisnessLoggingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
@RequiredArgsConstructor
@Service
public class BuisnessLoggingService {
    private final BuisnessLoggingRepository buisnessLoggingRepository;
    public void log(String action, String username, String details) {
        BuisnessLog log = new BuisnessLog(action, username, details, Instant.now());
        buisnessLoggingRepository.save(log);
    }
}
