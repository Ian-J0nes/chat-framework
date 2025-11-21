package org.marre.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * A wrapper around RedisRateLimiter that "fails open":
 * - Delegates to RedisRateLimiter under normal conditions
 * - On Redis error or timeout, allows the request instead of blocking it
 */
public class FailOpenRedisRateLimiter implements RateLimiter<RedisRateLimiter.Config> {
    private static final Logger log = LoggerFactory.getLogger(FailOpenRedisRateLimiter.class);

    private final RedisRateLimiter delegate;
    private final Duration timeout;

    public FailOpenRedisRateLimiter(RedisRateLimiter delegate, Duration timeout) {
        this.delegate = delegate;
        this.timeout = timeout != null ? timeout : Duration.ofMillis(1200);
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return delegate
                .isAllowed(routeId, id)
                .timeout(timeout)
                .onErrorResume(ex -> {
                    if (log.isWarnEnabled()) {
                        log.warn("RateLimiter fallback ALLOW (routeId={}, id={}): {}", routeId, id, ex.toString());
                    }
                    return Mono.just(new Response(true, Collections.emptyMap()));
                });
    }

    @Override
    public Class<RedisRateLimiter.Config> getConfigClass() {
        return delegate.getConfigClass();
    }

    @Override
    public RedisRateLimiter.Config newConfig() {
        return delegate.newConfig();
    }

    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        // Delegate current effective config map
        return delegate.getConfig();
    }
}
