-- MySQL dump 10.13  Distrib 9.7.2, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: mental_health_user
-- ------------------------------------------------------
-- Server version	9.7.2

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;

--
-- GTID state at the beginning of the backup 
--

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ 'eb1faa89-b941-11f1-ad30-7c4d8f367c2c:1-703';

--
-- Table structure for table `user`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `gender` tinyint DEFAULT NULL COMMENT '性别 0:未知 1:男 2:女',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `user_type` tinyint DEFAULT '1' COMMENT '用户类型 1:普通用户 2:管理员',
  `status` tinyint DEFAULT '1' COMMENT '状态 0:禁用 1:正常',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `username` (`username`) USING BTREE,
  UNIQUE KEY `email` (`email`) USING BTREE,
  KEY `idx_user_type` (`user_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'admin','admin@example.com','13123456789','$2a$10$qUjCfQLqf9qVt1w1LDoaj.f5TUzBYcXh3FNNA0BhoQ54Vv2cSUt7K','系统管理员','avatar/2026/10/19/c078daa0c05747d28b84b0ff72f72b4c.jpg',1,'2019-09-02',2,1,'2025-08-30 12:00:01','2026-10-19 17:26:25'),(2,'test','15165@qq.com','13132584165','$2a$10$qUjCfQLqf9qVt1w1LDoaj.f5TUzBYcXh3FNNA0BhoQ54Vv2cSUt7K','tyest','/files/bussiness/user_avatar/1757816752928.jpg',1,'2002-08-01',1,1,'2025-08-30 17:22:24','2025-09-14 10:25:54'),(4,'ces','111111111111@qq.com','13123456654','$2a$10$Sy22CXQJe5OAS.Xvmbb60ul9O4a5qi5FtnOjFC2v/Ruk2binJ7vEm','1561',NULL,1,'2025-09-10',1,1,'2025-08-30 18:53:07','2025-11-10 19:07:27'),(5,'范晨hehe','v9blnffzb@gmail.com','13555555555','$2a$10$IdL.2fNevZgsU1ds2JIv.OtjxzrBMXvK7GQcfxIocehmT86tUnSby','李家豪',NULL,1,NULL,1,1,'2026-09-27 17:38:57','2026-09-27 17:38:57'),(6,'凌敏aaa','gncsbt7@outlook.com','15666666666','$2a$10$bC.NdP/wt.0G8HOiys2sEu6lHw0LGz2ECwWZYC3YxgUU1jTrlSWyC','哈诚',NULL,1,NULL,1,1,'2026-09-27 17:40:35','2026-09-27 17:40:35'),(8,'AAA爆裂黎明批发','gncsbt7@gmail.com','19888888888','$2a$10$JcGplRpY5s6fVSnd40l8POZjbos6NdcT9H2q3p116RYkTt7czh3sG','哈诚',NULL,1,NULL,1,1,'2026-09-28 16:20:53','2026-09-28 16:20:53'),(9,'方国芳','izhfoz_gw4@qq.com','18986817264','$2a$10$MLu.Bm7YyfkfyU8J9VIaGee1Bgl08F9hS.tB70/5unEQeclXHCEJS','光佳琪',NULL,2,NULL,1,1,'2026-10-01 09:15:21','2026-10-01 09:15:21'),(10,'快乐的苟分仔','helloworld@qq.com','14899999999','$2a$10$JfA/qPwUUuCGt.GusPh1ceiKS1YPs1qkz12YSoQ1r3UYs3RuufVta','kuailedegoufenzai','avatar/2026/10/19/e2d294fa1bd34cf9a31a3dacdeab9f8d.jpg',0,NULL,1,1,'2026-10-05 09:08:28','2026-10-19 17:25:45'),(11,'快乐的苟分仔2','helloworld@gmail.com',NULL,'$2a$10$tNtdJmSf4Kmn3fn1onmB5.xEz0DE0Wc2IHlDtgd2FKfNfQBTGsaH6','',NULL,0,NULL,1,1,'2026-10-16 13:00:35','2026-10-16 13:00:35');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 15:14:47
