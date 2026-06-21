-- 用户收件箱表（im_message 库）—— 写扩散
-- 一条单聊消息向收发双方各写一行，各自带本人的 user_seq（多端漫游锚点）。
-- message 存正文唯一副本，inbox 存「谁的第几条收到了哪条消息」的索引。
CREATE TABLE IF NOT EXISTS `inbox` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `owner_id`        BIGINT      NOT NULL COMMENT '收件箱所有者',
    `user_seq`        BIGINT      NOT NULL COMMENT '用户级递增序号(漫游锚点)',
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `msg_id`          BIGINT      NOT NULL COMMENT '指向 message.msg_id',
    `conv_seq`        BIGINT      NOT NULL COMMENT '冗余会话内序号,便于排序',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_userseq` (`owner_id`, `user_seq`),
    KEY `idx_owner_conv` (`owner_id`, `conversation_id`, `conv_seq`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户收件箱(写扩散)';
-- 分片键预留：owner_id（P3 ShardingSphere）
