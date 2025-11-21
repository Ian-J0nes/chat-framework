package org.marre.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.marre.common.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserService {

    User getUserById(Long id);

    User getUserByUsername(String username);

    User getUserByEmail(String email);

    User createUser(User user);

    User updateUser(User user);

    boolean deleteUser(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> getUserByIds(List<Long> ids);

    Page<User> getUserPage(int page, int size);
}
