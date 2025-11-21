-- ===============================
-- chat_user 数据库
-- ===============================

-- 用户表

CREATE TABLE `users` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
`username` varchar(50) NOT NULL COMMENT '用户名',
`email` varchar(100) NOT NULL COMMENT '邮箱',
`nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
`avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`deleted` tinyint DEFAULT '0' COMMENT '删除标记 0:正常 1:删除',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_username` (`username`),
UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户认证表（新增：2025-09-12）

CREATE TABLE `user_auth` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
`user_id` bigint NOT NULL COMMENT 'users.id',
`password_hash` varchar(100) NOT NULL COMMENT 'BCrypt 哈希',
`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证';

-- ===============================
-- chat_data 数据库
-- ===============================

-- 聊天会话表

CREATE TABLE `chat_sessions` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
`session_id` varchar(100) NOT NULL COMMENT '会话标识',
`user_id` bigint NOT NULL COMMENT '用户ID',
`title` varchar(200) DEFAULT NULL COMMENT '会话标题',
`model` varchar(50) DEFAULT NULL COMMENT '使用模型',
`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`deleted` tinyint DEFAULT '0' COMMENT '删除标记',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_session_id` (`session_id`),
KEY `idx_user_id` (`user_id`),
KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 聊天消息表（变更：2025-09-12，新增 request_id 字段 + 唯一索引）

CREATE TABLE `chat_messages` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
`session_id` varchar(100) NOT NULL COMMENT '会话标识',
`user_id` bigint NOT NULL COMMENT '用户ID',
`role` varchar(20) NOT NULL COMMENT '角色 user/assistant/system',
`content` text NOT NULL COMMENT '消息内容',
`model` varchar(50) DEFAULT NULL COMMENT '使用模型',
`request_id` varchar(64) DEFAULT NULL COMMENT '请求ID（幂等用，新增）',
`prompt_tokens` int DEFAULT '0' COMMENT '输入token数',
`completion_tokens` int DEFAULT '0' COMMENT '输出token数',
`total_tokens` int DEFAULT '0' COMMENT '总token数',
`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`deleted` tinyint DEFAULT '0' COMMENT '删除标记',
PRIMARY KEY (`id`),
KEY `idx_session_id` (`session_id`),
KEY `idx_user_id` (`user_id`),
KEY `idx_create_time` (`create_time`),
UNIQUE KEY `ux_chat_messages_session_request` (`session_id`, `request_id`) -- 新增联合唯一索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- token使用统计表

CREATE TABLE `token_usage_stats` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '统计ID',
`user_id` bigint NOT NULL COMMENT '用户ID',
`model` varchar(50) NOT NULL COMMENT '模型名称',
`date` date NOT NULL COMMENT '统计日期',
`total_requests` int DEFAULT '0' COMMENT '总请求数',
`total_tokens` bigint DEFAULT '0' COMMENT '总token数',
`prompt_tokens` bigint DEFAULT '0' COMMENT '输入token数',
`completion_tokens` bigint DEFAULT '0' COMMENT '输出token数',
`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_user_model_date` (`user_id`,`model`,`date`),
KEY `idx_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='token使用统计表';
