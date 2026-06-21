-- 会话表（im_message 库）—— 每用户的"收件箱"索引
-- P0：登录后拉会话列表 + 未读数；按 owner_id 聚合，每条对应一个对端 peer。
-- 一条单聊消息会维护两行：(owner=发送方, peer=接收方) 与 (owner=接收方, peer=发送方)。
CREATE TABLE IF NOT EXISTS `conversation` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `owner_id`        BIGINT       NOT NULL COMMENT '会话归属用户(收件箱所有者)',
    `peer_id`         BIGINT       NOT NULL COMMENT '对端用户',
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话ID s_{小}_{大}',
    `last_msg_id`     BIGINT       DEFAULT NULL COMMENT '最后一条消息ID(雪花)',
    `last_seq`        BIGINT       DEFAULT NULL COMMENT '最后一条消息会话内序号',
    `last_content`    VARCHAR(255) DEFAULT NULL COMMENT '最后一条消息预览(截断)',
    `last_msg_time`   DATETIME     DEFAULT NULL COMMENT '最后一条消息时间',
    `unread_count`    INT          NOT NULL DEFAULT 0 COMMENT '未读数',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_peer` (`owner_id`, `peer_id`),
    KEY `idx_owner_time` (`owner_id`, `last_msg_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '会话(每用户收件箱)';
