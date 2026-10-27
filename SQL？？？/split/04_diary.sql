-- ===== 库: mental_health_diary =====
-- 表: emotion_diary, ai_analysis_task
SET NAMES utf8mb4;
USE `mental_health_diary`;

DROP TABLE IF EXISTS `emotion_diary`;
CREATE TABLE `emotion_diary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日记ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `diary_date` date NOT NULL COMMENT '日记日期',
  `mood_score` tinyint NOT NULL COMMENT '情绪评分(1-10)',
  `dominant_emotion` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主要情绪',
  `emotion_triggers` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '情绪触发因素',
  `diary_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '日记内容',
  `sleep_quality` tinyint DEFAULT NULL COMMENT '睡眠质量(1-5)',
  `stress_level` tinyint DEFAULT NULL COMMENT '压力水平(1-5)',
  `ai_emotion_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'AI情绪分析结果(JSON格式)',
  `ai_analysis_updated_at` datetime DEFAULT NULL COMMENT 'AI分析更新时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `user_date_unique` (`user_id`,`diary_date`) USING BTREE,
  KEY `idx_user_diary` (`user_id`,`diary_date`) USING BTREE,
  KEY `idx_ai_analysis_time` (`ai_analysis_updated_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='情绪日记表';

DROP TABLE IF EXISTS `ai_analysis_task`;
CREATE TABLE `ai_analysis_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `diary_id` bigint NOT NULL COMMENT '日记ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务状态：PENDING-待处理，PROCESSING-处理中，COMPLETED-已完成，FAILED-失败',
  `task_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务类型：AUTO-自动触发，MANUAL-手动触发，ADMIN-管理员触发，BATCH-批量触发',
  `priority` int NOT NULL DEFAULT '2' COMMENT '优先级：1-低，2-正常，3-高，4-紧急',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `max_retry_count` int NOT NULL DEFAULT '3' COMMENT '最大重试次数',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `started_at` datetime DEFAULT NULL COMMENT '处理开始时间',
  `completed_at` datetime DEFAULT NULL COMMENT '处理完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_diary_id` (`diary_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_task_type` (`task_type`) USING BTREE,
  KEY `idx_priority` (`priority`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE,
  KEY `idx_status_priority` (`status`,`priority`) USING BTREE,
  KEY `idx_status_created_at` (`status`,`created_at`) USING BTREE,
  KEY `idx_task_type_created_at` (`task_type`,`created_at`) USING BTREE,
  KEY `idx_retry_status` (`status`,`retry_count`,`max_retry_count`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI分析任务表';

