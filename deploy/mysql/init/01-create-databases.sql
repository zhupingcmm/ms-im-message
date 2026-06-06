-- 创建各微服务独立数据库（建表交给各服务的 Flyway 管理）
CREATE DATABASE IF NOT EXISTS `im_user`    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `im_message` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
