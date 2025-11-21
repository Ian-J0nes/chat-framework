package org.marre.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * JWT 黑名单服务
 *
 * 功能：
 * 1. 将已登出的JWT加入黑名单
 * 2. 检查JWT是否在黑名单中
 * 3. 支持批量清理过期的黑名单记录
 */
@Service
public class JwtBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklistService.class);
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 将JWT加入黑名单
     *
     * @param jti JWT的唯一标识符 (jti claim)
     * @param tokenHash JWT token的哈希值（用于日志记录）
     * @param expireAt JWT的过期时间戳（毫秒）
     */
    public void blacklistToken(String jti, String tokenHash, long expireAt) {
        try {
            String blacklistKey = BLACKLIST_KEY_PREFIX + jti;
            long currentTime = System.currentTimeMillis();

            // 如果token已经过期，无需加入黑名单
            if (expireAt <= currentTime) {
                log.debug("Token已过期，无需加入黑名单 jti={}, tokenHash={}",
                    jti, tokenHash.substring(0, 8) + "...");
                return;
            }

            // 计算剩余有效时间
            long remainingSeconds = (expireAt - currentTime) / 1000;
            if (remainingSeconds <= 0) {
                remainingSeconds = 1; // 至少保留1秒
            }

            // 加入黑名单，过期时间与JWT过期时间一致
            BlacklistInfo info = new BlacklistInfo();
            info.setJti(jti);
            info.setTokenHash(tokenHash);
            info.setBlacklistedAt(currentTime);
            info.setExpireAt(expireAt);
            info.setReason("USER_LOGOUT");

            redisTemplate.opsForValue().set(
                blacklistKey,
                info,
                Duration.ofSeconds(remainingSeconds)
            );

            log.info("JWT已加入黑名单 jti={}, tokenHash={}, remainingSeconds={}",
                jti, tokenHash.substring(0, 8) + "...", remainingSeconds);

        } catch (Exception e) {
            log.error("加入JWT黑名单失败 jti={}, tokenHash={}",
                jti, tokenHash.substring(0, 8) + "...", e);
        }
    }

    /**
     * 检查JWT是否在黑名单中
     *
     * @param jti JWT的唯一标识符
     * @return true-在黑名单中，false-不在黑名单中
     */
    public boolean isTokenBlacklisted(String jti) {
        try {
            String blacklistKey = BLACKLIST_KEY_PREFIX + jti;
            Object cached = redisTemplate.opsForValue().get(blacklistKey);

            boolean isBlacklisted = cached != null;

            if (isBlacklisted) {
                log.debug("JWT在黑名单中 jti={}", jti);
            }

            return isBlacklisted;

        } catch (Exception e) {
            log.error("检查JWT黑名单失败 jti={}", jti, e);
            // 异常时放行，避免影响正常用户
            return false;
        }
    }

    /**
     * 从黑名单中移除JWT（管理员操作）
     *
     * @param jti JWT的唯一标识符
     */
    public void removeFromBlacklist(String jti) {
        try {
            String blacklistKey = BLACKLIST_KEY_PREFIX + jti;
            Boolean deleted = redisTemplate.delete(blacklistKey);

            if (Boolean.TRUE.equals(deleted)) {
                log.info("JWT已从黑名单移除 jti={}", jti);
            } else {
                log.debug("JWT不在黑名单中 jti={}", jti);
            }

        } catch (Exception e) {
            log.error("从黑名单移除JWT失败 jti={}", jti, e);
        }
    }

    /**
     * 获取黑名单信息（调试用）
     *
     * @param jti JWT的唯一标识符
     * @return 黑名单信息，不存在返回null
     */
    public BlacklistInfo getBlacklistInfo(String jti) {
        try {
            String blacklistKey = BLACKLIST_KEY_PREFIX + jti;
            Object cached = redisTemplate.opsForValue().get(blacklistKey);

            if (cached instanceof BlacklistInfo) {
                return (BlacklistInfo) cached;
            }

        } catch (Exception e) {
            log.error("获取黑名单信息失败 jti={}", jti, e);
        }

        return null;
    }

    /**
     * 批量清理过期的黑名单记录（定时任务用）
     * 注意：Redis会自动清理过期key，此方法用于手动清理或统计
     */
    public void cleanupExpiredTokens() {
        try {
            // 扫描所有黑名单key
            var keys = redisTemplate.keys(BLACKLIST_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            int cleanedCount = 0;

            for (String key : keys) {
                try {
                    Object cached = redisTemplate.opsForValue().get(key);
                    if (cached instanceof BlacklistInfo) {
                        BlacklistInfo info = (BlacklistInfo) cached;
                        if (info.getExpireAt() <= currentTime) {
                            redisTemplate.delete(key);
                            cleanedCount++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("清理黑名单记录失败 key={}", key, e);
                }
            }

            if (cleanedCount > 0) {
                log.info("清理过期黑名单记录完成 count={}", cleanedCount);
            }

        } catch (Exception e) {
            log.error("批量清理黑名单失败", e);
        }
    }

    /**
     * 黑名单信息
     */
    public static class BlacklistInfo {
        private String jti;
        private String tokenHash;
        private Long blacklistedAt;
        private Long expireAt;
        private String reason;

        public String getJti() { return jti; }
        public void setJti(String jti) { this.jti = jti; }
        public String getTokenHash() { return tokenHash; }
        public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
        public Long getBlacklistedAt() { return blacklistedAt; }
        public void setBlacklistedAt(Long blacklistedAt) { this.blacklistedAt = blacklistedAt; }
        public Long getExpireAt() { return expireAt; }
        public void setExpireAt(Long expireAt) { this.expireAt = expireAt; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}