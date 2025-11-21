package org.marre.user.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.marre.common.entity.User;
import org.marre.common.result.Result;
import org.marre.user.auth.JwtService;
import org.marre.user.service.AuthService;
import org.marre.user.service.JwtBlacklistService;
import org.marre.user.service.UserService;
import org.marre.user.service.UserSessionCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.marre.common.exception.BusinessException;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口：注册、登录、获取当前用户、登出。
 *
 * 增强功能：
 * - 会话缓存：减少数据库查询，提升性能
 * - JWT黑名单：支持安全登出
 * - 统一的token验证逻辑
 */
@RestController
@RequestMapping("/api/user")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtService jwtService;
    private final UserService userService;
    private final UserSessionCacheService sessionCacheService;
    private final JwtBlacklistService blacklistService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            UserService userService,
            UserSessionCacheService sessionCacheService,
            JwtBlacklistService blacklistService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.userService = userService;
        this.sessionCacheService = sessionCacheService;
        this.blacklistService = blacklistService;
    }

    /**
     * 注册接口。
     * 入参：RegisterRequest（username, email, password, nickname）
     * 出参：Result<{ token, user }>
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().isEmpty()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (req.getEmail() == null || req.getEmail().isEmpty()) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new BusinessException(400, "密码不能为空");
        }

        User user = authService.register(req.getUsername(), req.getEmail(), req.getPassword(), req.getNickname());
        JwtService.JwtToken jwtToken = jwtService.issueToken(user.getId(), user.getUsername());

        // 缓存会话信息
        String tokenHash = jwtService.calculateTokenHash(jwtToken.getToken());
        long expireSeconds = (jwtToken.getExpireAt() - System.currentTimeMillis()) / 1000;
        sessionCacheService.cacheUserSession(tokenHash, user.getId(), expireSeconds);
        sessionCacheService.cacheUserInfo(user.getId(), user, expireSeconds);

        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtToken.getToken());
        data.put("user", sanitize(user));

        log.info("用户注册成功 userId={}, username={}", user.getId(), user.getUsername());
        return Result.success(data);
    }

    /**
     * 登录接口（用户名或邮箱 + 密码）。
     * 入参：LoginRequest（username 或 email，二选一；password）
     * 出参：Result<{ token, user }>
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        String principal = req.getUsername() != null && !req.getUsername().isEmpty()
                ? req.getUsername()
                : req.getEmail();
        if (principal == null || principal.isEmpty()) {
            throw new BusinessException(400, "用户名或邮箱必须填写其一");
        }
        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new BusinessException(400, "密码不能为空");
        }

        User user = authService.login(principal, req.getPassword());
        JwtService.JwtToken jwtToken = jwtService.issueToken(user.getId(), user.getUsername());

        // 缓存会话信息
        String tokenHash = jwtService.calculateTokenHash(jwtToken.getToken());
        long expireSeconds = (jwtToken.getExpireAt() - System.currentTimeMillis()) / 1000;
        sessionCacheService.cacheUserSession(tokenHash, user.getId(), expireSeconds);
        sessionCacheService.cacheUserInfo(user.getId(), user, expireSeconds);

        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtToken.getToken());
        data.put("user", sanitize(user));

        log.info("用户登录成功 userId={}, username={}", user.getId(), user.getUsername());
        return Result.success(data);
    }

    /**
     * 获取当前用户信息。
     * 入参：Authorization: Bearer <token>
     * 出参：Result<User>
     */
    @GetMapping("/me")
    public Result<User> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(401, "未授权");
        }

        String token = authorization.substring("Bearer ".length());
        String tokenHash = jwtService.calculateTokenHash(token);

        // 1. 先检查会话缓存
        UserSessionCacheService.SessionInfo sessionInfo = sessionCacheService.getSessionInfo(tokenHash);
        if (sessionInfo != null) {
            UserSessionCacheService.CachedUserInfo cachedUser = sessionCacheService.getCachedUserInfo(sessionInfo.getUserId());
            if (cachedUser != null) {
                User user = new User();
                user.setId(cachedUser.getId());
                user.setUsername(cachedUser.getUsername());
                user.setEmail(cachedUser.getEmail());
                user.setNickname(cachedUser.getNickname());
                user.setAvatar(cachedUser.getAvatar());
                user.setDeleted(cachedUser.getDeleted());
                return Result.success(sanitize(user));
            }
        }

        // 2. 缓存未命中，验证JWT
        DecodedJWT decoded;
        try {
            decoded = jwtService.verify(token);
        } catch (Exception e) {
            log.debug("JWT验证失败", e);
            throw new BusinessException(401, "令牌无效或已过期");
        }

        String jti = decoded.getId();

        // 3. 检查黑名单
        if (jti != null && blacklistService.isTokenBlacklisted(jti)) {
            throw new BusinessException(401, "令牌已失效");
        }

        // 4. 从数据库获取用户信息
        Long userId = Long.parseLong(decoded.getSubject());
        User user = userService.getUserById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 5. 重新缓存会话信息
        long remainingSeconds = jwtService.getRemainingSeconds(decoded);
        if (remainingSeconds > 60) {
            sessionCacheService.cacheUserSession(tokenHash, userId, remainingSeconds);
            sessionCacheService.cacheUserInfo(userId, user, remainingSeconds);
        }

        return Result.success(sanitize(user));
    }

    /**
     * 登出接口。
     * 入参：Authorization: Bearer <token>
     * 出参：Result<String>
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(401, "未授权");
        }

        String token = authorization.substring("Bearer ".length());
        DecodedJWT decoded;
        try {
            decoded = jwtService.verify(token);
        } catch (Exception e) {
            log.debug("登出时JWT验证失败", e);
            throw new BusinessException(401, "令牌无效");
        }

        String jti = decoded.getId();
        Long userId = Long.parseLong(decoded.getSubject());

        // 1. 将JWT加入黑名单
        if (jti != null) {
            String tokenHash = jwtService.calculateTokenHash(token);
            long expireAt = decoded.getExpiresAt().getTime();
            blacklistService.blacklistToken(jti, tokenHash, expireAt);
        }

        // 2. 清除会话缓存
        String tokenHash = jwtService.calculateTokenHash(token);
        sessionCacheService.removeSession(tokenHash);

        log.info("用户登出成功 userId={}, jti={}", userId, jti);
        return Result.success("登出成功");
    }

    private User sanitize(User user) {
        // 目前 User 没有敏感字段；如后续新增，注意在此处脱敏
        return user;
    }

    /** 注册请求体 */
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password;
        private String nickname;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
    }

    /** 登录请求体 */
    public static class LoginRequest {
        private String username;
        private String email;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}

