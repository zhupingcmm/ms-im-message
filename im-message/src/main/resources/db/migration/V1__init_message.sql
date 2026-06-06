-- 消息正文表（im_message 库）
-- P0：单聊文本，按 conversation_id 组织；后续 P3 接入 ShardingSphere 按 conversation_id 分片
CREATE TABLE IF NOT EXISTS `message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `conversation_id` VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `msg_id`          BIGINT       NOT NULL COMMENT '全局消息ID(雪花)',
    `seq`             BIGINT       NOT NULL COMMENT '会话内递增序号 conv_seq',
    `sender_id`       BIGINT       NOT NULL COMMENT '发送者',
    `type`            TINYINT      NOT NULL DEFAULT 1 COMMENT '1text 2image 3file 4voice',
    `content`         TEXT         DEFAULT NULL COMMENT '文本内容/结构化体',
    `media_url`       VARCHAR(512) DEFAULT NULL COMMENT '富媒体URL',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1normal 2recalled',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conv_seq` (`conversation_id`, `seq`),
    UNIQUE KEY `uk_msg_id` (`msg_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '消息正文';
