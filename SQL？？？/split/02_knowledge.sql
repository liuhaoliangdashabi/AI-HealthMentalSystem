-- ===== 库: mental_health_knowledge =====
-- 表: knowledge_article, knowledge_category, user_favorite
SET NAMES utf8mb4;
USE `mental_health_knowledge`;

DROP TABLE IF EXISTS `knowledge_article`;
CREATE TABLE `knowledge_article` (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文章ID(UUID)',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文章标题',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '文章摘要',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文章内容',
  `cover_image` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面图片',
  `tags` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签',
  `author_id` bigint DEFAULT NULL COMMENT '作者ID',
  `read_count` int DEFAULT '0' COMMENT '阅读次数',
  `status` tinyint DEFAULT '1' COMMENT '状态 1:已发布',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `author_id` (`author_id`) USING BTREE,
  KEY `idx_category_article` (`category_id`,`published_at`) USING BTREE,
  KEY `idx_title` (`title`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识文章表';

DROP TABLE IF EXISTS `knowledge_category`;
CREATE TABLE `knowledge_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `category_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类代码',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '分类描述',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态 0:禁用 1:启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `category_code` (`category_code`) USING BTREE,
  KEY `idx_parent_id` (`parent_id`) USING BTREE,
  KEY `idx_sort_order` (`sort_order`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='知识文章分类表';

DROP TABLE IF EXISTS `user_favorite`;
CREATE TABLE `user_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `article_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文章ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `user_article_unique` (`user_id`,`article_id`) USING BTREE,
  KEY `article_id` (`article_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户收藏表';

