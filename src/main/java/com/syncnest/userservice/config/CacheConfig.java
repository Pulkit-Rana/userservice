package com.syncnest.userservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    /** Centralized cache name constants (add more as needed). */
    public static final String USER_DETAILS_BY_EMAIL = "userDetailsByEmail";

    /**
     * Caffeine-backed CacheManager with sensible defaults:
     * - Capacity: 10k
     * - TTL: 5 minutes (auth safety; Redis blacklist handles revocation)
     * - Record stats (exposed via Micrometer/Actuator if present)
     * - Common pool executor for async maintenance
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();

        // Inline the builder to avoid type-argument annotation issues under @NonNullApi
        mgr.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .executor(ForkJoinPool.commonPool())
                        .recordStats()
        );

        // Pre-create caches so metrics appear immediately and to validate names
        mgr.setCacheNames(List.of(USER_DETAILS_BY_EMAIL));

        // Ensure null values are not cached (explicit for clarity)
        mgr.setAllowNullValues(false);

        return mgr;
    }


    /**
     * Lowercasing KeyGenerator for String email keys:
     * Guarantees consistent keys regardless of caller casing/whitespace.
     * Apply where referenced explicitly (e.g., @Cacheable keyGenerator="lowerCaseStringKeyGenerator").
     */
    @Bean

    public KeyGenerator lowerCaseStringKeyGenerator() {
        return new KeyGenerator() {
            @NonNull
            @Override
            public Object generate(@NonNull Object target,
                                   @NonNull Method method,
                                   @NonNull Object... params) {
                if (params.length == 1 && params[0] instanceof String s) {
                    return s.trim().toLowerCase(Locale.ROOT);
                }
                // Fallback to default composite key
                return org.springframework.cache.interceptor.SimpleKeyGenerator.generateKey(params);
            }
        };
    }

    /**
     * Error handler: never 500 your app due to cache issues.
     * Log and fail open; business logic still runs (DB lookup will happen).
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(@NonNull RuntimeException exception,
                                            @NonNull Cache cache,
                                            @NonNull Object key) {
                log.warn("Cache GET error on {} key={}: {}", cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCachePutError(@NonNull RuntimeException exception,
                                            @NonNull Cache cache,
                                            @NonNull Object key,
                                            @Nullable Object value) {
                log.warn("Cache PUT error on {} key={}: {}", cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheEvictError(@NonNull RuntimeException exception,
                                              @NonNull Cache cache,
                                              @NonNull Object key) {
                log.warn("Cache EVICT error on {} key={}: {}", cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheClearError(@NonNull RuntimeException exception,
                                              @NonNull Cache cache) {
                log.warn("Cache CLEAR error on {}: {}", cache.getName(), exception.toString());
            }
        };
    }
}
