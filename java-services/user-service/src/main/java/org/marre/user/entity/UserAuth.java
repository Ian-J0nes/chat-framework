package org.marre.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户认证信息（仅存储密码哈希）。
 * 入参/出参：不在 Controller 中直接暴露完整对象，以避免敏感字段泄露。
 */
@Data
@TableName("user_auth")
public class UserAuth {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String passwordHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

