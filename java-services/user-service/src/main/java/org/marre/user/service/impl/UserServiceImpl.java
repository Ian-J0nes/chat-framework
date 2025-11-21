package org.marre.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.marre.common.entity.User;
import org.marre.user.mapper.UserMapper;
import org.marre.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public User getUserById(Long id) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User getUserByUsername(String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User getUserByEmail(String email) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", email);
        return userMapper.selectOne(queryWrapper);
    }

    @Override
    public User createUser(User user) {
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
        return user;
    }

    @Override
    public User updateUser(User user) {
        user.setUpdateTime(LocalDateTime.now());

        userMapper.updateById(user);

        return user;
    }

    @Override
    public boolean deleteUser(Long id) {
        userMapper.deleteById(id);
        return true;
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.selectCount(
                new QueryWrapper<User>().eq("username", username)) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.selectCount(
                new QueryWrapper<User>().eq("email", email)) >0;
    }

    @Override
    public List<User> getUserByIds(List<Long> ids) {
        return userMapper.selectBatchIds(ids);
    }

    @Override
    public Page<User> getUserPage(int page, int size) {

        Page<User> pageInfo = new Page<>(page, size);
        return userMapper.selectPage(pageInfo, null);
    }
}
