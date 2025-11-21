package org.marre.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 限流 Key 解析器（KISS）：
 * - 优先 "X-RateLimit-Key"（方便测试/定位）；
 * - 其次 Authorization（记录为 SHA-256 哈希，避免日志泄露原始 Token）；
 * - 再次 X-Forwarded-For；
 * - 最后 remoteAddress。
 * 返回：同 Key 下共享令牌桶。
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    /**
     * KeyResolver Bean
     * 入参：ServerWebExchange（由 Gateway 传入）
     * 返参：限流 key（同 key 共享令牌桶配额）
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.defer(() -> {
            // 1) 显式 Key（测试/排错最稳妥）
            String explicit = exchange.getRequest().getHeaders().getFirst("X-RateLimit-Key");
            if (explicit != null && !explicit.isBlank()) {
                String key = "xrl:" + explicit;
                if (log.isDebugEnabled()) log.debug("RateLimit key(explicit)={}", key);
                return Mono.just(key);
            }

            // 2) Authorization：使用哈希作为 Key，避免在日志中泄露原始值
            String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (auth != null && !auth.isBlank()) {
                String key = "auth:" + sha256Hex(auth);
                if (log.isDebugEnabled()) log.debug("RateLimit key(auth)={}", key);
                return Mono.just(key);
            }

            // 3) XFF：取第一个地址
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String key = xff.split(",")[0].trim();
                if (log.isDebugEnabled()) log.debug("RateLimit key(xff)={}", key);
                return Mono.just(key);
            }

            // 4) 远端地址
            InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
            String ip = (addr != null && addr.getAddress() != null) ? addr.getAddress().getHostAddress() : "unknown";
            if (log.isDebugEnabled()) log.debug("RateLimit key(ip)={}", ip);
            return Mono.just(ip);
        });
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // 极少见；兜底为 hashCode（不会打印原文到日志）
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * Fail-open RateLimiter：当 Redis 超时/错误时放行，避免请求被长时间阻塞。
     * 可通过 app.ratelimit.failOpenTimeoutMs 控制超时阈值，默认 1200ms。
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public RateLimiter<RedisRateLimiter.Config> failOpenRateLimiter(
            RedisRateLimiter delegate,
            @Value("${app.ratelimit.failOpenTimeoutMs:1200}") long timeoutMs) {
        return new FailOpenRedisRateLimiter(delegate, java.time.Duration.ofMillis(timeoutMs));
    }
}
