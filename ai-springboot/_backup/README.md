# _backup —— 拆微服务过程中移出的旧代码

**这些代码已废弃，不再维护。** 保留原因：`ai-springboot/.git` 没有任何提交，删掉就无法回滚。
确认新服务稳定运行一段时间（建议 2 周以上）后，整个目录可以直接删除。

## 目录

| 子目录 | 来源 | 时间 | 数量 | 内容 |
|---|---|---|---|---|
| `d10/` | D10 下线 web 咨询接口 | 2026-11-01 | 15 个 | 咨询域：`PsycholigicalChatController`、`ConsultationSessionsService`、`ConsultationMessageService`、`KnowledgeConsultationService`、`ConsultationConvert`、`AiTaskConsumer`、`AiTaskQueueService`、`AiService/*`（EmotionAnalysisService / PsychologicalSupportService / StructOutPut）、4 个 Consultation DTO |
| `d12/` | D12 下线 web 用户域 + 文件域 | 2026-11-02 | 22 个 | **用户域 14**：`UserController`、`UserService`、`OnlineUserService`、`UserMapper`、`UserConvert`、`entity/User`、`SignService`、`DiaryService`、`UserActivityItem`、3 个 `User*CommandDTO`、`UserStatus`、`UserType`<br>**文件域 8**：`FileService`、`FileCompensator`、`FileMapper`、`SysFileInfo`、`OSSUtil`、`OssConfig`、`OssClientConfig`、`AsyncConfig` |

两批的相对路径都按原位置保留：`d10/` 以 `org/example/aispringboot/` 下一级为根，`d12/` 多套了一层 `../analytics-service`。

## 这些代码现在归谁

| 原功能 | 现在的归属 |
|---|---|
| 咨询会话 / SSE / AI 任务队列 | `consult-service` (8040) |
| 登录 / 注册 / 签到 / 用户资料 | `user-service` (8050) |
| 文件上传 / OSS / 孤儿清理 | `file-service` (8060) |
| 情绪日记 / 贡献日历 | `diary-service` (8030) |
| 知识库 | `knowledge-service` (8020) |

## 还原方法

按原路径搬回 `../analytics-service` 对应位置，然后：

```bash
mvn -o -pl web -am clean package -DskipTests
```

**必须 `clean`**：Maven 和 IDEA 的增量编译都不会删除源文件已删的 `.class`，残留的 class 会被 Spring 继续扫成 Bean。
