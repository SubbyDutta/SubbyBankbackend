package backend.backend.configuration;

import backend.backend.Dtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;


@Configuration
@EnableCaching
public class CacheConfig {


    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory("redis", 6379);
    }

    @Bean
    @Qualifier("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }


    private <T> RedisCacheConfiguration typedCache(
            Duration ttl,
            ObjectMapper mapper,
            Class<T> type
    ) {
        Jackson2JsonRedisSerializer<T> serializer =
                new Jackson2JsonRedisSerializer<>(type);
        serializer.setObjectMapper(mapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(serializer)
                )
                .disableCachingNullValues();
    }



    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper mapper
    ) {

        return RedisCacheManager.builder(connectionFactory)



                // User by ID
                .withCacheConfiguration(
                        "banking:user:byId",
                        typedCache(Duration.ofMinutes(10), mapper, UserResponseDto.class)
                )

                // User list pages
                .withCacheConfiguration(
                        "banking:users:list",
                        typedCache(Duration.ofMinutes(3), mapper, List.class)
                )



                // Account DTO
                .withCacheConfiguration(
                        "banking:account:dto",
                        typedCache(Duration.ofMinutes(5), mapper, BankAccountResponseDto.class)
                )

                // Account list pages
                .withCacheConfiguration(
                        "banking:accounts:list",
                        typedCache(Duration.ofMinutes(3), mapper, List.class)
                )



                // Transaction lists (admin)
                .withCacheConfiguration(
                        "banking:transactions:list",
                        typedCache(Duration.ofMinutes(2), mapper, List.class)
                )

                // Transaction list per user
                .withCacheConfiguration(
                        "banking:transactions:user",
                        typedCache(Duration.ofMinutes(2), mapper, List.class)
                )

                /* ---------- LOANS ---------- */

                // All loans
                .withCacheConfiguration(
                        "banking:loans:list",
                        typedCache(Duration.ofMinutes(5), mapper, List.class)
                )

                // Pending loans
                .withCacheConfiguration(
                        "banking:loans:pending",
                        typedCache(Duration.ofMinutes(3), mapper, List.class)
                )

                // User approved loans
                .withCacheConfiguration(
                        "banking:loans:userApproved",
                        typedCache(Duration.ofMinutes(5), mapper, List.class)
                )

                // Loan repayments (admin)
                .withCacheConfiguration(
                        "banking:loans:repayments",
                        typedCache(Duration.ofMinutes(3), mapper, List.class)
                )

                // Loan repayments per user
                .withCacheConfiguration(
                        "banking:loans:userrepaylist",
                        typedCache(Duration.ofMinutes(3), mapper, List.class)
                )



                // Credit score
                .withCacheConfiguration(
                        "banking:credit:score",
                        typedCache(Duration.ofMinutes(5), mapper, Integer.class)
                )

                // Idempotency keys (simple values)
                .withCacheConfiguration(
                        "banking:idempotency:keys",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(60))
                )

                .build();
    }
}
