package me.luucka.travelplanner.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import me.luucka.travelplanner.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    public enum Limit {
        LOGIN(10, Duration.ofMinutes(15)),
        SENSITIVE(5, Duration.ofHours(1));

        private final int capacity;
        private final Duration window;

        Limit(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }
    }

    // Unused keys are evicted after one window, by which time their bucket would be full again anyway.
    private final Cache<String, Bucket> login = cacheFor(Limit.LOGIN);
    private final Cache<String, Bucket> sensitive = cacheFor(Limit.SENSITIVE);

    private static Cache<String, Bucket> cacheFor(Limit limit) {
        return Caffeine.newBuilder().expireAfterAccess(limit.window).build();
    }

    public void check(String key, Limit limit) {
        Cache<String, Bucket> cache = limit == Limit.LOGIN ? login : sensitive;
        Bucket bucket = cache.get(key, k -> Bucket.builder()
                .addLimit(b -> b.capacity(limit.capacity).refillIntervally(limit.capacity, limit.window))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many attempts, try again later");
        }
    }

    /** Tests only. */
    public void reset() {
        login.invalidateAll();
        sensitive.invalidateAll();
    }
}
