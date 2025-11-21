package org.marre.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.marre.common.entity.User;
import org.marre.common.result.Result;
import org.marre.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public Result<User> getUser(@PathVariable Long id){
        User user = userService.getUserById(id);
        return Result.success(user);
    }

    @PostMapping
    public Result<User> createUser(@RequestBody User user){
        User userCreated = userService.createUser(user);
        return Result.success(userCreated);
    }

    @PutMapping("/{id}")
    public Result<User> updateUser(@PathVariable Long id, @RequestBody User user){
        user.setId(id);
        User userUpdated = userService.updateUser(user);
        return Result.success(userUpdated);
    }

    @DeleteMapping("/{id}")
    public Result<User> deleteUser(@PathVariable Long id){
        userService.deleteUser(id);
        return Result.success();
    }

    @GetMapping
    public Result<Page<User>> getUserList(@RequestParam int page, @RequestParam int size){
        Page<User> pageResult = userService.getUserPage(page, size);
        return Result.success(pageResult);
    }
}
