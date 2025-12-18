package backend.backend.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

   /* @Bean
    public CacheManager cacheManager() {

        CaffeineCacheManager manager = new CaffeineCacheManager();


        manager.registerCustomCache("balance",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.SECONDS)
                        .maximumSize(50_000)
                        .build());

        manager.registerCustomCache("BankAccountResponseDto",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(20_000)
                        .build());


        manager.registerCustomCache("user",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build());

        manager.registerCustomCache("users:all",
                Caffeine.newBuilder()
                        .expireAfterWrite(3, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .build());

        manager.registerCustomCache("score",
                Caffeine.newBuilder()
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build());


        manager.registerCustomCache("transactions:all",
                Caffeine.newBuilder()
                        .expireAfterWrite(15, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());

        manager.registerCustomCache("transactions:user",
                Caffeine.newBuilder()
                        .expireAfterWrite(20, TimeUnit.MINUTES)
                        .maximumSize(5_000)
                        .build());


        manager.registerCustomCache("loanSummary",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build());

        manager.registerCustomCache("pendingapplications:all",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .build());

        manager.registerCustomCache("loanApplications:all",
                Caffeine.newBuilder()
                        .expireAfterWrite(20, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());
        manager.registerCustomCache("repaylist",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .build()
        );
        manager.registerCustomCache("UserApprovedLoan",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .build()
        );
        manager.registerCustomCache("accountNumber",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.DAYS)
                        .maximumSize(200)
                        .build()
        );
        manager.registerCustomCache("existsuser",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.DAYS)
                        .maximumSize(200)
                        .build()
        );
        manager.registerCustomCache("accounts:all",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.DAYS)
                        .maximumSize(200)
                        .build()
        );

        return manager;
    }*/

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
            return RedisCacheManager.builder(factory)
                    .cacheDefaults(
                            RedisCacheConfiguration.defaultCacheConfig()
                                    .entryTtl(Duration.ofMinutes(10))
                                    .serializeValuesWith(
                                            RedisSerializationContext.SerializationPair
                                                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
                                    )
                    ).build();
        }
    }


