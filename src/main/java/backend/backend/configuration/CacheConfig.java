package backend.backend.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "transactions:user",
                "transactions:all",
                "user",
                "users:all",
                "balance",
                "score",
                "repaylist",
                "accountNumber",
                "loanApplications:all",
                "pendingapplications:all",
                "UserApprovedLoan",
                "BankAccountResponseDto",
                "accounts:all",
                "existsuser"
        );

        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .recordStats()
        );

        return cacheManager;
    }
}
