package org.marre.user.service;

import org.marre.common.entity.User;

/**
 * 认证服务（注册、登录）。
 */
public interface AuthService {
    /**
     * 注册新用户（含密码哈希存储）。
     * @param username 用户名
     * @param email 邮箱
     * @param password 明文密码
     * @param nickname 昵称（可空）
     * @return 创建后的用户对象
     */
    User register(String username, String email, String password, String nickname);

    /**
     * 登录校验（支持用户名或邮箱）。
     * @param usernameOrEmail 用户名或邮箱
     * @param password 明文密码
     * @return 用户对象（通过则返回，否则抛异常）
     */
    User login(String usernameOrEmail, String password);
}

