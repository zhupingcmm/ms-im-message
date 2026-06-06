-- 用户表（im_user 库）
CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL COMMENT '用户ID',
    `username`   VARCHAR(64)  NOT NULL COMMENT '登录名',
    `nickname`   VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `avatar`     VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `phone`      VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    `password`   VARCHAR(100) NOT NULL COMMENT '密码哈希(BCrypt)',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户';
