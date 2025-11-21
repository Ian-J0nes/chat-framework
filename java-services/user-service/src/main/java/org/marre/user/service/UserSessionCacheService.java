package org.marre.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.marre.common.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 用户会话缓存服务
 *
 * 功能：
 * 1. 缓存用户信息，避免频繁查询数据库
 * 2. 提供快速的用户身份验证
 * 3. 支持会话失效管理
 */
@Service
public class UserSessionCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserSessionCacheService.class);
    private static final String SESSION_KEY_PREFIX = "user:session:";
    private static final String USER_INFO_KEY_PREFIX = "user:info:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 缓存用户会话信息
     *
     * @param tokenHash JWT token的SHA256哈希值（避免缓存原始token）
     * @param userId 用户ID
     * @param expireSeconds 过期时间（秒）
     */
    public void cacheUserSession(String tokenHash, Long userId, long expireSeconds) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + tokenHash;

            // 创建会话信息
            SessionInfo sessionInfo = new SessionInfo();
            sessionInfo.setUserId(userId);
            sessionInfo.setCachedAt(System.currentTimeMillis());
            sessionInfo.setExpireAt(System.currentTimeMillis() + expireSeconds * 1000);

            // 缓存会话信息
            redisTemplate.opsForValue().set(
                sessionKey,
                sessionInfo,
                Duration.ofSeconds(expireSeconds)
            );

            log.debug("缓存用户会话 tokenHash={}, userId={}, expireSeconds={}",
                tokenHash.substring(0, 8) + "...", userId, expireSeconds);

        } catch (Exception e) {
            log.error("缓存用户会话失败 tokenHash={}, userId={}",
                tokenHash.substring(0, 8) + "...", userId, e);
        }
    }

    /**
     * 缓存用户基本信息
     *
     * @param userId 用户ID
     * @param user 用户对象
     * @param expireSeconds 过期时间（秒）
     */
    public void cacheUserInfo(Long userId, User user, long expireSeconds) {
        try {
            String userKey = USER_INFO_KEY_PREFIX + userId;

            // 创建缓存用户信息（脱敏）
            CachedUserInfo cachedUser = new CachedUserInfo();
            cachedUser.setId(user.getId());
            cachedUser.setUsername(user.getUsername());
            cachedUser.setEmail(user.getEmail());
            cachedUser.setNickname(user.getNickname());
            cachedUser.setAvatar(user.getAvatar());
            cachedUser.setDeleted(user.getDeleted());
            cachedUser.setCachedAt(System.currentTimeMillis());

            redisTemplate.opsForValue().set(
                userKey,
                cachedUser,
                Duration.ofSeconds(expireSeconds)
            );

            log.debug("缓存用户信息 userId={}, username={}", userId, user.getUsername());

        } catch (Exception e) {
            log.error("缓存用户信息失败 userId={}", userId, e);
        }
    }

    /**
     * 获取会话信息
     *
     * @param tokenHash JWT token的哈希值
     * @return 会话信息，不存在返回null
     */
    public SessionInfo getSessionInfo(String tokenHash) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + tokenHash;
            Object cached = redisTemplate.opsForValue().get(sessionKey);

            if (cached instanceof SessionInfo) {
                SessionInfo sessionInfo = (SessionInfo) cached;
                log.debug("命中会话缓存 tokenHash={}, userId={}",
                    tokenHash.substring(0, 8) + "...", sessionInfo.getUserId());
                return sessionInfo;
            }

        } catch (Exception e) {
            log.error("获取会话缓存失败 tokenHash={}",
                tokenHash.substring(0, 8) + "...", e);
        }

        return null;
    }

    /**
     * 获取缓存的用户信息
     *
     * @param userId 用户ID
     * @return 缓存的用户信息，不存在返回null
     */
    public CachedUserInfo getCachedUserInfo(Long userId) {
        try {
            String userKey = USER_INFO_KEY_PREFIX + userId;
            Object cached = redisTemplate.opsForValue().get(userKey);

            if (cached instanceof CachedUserInfo) {
                CachedUserInfo userInfo = (CachedUserInfo) cached;
                log.debug("命中用户信息缓存 userId={}, username={}", userId, userInfo.getUsername());
                return userInfo;
            }

        } catch (Exception e) {
            log.error("获取用户信息缓存失败 userId={}", userId, e);
        }

        return null;
    }

    /**
     * 删除会话缓存
     *
     * @param tokenHash JWT token的哈希值
     */
    public void removeSession(String tokenHash) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + tokenHash;
            redisTemplate.delete(sessionKey);
            log.debug("删除会话缓存 tokenHash={}", tokenHash.substring(0, 8) + "...");

        } catch (Exception e) {
            log.error("删除会话缓存失败 tokenHash={}", tokenHash.substring(0, 8) + "...", e);
        }
    }

    /**
     * 删除用户信息缓存
     *
     * @param userId 用户ID
     */
    public void removeUserInfo(Long userId) {
        try {
            String userKey = USER_INFO_KEY_PREFIX + userId;
            redisTemplate.delete(userKey);
            log.debug("删除用户信息缓存 userId={}", userId);

        } catch (Exception e) {
            log.error("删除用户信息缓存失败 userId={}", userId, e);
        }
    }

    /**
     * 会话信息
     */
    public static class SessionInfo {
        private Long userId;
        private Long cachedAt;
        private Long expireAt;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getCachedAt() { return cachedAt; }
        public void setCachedAt(Long cachedAt) { this.cachedAt = cachedAt; }
        public Long getExpireAt() { return expireAt; }
        public void setExpireAt(Long expireAt) { this.expireAt = expireAt; }
    }

    /**
     * 缓存的用户信息（脱敏）
     */
    public static class CachedUserInfo {
        private Long id;
        private String username;
        private String email;
        private String nickname;
        private String avatar;
        private Integer deleted;
        private Long cachedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public Integer getDeleted() { return deleted; }
        public void setDeleted(Integer deleted) { this.deleted = deleted; }
        public Long getCachedAt() { return cachedAt; }
        public void setCachedAt(Long cachedAt) { this.cachedAt = cachedAt; }
    }
}