package org.marre.user.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（HS256）。
 *
 * 增强功能：
 * - 支持 jti (JWT ID) 用于黑名单管理
 * - 提供 token 哈希计算，用于会话缓存
 * - 统一的token验证逻辑
 *
 * 环境变量：
 * - JWT_SECRET（必填，生产环境必须设置）
 * - JWT_ISSUER（可选，默认 chat-microservices）
 * - JWT_EXPIRES_IN（秒，可选，默认 7200）
 */
@Component
public class JwtService {
    private final String issuer;
    private final Algorithm algorithm;
    private final long expiresInSeconds;

    public JwtService() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isEmpty()) {
            secret = "CHANGE_ME_DEV_SECRET";
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.issuer = System.getenv("JWT_ISSUER") != null ? System.getenv("JWT_ISSUER") : "chat-microservices";
        String exp = System.getenv("JWT_EXPIRES_IN");
        this.expiresInSeconds = (exp != null && !exp.isEmpty()) ? Long.parseLong(exp) : 7200L;
    }

    /**
     * 签发 JWT（增强版）。
     * @param userId 用户ID
     * @param username 用户名
     * @return JwtToken 对象（包含token字符串、jti、过期时间等）
     */
    public JwtToken issueToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expireTime = now.plusSeconds(expiresInSeconds);
        String jti = UUID.randomUUID().toString(); // 唯一标识符

        String token = JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expireTime))
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withJWTId(jti) // 设置 jti
                .sign(algorithm);

        return new JwtToken(token, jti, expireTime.toEpochMilli(), userId, username);
    }

    /**
     * 验证 JWT 并返回解码后的对象。
     * @param token Bearer token（不含前缀）
     * @return DecodedJWT（包含 subject/claims/jti）
     */
    public DecodedJWT verify(String token) {
        JWTVerifier verifier = JWT.require(algorithm).withIssuer(issuer).build();
        return verifier.verify(token);
    }

    /**
     * 计算token的SHA-256哈希值（用于会话缓存key）
     * @param token JWT token字符串
     * @return SHA-256 hex字符串
     */
    public String calculateTokenHash(String token) {
        return sha256Hex(token);
    }

    /**
     * 获取token的剩余有效时间（秒）
     * @param decodedJWT 已解码的JWT
     * @return 剩余秒数，已过期返回0
     */
    public long getRemainingSeconds(DecodedJWT decodedJWT) {
        Date expiresAt = decodedJWT.getExpiresAt();
        if (expiresAt == null) {
            return 0;
        }

        long remainingMs = expiresAt.getTime() - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
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
     * JWT Token 信息
     */
    public static class JwtToken {
        private final String token;
        private final String jti;
        private final long expireAt;
        private final Long userId;
        private final String username;

        public JwtToken(String token, String jti, long expireAt, Long userId, String username) {
            this.token = token;
            this.jti = jti;
            this.expireAt = expireAt;
            this.userId = userId;
            this.username = username;
        }

        public String getToken() { return token; }
        public String getJti() { return jti; }
        public long getExpireAt() { return expireAt; }
        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
    }
}

