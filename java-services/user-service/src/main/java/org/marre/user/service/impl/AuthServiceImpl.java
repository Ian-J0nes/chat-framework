package org.marre.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.marre.common.entity.User;
import org.marre.user.entity.UserAuth;
import org.marre.user.mapper.UserAuthMapper;
import org.marre.user.service.AuthService;
import org.marre.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserService userService;

    @Autowired
    private UserAuthMapper userAuthMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public User register(String username, String email, String password, String nickname) {
        if (userService.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (userService.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        user = userService.createUser(user);

        UserAuth auth = new UserAuth();
        auth.setUserId(user.getId());
        auth.setPasswordHash(encoder.encode(password));
        auth.setCreateTime(LocalDateTime.now());
        auth.setUpdateTime(LocalDateTime.now());
        userAuthMapper.insert(auth);

        return user;
    }

    @Override
    public User login(String usernameOrEmail, String password) {
        User user = usernameOrEmail.contains("@")
                ? userService.getUserByEmail(usernameOrEmail)
                : userService.getUserByUsername(usernameOrEmail);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在或密码错误");
        }
        UserAuth auth = userAuthMapper.selectOne(new QueryWrapper<UserAuth>().eq("user_id", user.getId()));
        if (auth == null || !encoder.matches(password, auth.getPasswordHash())) {
            throw new IllegalArgumentException("用户不存在或密码错误");
        }
        return user;
    }
}

