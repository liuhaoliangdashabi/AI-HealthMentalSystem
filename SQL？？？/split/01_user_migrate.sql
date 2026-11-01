-- ============================================================
-- D11 第 1 步：user 表迁入独立库 mental_health_user
-- 幂等，可重复执行（每次都重建新库里的 user 表）
-- 前提：user-service(8050) 尚未上线，新库里没有新注册用户
-- 若新库已有注册数据，先备份再执行
-- ============================================================
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `mental_health_user`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 1) 用源表结构建表，字段以线上 mental_health_assistant.user 为准
DROP TABLE IF EXISTS `mental_health_user`.`user`;
CREATE TABLE `mental_health_user`.`user` LIKE `mental_health_assistant`.`user`;

-- 2) 全量搬数据，列名写全，避免源表字段顺序变化导致错位
INSERT INTO `mental_health_user`.`user`
    (`id`,`username`,`email`,`phone`,`password`,`nickname`,`avatar`,
     `gender`,`birthday`,`user_type`,`status`,`created_at`,`updated_at`)
SELECT
     `id`,`username`,`email`,`phone`,`password`,`nickname`,`avatar`,
     `gender`,`birthday`,`user_type`,`status`,`created_at`,`updated_at`
FROM `mental_health_assistant`.`user`;

-- 3) 校验一：两个数字必须相等
SELECT
  (SELECT COUNT(*) FROM `mental_health_assistant`.`user`) AS old_cnt,
  (SELECT COUNT(*) FROM `mental_health_user`.`user`)      AS new_cnt;

-- 4) 校验二：AUTO_INCREMENT 必须 >= max(id)+1，否则新注册会撞主键
SELECT AUTO_INCREMENT AS next_id,
       (SELECT MAX(id) FROM `mental_health_user`.`user`) AS max_id
FROM information_schema.TABLES
WHERE TABLE_SCHEMA='mental_health_user' AND TABLE_NAME='user';

-- 5) 校验三：账号名单，应有 admin(管理员) 和 快乐的苟分仔(普通用户)
SELECT id, username, nickname, user_type, status
FROM `mental_health_user`.`user` ORDER BY id;
