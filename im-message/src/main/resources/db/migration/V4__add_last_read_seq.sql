-- 已读位点：会话所有者已读到的会话内序号（用于已读回执与未读计算）
ALTER TABLE `conversation`
    ADD COLUMN `last_read_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '已读到的 conv_seq';
