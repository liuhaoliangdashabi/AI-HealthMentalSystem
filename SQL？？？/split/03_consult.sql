-- ===== 库: mental_health_consult =====
-- 表: consultation_session, consultation_message
SET NAMES utf8mb4;
USE `mental_health_consult`;

DROP TABLE IF EXISTS `consultation_session`;
CREATE TABLE `consultation_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话标题',
  `started_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `last_emotion_analysis` json DEFAULT NULL COMMENT '最后一次情绪分析结果(JSON格式)',
  `last_emotion_updated_at` datetime DEFAULT NULL COMMENT '最后一次情绪分析更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_session` (`user_id`,`started_at`) USING BTREE,
  KEY `idx_last_emotion_updated_at` (`last_emotion_updated_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='咨询会话表';

DROP TABLE IF EXISTS `consultation_message`;
CREATE TABLE `consultation_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `sender_type` tinyint NOT NULL COMMENT '发送者类型 1:用户 2:AI助手',
  `message_type` tinyint DEFAULT '1' COMMENT '消息类型 1:文本',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `emotion_tag` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '情绪标签',
  `ai_model` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用的AI模型',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_session_message` (`session_id`,`created_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=255 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='咨询消息表';

