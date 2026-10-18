# Redis 接入进度

> **这是本项目的唯一进度文档。** 想知道「Redis 接得怎么样了、下一步干什么」，读这一篇就够了，不用翻别的文件，也不用重新扫代码。
>
> 更新日期：**2026-10-15**
>
> `docs/` 下另外两篇（`DTO怎么写.md`、`事务练习-删除知识分类.md`）是**知识笔记**，不含进度信息。
>
> ⚠️ **用户约定（AI 必须遵守）**：**冗余 import 不用提醒** —— 用户会按 IDE 提示自己清理。**不要**把"清理无用 import"写进待办清单、review 结论或本文档的待办项。

---

## 0. AI 先读这段（当前进度快照）

### 0.1 一句话状态

**第 0 关 ✅、第 1 关（场景A 分类树 + 场景B 文章详情）✅、第 2 关（浏览量 `INCR` + 定时回写）✅、第 3 关（JWT 登出黑名单）✅、第 4 关（热门文章排行榜 ZSet）✅ —— 全部完成并实测验收。**

**第 1 关欠的「兜底账」也已还清 ✅**（4 处代码 + 1 处配置，见 **9.4**）：手写流 4 处 try-catch、注解流 `CacheErrorHandler`、`timeout` 3000→500ms，全部落地。顺带把 **8.6-#7 的 `jti`** 也补了。

**第 4 关收官记录见第十节**（2026-10-14）：定为「**最近 7 天热度**」+「**天级 key 实时累加 + 定时 `ZUNIONSTORE` 预热**」，前端「推荐阅读」已从累计榜切到新接口。

**✅ 第 5 关（限流 + 防重复提交）已完成**（2026-10-15，记录见**第十一节**）：
场景 A（登录/注册限流，滑动窗口 + 用户名/IP 双维度）和场景 B（日记 `SET NX` 防重）**均已实现并实测**；两个编译错误已清，`DiaryService` 的 `expire` 段已删。
⏭️ **`@RateLimit` 注解 + 切面「有意推迟」** —— 用户决定**做完第 7 关后**统一做「手写流 → 注解流」改造（见 **11.6**）。**这不是漏做，是有意安排。**
📌 **部署前检查项**：`getClientIp()` 现在只取 `getRemoteAddr()` —— **本地无 Nginx 时这是对的**，上反代前必修（见 **11.4**）。

**✅ 第 6 关（分布式锁）已完成**（2026-10-16，记录见**第十二节**）：`FileCleanupTask` 用 `SET NX EX` 抢锁 + Lua 原子释放（比对 uuid），多实例验收通过。
📌 遗留：`LOCK_TTL = 30 秒` 与"任务最坏耗时"尚未对齐（见 **12.4**）。

📅 **已登记待开发：用户日历模块**（仿 GitHub 贡献图，左右分栏）—— 用户明确要求**第 7 关完成 + 「手写流 → 注解流」改造完成后**开工。⚠️ 开工前必须先定"强度语义"，否则会选错数据结构，详见 **11.7**。

**还剩几笔小账**（不急，见 8.6）：裸拼 SQL / 增量 key 无 TTL。

### 0.2 进度表

| 关卡 | 主题 | 状态 |
|---|---|---|
| **第 0 关** | 地基：依赖 + 配置 + 序列化 | ✅ **完成**（2026-10-08） |
| **第 1 关** | 场景A：分类树缓存（**手写流** Cache Aside） | ✅ **完成**（2026-10-10） |
| **第 1 关** | 场景B：文章详情缓存（**注解流** `@Cacheable`） | ✅ **完成**（2026-10-13） |
| **第 2 关** | 浏览量 `INCR` + 定时回写 | ✅ **完成**（2026-10-11） |
| **第 3 关** | JWT 登出黑名单 ⭐ 最出彩 | ✅ **完成**（2026-10-13，收官记录见**第九节**） |
| **兜底账** | 第 1 关欠的读路径降级 + `timeout` 调优 | ✅ **完成**（2026-10-13，清单见 **9.4**） |
| **第 4 关** | 热门文章排行榜（ZSet） | ✅ **完成**（2026-10-14，收官记录见**第十节**） |
| **第 5 关** | 限流 + 防重复提交 | ✅ **完成**（2026-10-15，收官记录见**第十一节**）；⏭️ 注解流改造推迟到第 7 关后（见 11.6） |
| **第 6 关** | 分布式锁 | ✅ **完成**（2026-10-16，收官记录见**第十二节**）；⚠️ TTL 与任务耗时待对齐（见 12.4） |
| 第 7 关 | 高级数据结构 | 🔧 进行中（2026-10-16 起）；场景 1 BitMap 签到 ✅、**场景 2 HyperLogLog UV ✅（2026-10-17，见第十三节）**、**场景 4 文件秒传 ✅**、**场景 5 AI 结果缓存 ✅**（后两个见第十四节）、场景 3 Stream 任务队列 ⬜（明天单独打） |
| 第 8 关 | 面试包装 | ⬜ 未开始 |

> 第 1 关的「三大问题」已讨论完，**只有"穿透"落地了**（见 8.4）。

### 0.3 已经落地的文件（接手前先看这张表）

| 文件 | 状态 |
|---|---|
| `config/RedisConfig.java` | ✅ `RedisTemplate<String,Object>`（JSON + `JavaTimeModule`） |
| `config/CacheConfig.java` | ✅ `@EnableCaching` + `RedisCacheManager`（JSON 序列化 + TTL）+ **`implements CachingConfigurer` 自定义 `CacheErrorHandler`（注解流降级）** |
| `service/KnowledgeCategoryService.java` | ✅ 场景A：`listAll` 手写 Cache Aside（**get / set 各自 try-catch 降级**）+ `evictCategoryTreeCache`（`try-catch` 降级）+ **`afterCommit` 钩子** |
| `service/ArticleContentService.java` | ✅ 场景B：**拆出来的纯读 bean**，`loadDetail` 贴 `@Cacheable` |
| `service/KnowledgeArticleService.java` | ✅ 三个写点贴 `@CacheEvict`；`getArticleDetail` 只做「取内容 → 权限 → 计数」，**计数两处已 try-catch 降级**；**第 4 关：`getArticleDetail` 里 `ZINCRBY` 日热度（+8 天 TTL）、`getHotArticle()` 读榜（只包 Redis 的兜底）、`complementArticleDTO` 共用补名字逻辑** |
| `common/ArticleReadCountTask.java` | ✅ 第 2 关回写任务：`pop` 认领 + `getAndDelete` + 失败 `SADD` 放回；**第 4 关：`preheatArticleHot()` 每 30 秒 `ZUNIONSTORE` 预热 7 天榜（TTL 90 秒）** |
| `common/FileCleanupTask.java` | ✅ **第 6 关：`SET NX EX` 抢锁（`lock:fileCleanup`，TTL 30 秒）+ `try/finally` 里 Lua 原子释放**（比对 uuid 才删）；⚠️ cron 是 **6 字段** `0 */20 * * * ?`，别写成 Quartz 的 7 字段 |
| `controller/KnowledgeController.java` | ✅ 第 4 关：`GET /api/knowledge/article/hot` |
| `ai-vue/src/views/frontendKnowledge.vue` | ✅ 第 4 关：「推荐阅读」改调 `/knowledge/article/hot`（原先是 `sortField=readCount` 的**累计榜**） |
| `ai-vue/src/api/frontend.js` | ✅ 第 4 关：新增 `getHotArticles()` |
| `service/UserService.java` | ✅ 第 3 关：`logout(token)` 写黑名单（`StringRedisTemplate` + TTL=剩余有效期，**fail-closed**）；✅ **第 5 关：`login` / `register` 开头 `checkIpLimit(request)`，用户名维度 + IP 维度双滑动窗口，超限写 3 分钟黑名单** |
| `service/DiaryService.java` | ✅ **第 5 关：`addEmotionDiary` 用 `SET NX` 防重**（`diary:dup:<userId>`，TTL 15 秒；⚠️ 成功后**不删**锁，等它自然过期） |
| `controller/UserController.java` | ✅ **第 5 关：`login` / `register` 方法签名补 `HttpServletRequest` 并透传给 Service** |
| `util/JwtAuthticationFilter.java` | ✅ 第 3 关：黑名单校验（**fail-open**，Redis 挂则放行） |
| `util/JwtTokenUtil.java` | ✅ `generateToken` 带 **`jti`**（`.withJWTId(UUID...)`）—— 同用户同秒登录不再签出相同 token |

**Redis key 一览**

| key | 谁写的 | 备注 |
|---|---|---|
| `cache:category:tree` | 手写流 | TTL 10 分钟 |
| `article::<id>` | **注解流** | ⚠️ 是 `::` 两个冒号 —— `cacheName + "::" + key` |
| `article:read:delta:<id>` | 第 2 关 | 增量（不是总数）；⚠️ **仍无 TTL 兜底**（8.6-#5） |
| `article:read:dirty` | 第 2 关 | Set，待回写的 id |
| `blacklist:token:<md5(token)>` | 第 3 关 | String，TTL = token **剩余**有效期；⚠️ key 必须 md5，**读写两边要一致** |
| `article:hot:<yyyy-MM-dd>` | 第 4 关 | ZSet，当天热度；TTL **8 天**（自动滚出窗口）；⚠️ **冒号别漏**（`article:hot:` 不是 `article:hot`） |
| `article:hot:7d` | 第 4 关 | ZSet，最近 7 天合并榜；定时任务每 30 秒重建，TTL **90 秒** |
| `login:try:username:<username>` | 第 5 关 | ZSet 滑动窗口（member = `时间戳-UUID`，score = 时间戳）；60 秒内 > **10** 次 → 封 |
| `login:ban:username:<username>` | 第 5 关 | String，用户名黑名单；TTL **3 分钟**；⚠️ 读和写都要拼 `<username>` 后缀 |
| `login:try:ip:<ip>` | 第 5 关 | ZSet 滑动窗口；60 秒内 > **30** 次 → 封；⚠️ `login` 与 `register` **共用**这一个 key |
| `login:ban:ip:<ip>` | 第 5 关 | String，IP 黑名单；TTL **3 分钟**；⚠️ 读和写都要拼 `<ip>` 后缀 |
| `diary:dup:<userId>` | 第 5 关 | String（`SET NX` 防重锁）；TTL **15 秒**；⚠️ 成功提交后**不要** `delete`，等它自然过期 |
| `lock:fileCleanup` | 第 6 关 | String，分布式锁（value = 本次执行的 UUID）；TTL **30 秒**；⚠️ 释放必须用 **Lua 比对 value** 再删，**别直接 `DEL`** |

### 0.4 下一步就做这个

**兜底账 ✅ 已还清**（2026-10-13 对着代码核对，非凭记忆）：

1. ✅ 手写流 4 处 try-catch 降级 —— `KnowledgeCategoryService.listAll` 的 get / set、`KnowledgeArticleService.getArticleDetail` 的 `increment` / `SADD`（见 **9.4-A**）
2. ✅ 注解流 `CacheErrorHandler` —— `CacheConfig` 已 `implements CachingConfigurer`，4 个回调按 warn / error 分级（见 **9.4-B/C**）
3. ✅ `spring.data.redis.timeout` 已从 `3000ms` 调到 `500ms`（见 **9.7 治法①**）
4. ✅ 顺带补了 `JwtTokenUtil` 的 **`jti`**（8.6-#7）

> ⭐ **唯一需要你亲手改的**：`KnowledgeArticleService.getArticleDetail` 里 `delta` 的初值。
> 原来写的是 `Integer delta = null;`，catch 里没赋 0 → Redis 一挂就 `int + null` → **NPE 500**（正是 9.4 第 2 点警告的坑）。
> **已改成 `Integer delta = 0;`** ✅（1 行）

**第 4 关 ✅ 已完成**（2026-10-14，收官记录见**第十节**）。

**✅ 第 5 关已完成**（2026-10-15，收官记录见**第十一节**）。编译错误已清（`sw.prettyPrint()` 已删、`BCryptExecute.java` 已删），`DiaryService` 的 `expire` 段已删。

**⏭️ 已决定推迟**：`@RateLimit` 注解 + 切面 —— 做完第 7 关后统一做「手写流 → 注解流」改造（见 **11.6**）。**这不是漏做，是有意安排。**

**📌 部署前检查项（本地不用动）**：

1. **`getClientIp()` 仍只取 `getRemoteAddr()`** —— 本地无 Nginx 时这是对的；**上反代前必修**，否则所有用户共用一个 IP → 全体被封。详见 **11.4** / **11.6**

**🧹 遗留小优化（不急）**：

2. **黑名单命中应早退** —— `UserService.checkIpLimit` 命中后不要再走 `zadd` / `zCard`
3. `DiaryService` 里 `IP_LIMIT_KEY` 定义了没用（要么用要么删）；"日记重复提交"那行 `log.info` 建议降 `debug`
4. **第 6 关：`LOCK_TTL = 30 秒` 与"任务最坏耗时"未对齐** —— 孤儿文件多时锁会在任务跑完前过期、互斥失效（见 **12.4**）

**✅ 第 6 关已完成**（2026-10-16，收官记录见**第十二节**）：`common/FileCleanupTask.java` 抢锁 + Lua 原子释放，多实例验收通过。

**下一步：第 7 关（高级数据结构）** —— 5 个场景（String+JSON / BitMap / HyperLogLog / Stream / 秒传），锚点已实测校准（见第四节）。

**顺带清了 8.6-#1**（跨模块失效点，已完成）。剩余小账（见 8.6）：`ArticleReadCountTask:45` 裸拼 SQL、`article:read:delta:<id>` 无 TTL。

> ✅ **防穿透已收尾（2026-10-13 对着代码核对，非凭记忆）**：
> `ArticleContentService.loadDetail:36` 已经是 `if(a==null)return null;`，
> 「文章不存在」已挪到 `KnowledgeArticleService.getArticleDetail:151` 抛；
> 且 `CacheConfig` 用的是 `RedisCacheConfiguration.defaultCacheConfig()`，**没有**关掉
> `allowCacheNullValues`（默认 true）→ null 会真的写进缓存 → `unless` 的 null 分支**真正生效**。
> 8.6 第 3 条那句错注释也已不在了。**这条尾巴不用再补。**

### 0.5 硬规则（给接手的人 / AI）

1. **`ai-springboot/` 的代码是只读的。** 约定是**搭架子的人搭架子、用户写主体**：可以给配置片段、给带 TODO 的骨架、给验收标准，**业务逻辑留给用户填**。唯一例外是 `docs/` 下的文档。
   - ⚠️ 想搭架子**先问一句要不要**；架子**直接写进文件**，别让用户在聊天里手抄；架子 = **带 TODO 的代码，不是完整实现**。
2. **Boot 版本决定序列化器，抄错必崩**：本项目 Spring Boot **3.5.15**（Jackson **2**）→ 只能用 `GenericJackson2JsonRedisSerializer`（**带 2**）。网上大量中文教程是 Boot 3 之前写的，抄之前先看包名。
3. **别提议换端口。** 用户明确「就要 8080」。
4. **`AI-mentalHealthSystem/demo/` 是用户自己的练兵场，不要管理它。** 别提议往里加东西、别写大纲。原话：「Demo 不要，这就是个练兵场，这不需要你管理」。参考可以，动手不行。
5. 学习笔记在**另一个仓库**（`RedisNotes`），那里讲原理；本文档只讲**怎么接进这个项目**。

---

## 一、这份文档是什么

学习 Redis 有**两条线**，别混：

| 线 | 载体 | 回答的问题 |
|---|---|---|
| **知识线** | `RedisNotes/1~10.md`（独立仓库） | Redis 是什么、命令怎么敲、为什么快 |
| **落地线**（本文档） | `ai-springboot/docs/Redis接入进度.md` | **这个项目里，哪里该用 Redis、怎么用、用了有什么收益** |

本文档不重复知识线，只做**映射**：把知识线的第 N 课，挂到本项目的具体文件、具体行号上。

> **本文档不写完整代码。** 它写的是：锚点（改哪个文件）+ 方案（用什么结构）+ 产出（应该多出哪些类/注解）+ 验收（`redis-cli` 敲什么能看到效果）。**代码由用户自己写。**

> ℹ️ 历史说明：曾定过「阶段一学全 Redis → 才动阶段二落地」的闸门。**2026-10-08 用户亲自开闸作废了它**（原话大意：学了不少、会最简单的存取，你说什么我干什么）。**别再拿「先学全」拦他，也别劝他回去补课。**

---

## 二、现状盘点

### 2.1 ai-springboot 技术栈（从真实 `pom.xml` 读出来的）

| 项 | 值 | 对 Redis 的影响 |
|---|---|---|
| Spring Boot | **3.5.15** | ⚠️ Jackson 2 → 序列化器**带 2** |
| Java | 17 | 无影响 |
| ORM | MyBatis-Plus 3.5.7 | 缓存注入点在 `service/` 层 |
| 安全 | spring-security + java-jwt 4.5.2 | 登出黑名单要动 filter 链 |
| 工具 | Hutool 5.8.25 | 有现成工具类，别重复造 |
| AI | spring-ai-starter-model-openai 1.1.8 → SiliconFlow | **AI 调用按量计费 → 结果缓存有真金白银的收益** |
| 存储 | 阿里云 OSS (alibabacloud-oss-v2) | 秒传场景 |
| DB | MySQL `mental_health_assistant` | — |
| 端口 | 8080（yml 里没写 `server.port`，用默认值） | — |

### 2.2 这个项目为什么值得接 Redis（真实锚点）

不是「为了用而用」，下面每一条都能对应到代码里的一个具体缺陷或成本：

| 锚点（文件:行） | 现状 | Redis 能解决什么 |
|---|---|---|
| `controller/UserController.java:51` `logout()` | **空实现**，只打日志就 `Result.success()` | JWT 无状态 → 登出后旧 token 仍然有效，**这是真实安全漏洞**。Redis 黑名单可修 |
| `entity/KnowledgeArticle.java:62` `readCount` | 字段存在，详情接口没看到维护逻辑 | 浏览量 `INCR` + 定时回写，避免每看一次打一次库 |
| `controller/KnowledgeController.java:38` `categoryTree()` | 每次请求都查库 | 分类树读多写少、变更极少 → 缓存收益率最高 |
| `controller/KnowledgeController.java:92` `getArticleDetail()` | 每次请求都查库 | 文章详情是典型热点读 |
| `entity/UserFavorite.java` | 只有 `(userId, articleId)`，无去重保障 | 收藏/点赞去重 → Set；计数 → `INCR` |
| `controller/UserController.java:40` `login()` | ~~无限流~~ **已限流 ✅**（第 5 关） | 登录接口是撞库的天然目标 → 已用**滑动窗口 + 双维度黑名单**解决，见**第十一节** |
| `controller/UserController.java:57` `addEmotionDiary()` | 无幂等 | 前端狂点会写入多条日记 → `SET NX` 防重 |
| `common/FileCleanupTask.java` + `entity/SysFileInfo.java` | 定时清理临时文件 | 多实例部署会重复跑 → 分布式锁 |
| `entity/AiAnalysisTask.java` | 有 `status`/`priority`/`retryCount`/`maxRetryCount` | 这本身就是一张任务队列表 → Stream / ZSet 延时队列 |
| `entity/EmotionDiary.java:35` `diaryDate` + `moodScore` | 一天一条 | 天然是「签到」模型 → BitMap 统计连签 |
| `service/AnalyticsService.java` | 聚合统计，开销大 | 缓存 + 定时刷新；UV 用 HyperLogLog |

> ⚠️ 分类相关接口**只有**读写两类：`GET /knowledge/category/tree`、`DELETE /knowledge/category/{id}`、`DELETE /knowledge/category/{id}/{goToId}`。**没有新建/修改分类的接口** —— 这点在讨论 TTL 时很关键（见 5.3）。

---

## 三、防坑清单（写代码前过一遍）

| # | 坑 | 正确做法 |
|---|---|---|
| 1 | **序列化器版本** | Boot 3.5.15 → Jackson 2 → `GenericJackson2JsonRedisSerializer`（**带 2**）。判据：看 Jackson 包名，出现 `tools.jackson` 才用不带 2 的 |
| 2 | **配置前缀** | 是 `spring.data.redis.*`。写 `spring.redis.*`（Boot 2.x 老写法）**理论上不报错、静默忽略**（Spring Boot 宽松绑定不认识的前缀不报错）。⚠️ **这条没在真机验证过** —— 想确认就把 `spring.data.redis.port` 删掉、只留 `spring.redis.port: 6380`，看它是不是照连 6379 |
| 3 | **没密码就别写 password** | 写 `spring.data.redis.password=`（空值）Lettuce 仍会发 `AUTH` → `ERR Client sent AUTH, but no password is set` → 连接失败。**整行不写** |
| 4 | **`@class` 不是万能的** ⭐ | 序列化器靠 Jackson 类型信息写 `@class`，但 Spring Data 的解析器**对它没法实例化的类会省略类型信息** → 写出来是**裸数组** → 读的时候在「解析类型 id」阶段就抛 `SerializationException`。**典型受害者：`List.of()` / `Stream.toList()` / `Set.of()` / `Arrays.asList()` 返回的 JDK 内部类**。往 Redis 存的集合**必须**是 `new ArrayList<>(...)` 或 `collect(Collectors.toCollection(ArrayList::new))`。**已实测复现**，详见 5.1 |

**本机 Redis 环境**

- 服务端：`E:\Downloads\Redis-8.10.2-Windows-x64-msys2-with-Service\...\`（`redis-server.exe` / `redis-cli.exe` / `redis.conf` 都在这一层）
- 启动方式：**手工跑 `redis-server`**，没注册 Windows 服务、没带配置文件 → 默认 `protected-mode yes` + 无密码
- 端口 6379，无密码，`database 0`
- ⚠️ 真实下载目录在 **`E:\Downloads`**，不是 `C:\Users\lenovo\Downloads`（那个是空的）
- ✅ **Redis 目前是启动状态**（2026-10-08 起可正常读写）

**验收入口**

```bash
redis-cli.exe ping            # → PONG
redis-cli.exe --raw --scan    # ⚠️ 用 --scan，别用 KEYS *（Git Bash 会把 * 展开成文件名）
DBSIZE                        # 当前库几个 key
TTL cache:category:tree       # -1 永久 / -2 不存在 / 正数 = 剩余秒
```

---

## 四、落地路线图：功能点 → 知识点

> 分 9 关（第 0 关是地基）。每关给出：**锚点 / 方案 / 产出 / 验收 / 对应知识课**。
>
> 关卡之间也有依赖：后一关会用到前一关的基建，跳着做会返工。

| 关 | 主题 | 必须已学完 | 依赖的基建 |
|---|---|---|---|
| 0 | 地基（依赖 / 配置 / 序列化） | 第 2 课 | — |
| 1 | 缓存实战 + 三大问题 + 一致性 | 第 3、4 课 | 第 0 关 |
| 2 | 浏览量 `INCR` + 定时回写 | 第 1、8 课 | 第 0 关 |
| 3 | JWT 登出黑名单 | 第 1、8 课 | 第 0 关 |
| 4 | 热门文章排行榜（ZSet） | 第 3、6 课 | 第 0 关 |
| 5 | 限流 + 防重复提交 | 第 5 课 | 第 0 关 |
| 6 | 分布式锁 | 第 5 课 | 第 0 关 |
| 7 | 高级数据结构 | 第 6 课 | 第 0、1 关 |
| 8 | 面试包装 | 第 9 课 | 第 1~7 关 |

> ⚠️ 第 1 关排在最前面是有意的：缓存一致性、三大问题是**面试权重最高**的（⭐⭐⭐⭐⭐），也是最容易「知道但讲不出来」的。别因为它叫「第 1 关」就急着做完往下走。

---

### 第 0 关：地基（依赖 + 配置 + 序列化）✅ 已完成

| 项 | 内容 |
|---|---|
| **锚点** | `pom.xml`、`src/main/resources/application.yml`、`config/RedisConfig.java` |
| **方案** | `spring-boot-starter-data-redis`（**不写 version**，由 Boot parent 管）；`spring.data.redis.host/port/database/timeout`；`redisconfig` 暴露 `RedisTemplate<String,Object>`（JSON 序列化） |
| **产出** | `config/RedisConfig.java`（实际实现见附录 A）；能同时注入 `StringRedisTemplate` 和 `RedisTemplate<String,Object>` |
| **验收** | ✅ 启动不报错；✅ 测试 `src/test/java/.../Test/Redis/RedisStartTest.java` 跑通；✅ `redis-cli --raw GET <key>` 看到干净 JSON |
| **顺带完成** | 用户把 **8 个 response DTO 全都补了 `@NoArgsConstructor + @AllArgsConstructor`**（原计划留到第 1 关做） |
| **知识课** | 第 2 课（序列化） |

> 💡 为什么 value 用 JSON 而不是 `StringRedisTemplate` 一把梭：本项目缓存的是 `ArticleResponseDTO`、分类树这类**对象**，手写 `ObjectMapper` 转换会在每个 Service 里重复一遍。但**计数、锁、黑名单这类单值仍用 `StringRedisTemplate`**，别把简单的搞复杂。

---

### 第 1 关：缓存实战（三大问题 + 一致性）⭐⭐⭐⭐⭐ ✅ 已完成（2026-10-13）

**这是面试第一高频，也是收益率最高的一关。选两个对比鲜明的场景做。**

#### 场景 A：分类树缓存 —— 最干净的缓存（✅ 已完成，见第五节）

| 项 | 内容 |
|---|---|
| **锚点** | `controller/KnowledgeController.java:38` `categoryTree()` → `service/KnowledgeCategoryService.java` |
| **写操作（要删缓存的）** | `KnowledgeController.java:46` 和 `:54` 两个 `deleteCategory` |
| **方案** | 手动 Cache Aside（没用 `@Cacheable` 注解，直接 `redisTemplate` 读写，便于逐步理解） |
| **为什么它最简单** | 全量一个 key、变更极少、数据量小 → 不会遇到分页 key 爆炸 |

#### 场景 B：文章详情缓存 —— 一致性训练场（✅ 已完成，见 8.3）

| 项 | 内容 |
|---|---|
| **锚点** | `KnowledgeController.java:92` `getArticleDetail(@PathVariable String id)` ⚠️ `KnowledgeArticle.id` 是 **String** |
| **写操作（三个，都要删缓存）** | `:100` `updateArticleDetail`、`:108` `deleteArticle`、`:84` `putArticleStatus` |
| **方案** | `@Cacheable(value="article", key="#id")` + 三个写方法 `@CacheEvict(value="article", key="#id")` |
| **一致性要求** | **Cache Aside：先更新 DB → 再删缓存，不是更新缓存。** 三个写操作漏掉任何一个，都会留下永久脏缓存 —— 最容易翻车的地方，写完逐个核对 |

#### 必答的三大问题（用本项目的场景讲，别背定义）

| 问题 | 在本项目的触发点 | 解法 |
|---|---|---|
| **穿透** | 请求 `getArticleDetail(id="乱填")`，DB 查不到 → 每次都打库 | 缓存空值（TTL 短一点）；或上布隆过滤器 |
| **击穿** | 热门文章 key 到期的瞬间，并发全打到 DB | 互斥锁（`SET NX`）只放一个去查；或逻辑过期（不设物理 TTL） |
| **雪崩** | 项目重启后大量 key 同一时刻过期 | TTL 加随机值，如 `30min + random(0,5min)` |

**验收**：缓存生效（第二次请求不打 SQL，看 MyBatis 日志）；改一篇文章后，`GET` 立刻拿到新值。

---

### 第 2 关：浏览量计数器（`INCR` + 定时回写）

| 项 | 内容 |
|---|---|
| **锚点** | `entity/KnowledgeArticle.java:62` `readCount` |
| **方案** | `getArticleDetail` 里 `opsForValue().increment("article:read:" + id)`；`@Scheduled` 每 N 分钟把 Redis 计数批量 `UPDATE` 回 `knowledge_article.read_count` |
| **产出** | 一个 `ArticleReadCountTask`（放 `common/` 或新建 `task/`） |
| **加分点** | ① 为什么不用 `UPDATE ... SET read_count = read_count + 1`：高频写同一行 → **行锁竞争**，热点文章会把 DB 拖垮 ② 宕机会丢最后一段增量，可接受（浏览量不是钱）③ 用 `@Scheduled` 要开 `@EnableScheduling` |
| **验收** | 刷 10 次详情，`redis-cli --raw GET article:read:<id>` → `10`；等定时任务跑完，DB 里 `read_count` 对得上 |

**知识课**：第 1 课（`increment` 原子性）+ 第 8 课（落地）

---

### 第 3 关：JWT 登出黑名单 ⭐ 最出彩的一关

> 这关的特别之处：它**修的是一个真实存在的安全缺陷**，不是「加个缓存」这种可有可无的优化。面试讲这个比讲缓存有说服力得多。

**问题**

`controller/UserController.java:51` 的 `logout()` 是空实现：

```java
@PostMapping("/user/logout")
public Result<Void> logout(){
    log.debug("用户退出登录");
    return Result.success();   // ← 什么都没做
}
```

JWT 是**无状态**的，token 一旦签发，在过期前永远有效。所以现在的「登出」只是前端把 token 从 localStorage 删了 —— 攻击者拿到旧 token 照样能用满 24 小时（`application.yml` 里 `jwt.expiration: 86400000`）。

**方案**

| 步 | 做什么 | 锚点 |
|---|---|---|
| 1 | 登出时把 token 写进 Redis 黑名单 | `service/UserService.java` 新增 `logout(String token)`；`UserController.logout()` 调用它 |
| 2 | TTL = token 的**剩余**有效期 | `JwtTokenUtil.generateToken()` 用 `withExpiresAt(now + expiration)`；解析 `DecodedJWT.getExpiresAt()` 减当前时间 |
| 3 | 每次请求校验前先查黑名单 | `util/JwtAuthticationFilter.java` |
| 4 | filter 链不用改结构 | `config/SecurityConfig.java:52` 的 `filterChain` 已经是 `STATELESS` + `addFilterBefore(jwtAuthticationFilter(), ...)`，只需在 filter 内部加判断 |

**产出**：`service/UserService.logout()`；`JwtAuthticationFilter` 中一处黑名单校验。

**深度加分点**

- **TTL 为什么必须是「剩余时间」而不是固定 24h**：黑名单的 key 只要活到 token 自然过期就够了，之后 token 自己会失效，多留一秒都是浪费内存。这是「用 TTL 做自动清理」的经典用法
- **黑名单 vs 白名单**：黑名单省内存（只存登出的），但 Redis 挂了会**误放行**；白名单（存所有有效 token）Redis 挂了会**全部拒绝**。安全场景通常宁拒绝勿放行 → 白名单更安全，代价是内存
- **Redis 挂了怎么办**：降级策略必须想清楚（放行 + 告警，还是拒绝 + 报错），面试必问
- **比黑名单更轻的方案**：`(userId, tokenVersion)` 计数 —— 用户改密码/登出时 `INCR` 版本号，token 里带版本号，不匹配即失效。**key 数从「每 token 一个」降到「每用户一个」**

**验收**：登录拿 token → 调 `/api/user/current` 成功 → 调 `/api/user/logout` → 同一个 token 再调 `/api/user/current` 返回 401。

**知识课**：第 1 课（`SETEX`/`TTL`）+ 第 8 课（落地）

---

### 第 4 关：热门文章排行榜（ZSet）✅ 已完成（2026-10-14）

| 项 | 内容 |
|---|---|
| **锚点** | `entity/KnowledgeArticle.java:62` `readCount`、`controller/KnowledgeController.java:64` `articlePage()` |
| **方案** | ZSet `article:hot`，`ZINCRBY article:hot 1 <articleId>`；取榜 `ZREVRANGE article:hot 0 9 WITHSCORES` |
| **和 DB 的关系** | DB 是**权威数据**，ZSet 只是**加速视图**。可以定时用 DB 的 `read_count` 全量重建（简单、不会漂），也可以实时累加（快、会漂）——**面试要能说出这个取舍** |
| **加分点** | ① 取 Top N 用 `ZREVRANGE` 是 O(log N + M)，别 `ZRANGE` 全量再截断 ② 大榜单翻页仍有性能问题，真实场景只缓存前 N 页 ③ 底层是跳表 + 哈希，能说出跳表查询复杂度 |
| **验收** | `redis-cli --raw ZREVRANGE article:hot 0 9 WITHSCORES` 看到的排名和分数符合预期 |

**知识课**：第 3 课（ZSet）+ 第 6 课

---

### 第 5 关：限流 + 防重复提交 ✅ 已完成（2026-10-15，收官记录见第十一节）

**场景 A：登录接口限流**

| 项 | 内容 |
|---|---|
| **锚点** | `controller/UserController.java:40` `login()` + `:48` `register()`（**实际两个都接了限流**） |
| **原方案** | 固定窗口：`INCR login:try:<username>:<minute>` + `EXPIRE 60`；超阈值拒绝 |
| **实际实现** | **滑动窗口**（ZSet 存时间戳）+ **用户名 / IP 双维度** —— 见 **11.1**（为什么改） |
| **四算法取舍** | 固定窗口（最简单，有**临界问题**：59s 和 61s 各打满一轮 = 2 倍流量）/ 滑动窗口（ZSet 存时间戳，精确但费内存）/ 令牌桶（允许突发，最常用）/ 漏桶（恒定速率） |
| **产出** | `@RateLimit` 注解 + 拦截器 —— ⏭️ **有意推迟到第 7 关后**（见 **11.6**）；当前限流逻辑手写在 `UserService` 里 |

**场景 B：情绪日记防重复提交**

| 项 | 内容 |
|---|---|
| **锚点** | `controller/UserController.java:72` `addEmotionDiary()` |
| **原方案** | `SET NX` 幂等键（`userId + 内容摘要`），TTL 几秒；抢到才放行 |
| **实际实现** | key = **纯 `userId`**，TTL **15 秒** —— 见 **11.1** / **11.2**（业务假设：写一篇日记至少要 15 秒） |
| **加分点** | 幂等 vs 防重的区别；为什么锁的粒度选「几秒」而不是「一分钟」 |

**知识课**：第 5 课（⭐⭐⭐⭐）

---

### 第 6 关：分布式锁（用在真实定时任务上）✅ 已完成（2026-10-16，收官记录见第十二节）

| 项 | 内容 |
|---|---|
| **锚点** | `common/FileCleanupTask.java` + `entity/SysFileInfo.java`（`isTemp`/`status`/`expireTime`） |
| **为什么需要** | 定时任务在**多实例部署**时会重复执行。清理临时文件重复跑可能删两次、或状态机错乱 |
| **方案** | `SET lock:fileCleanup <uuid> NX EX 30`；抢到锁才执行；**释放用 Lua 原子地「比对 uuid + 删除」** |
| **必须知道的三个点** | ① 加锁和设过期必须**原子**（`SET NX EX`，别 `SETNX` + `EXPIRE` 两条）② 释放必须**校验持有者**，否则会删掉别人的锁 ③ Redis 主从异步复制 → 主挂了锁可能丢 → 这是 **Redlock 的由来**，也是它的争议点 |
| **进阶** | 看门狗/锁续期（Redisson 的 `RLock` 自动续期）|
| **验收** | 起两个实例，只有一个执行清理；日志能看到抢锁失败 |

**知识课**：第 5 课（⭐⭐⭐⭐）

---

### 第 7 关：高级数据结构（每个写一个能用的小接口）

| 场景 | 结构 | 锚点 | 关键点 |
|---|---|---|---|
| **AI 分析结果缓存** | String + JSON | `entity/EmotionDiary.java:71` `aiEmotionAnalysis`；`AiService/EmotionAnalysisService.java` | **AI 调用按量计费，同样输入不必重复调** —— 这条能省真金白银，面试讲收益最有说服力 |
| **签到统计** | BitMap | `entity/EmotionDiary.java:35` `diaryDate`（一天一条，天然是签到） | `SETBIT`/`BITCOUNT`；1 亿用户签到只占 12MB |
| **UV 统计** | HyperLogLog | `service/AnalyticsService.java` | 误差 0.81%，固定 12KB 上限，**不能取单个元素** |
| **任务队列** | Stream 或 ZSet 延时队列 | `entity/AiAnalysisTask.java`（已有 `status`/`priority`/`retryCount`/`maxRetryCount` —— **就是一张队列表**） | Stream 有消费者组 + ACK + PEL；ZSet 用 score=时间戳做延时 |
| **文件秒传** | `SET NX` | `controller/KnowledgeController.java:147` `uploadFile()` + `entity/SysFileInfo.java` | 文件 MD5 做 key，已存在就直接返回 URL（⚠️ 现有 `OSSUtil.upload` 是**按 UUID 生成路径**，没有 MD5 去重 —— 秒传要**新增**"算 MD5 → 查索引"的逻辑，不只是加个缓存） |

**知识课**：第 6 课（⭐⭐⭐）

---

### 第 8 关：面试包装

把第 1~7 关的成果压缩成能在 2 分钟里讲清楚的版本。

**场景设计题（面试最爱问，用本项目当素材）**

- 「缓存和数据库如何保证一致性？」→ 第 1 关（话术见 5.6）
- 「你的分布式锁有什么问题？主从切换时会怎样？」→ 第 6 关
- 「JWT 怎么做登出？」→ 第 3 关（**这题很多人答不上来，答得出是加分项**）
- 「设计一个排行榜，实时更新前 100 名」→ 第 4 关

**知识课**：第 9 课（⭐⭐⭐⭐⭐）

---

## 五、第 1 关详解：缓存与事务的一致性 ⭐

> 这一节是 2026-10-08 ~ 10-09 讲过的内容汇总，**第 1 关的所有细节都在这里**。

### 5.1 已经踩过的坑（都是实测复现过的）

#### 坑 A：`Stream.toList()` / `List.of()` 不能塞 Redis

**现象**：第一次请求成功（写缓存），第二次请求抛

```
SerializationException: Could not read JSON: Unexpected token (START_OBJECT),
expected VALUE_STRING: need String, Number or Boolean value that contains type id
(for subtype of java.lang.Object)
```

**根因**：Spring Data 的类型解析器**对它没法实例化的类会省略类型信息**。

| 写进去的东西 | Redis 里长什么样 | 读回来 |
|---|---|---|
| `new ArrayList<>(...)` | `["java.util.ArrayList",[ {...}, {...} ]]` | ✅ 正常 |
| `stream().toList()` → `ImmutableCollections$ListN` | `[ {...}, {...} ]`（**裸数组，没有 `@class`**） | ❌ 解析类型 id 阶段就炸 |
| `List.of()` / `Set.of()` / `Map.of()` / `Arrays.asList()` | 同上 | ❌ 同上 |

**为什么是「内部类」**：JDK 的 `ImmutableCollections` 按元素个数特化 —— `List12`（1~2 个，不用数组）和 `ListN`（3 个以上）。这些类是**包级私有**的，JDK 保留随时改实现的自由。既然外界拿不到、也不该依赖，Spring Data 就不给它写类型信息。

**修法**（用户已改）：

```java
dto = list.stream()
        .map(KnowledgeConvert::categoryToResponse)
        .collect(Collectors.toCollection(ArrayList::new));
```

#### 坑 B：`GenericJackson2JsonRedisSerializer` 的 ObjectMapper 是「裸机」

它内部是 `new ObjectMapper()`，**不挂 `JavaTimeModule`**。所以带 `LocalDateTime` 的对象**写的时候就抛** `SerializationException`（`MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES`）。

**注意**：Spring Boot 自动配置的那个 `ObjectMapper`（给 HTTP 接口用的）**跟它毫无关系**，所以接口一直正常 —— 这个坑只在 Redis 这条线上出现。

**修法**：`redisconfig` 里 `json.configure(mapper -> {...})`，见附录 A。

#### 坑 C：`log.debug(..., dto.size())` 写在判空之前 → NPE

已修：`dto == null ? 0 : dto.size()`。

#### 坑 D：读写的 key 对不上（最隐蔽）

曾经「跑通了」，但其实是**数据库那条路通了、缓存一次都没命中**。用 `redis-cli` 才查出来：

```
DBSIZE → 4        （4 个 key）
--scan → 1 2 3 4  （写的是裸 id，不是约定的 key）
TYPE cache:category:tree → none
```

读的是 `cache:category:tree`，写的是 `"1"`/`"2"`/`"3"`/`"4"`。**「接口返回了数据」不等于「缓存生效了」。**

> **判据永远是 MyBatis 日志**：第 2 次请求**不该有 SQL**。有 SQL = 缓存没命中。别用肉眼看接口有没有返回来判断。

### 5.2 关于「缓存到底快了多少」—— 别量错东西

实测数据（2026-10-08）：

| 指标 | 值 |
|---|---|
| Redis 往返延迟 | min **0.348ms** / avg **0.720ms** / max **1.268ms** |
| 缓存值大小 | **463 字节**（4 个分类） |

结论：

1. **Redis 一次 GET 只花 0.7ms** —— 请求总耗时里的 20ms **不是 Redis 花的**。
2. **本场景天然测不出延迟差距**：本地 MySQL 查 4 行数据本来就只要 1~3ms，加上 MyBatis 映射 + 连接池开销也就十几毫秒。**4 行 vs 一次 GET，差距埋在噪声里。**
3. **测量的口径要对齐**：别拿「SQL 耗时」比「整个 HTTP 请求耗时」（后者含 JWT filter、Spring MVC、参数序列化、**打日志**）。要做减法，必须同一个接口、缓存命中 vs 未命中。
4. **日志是隐形大头**：`application.yml` 把 `service` / `controller` / `util` 都设成了 `debug`，**控制台日志是同步的**，Windows 下尤其慢。它对两条路径一视同仁 —— 恰好解释了「两条路差不多快」。
5. **缓存的收益 = 查询成本 × 并发量**。你现在两个乘数都是最小值（4 行表 / 一个人点）。

📌 **诚实的工程结论**：单看这个 4 行表，缓存的**延迟收益是负的**（多了序列化和网络）。加它的理由是：① 分类不会永远是 4 条，收益随数据量增长；② 它是第 1 关的**训练场** —— 一致性、穿透、击穿、雪崩才是分数所在。

> **面试话术**（能说出收益有限，比硬吹「性能提升 80%」可信十倍）：
> 「分类树这个缓存的收益不在响应时间 —— 本地 MySQL 查 4 行本来就只有十几毫秒。它的价值是把数据库的**读压力**移到 Redis，在**并发**下才有意义。我评估过，这个场景的收益有限；加它主要是建立缓存层的模式，以及应对后续数据量增长。」

**真正有收益的缓存**是第 7 关那个 —— **AI 调用按量计费**，同一输入不该调两次。分类树缓存的收益是**理论上的**，AI 结果缓存的收益是**账单上的**。

### 5.3 为什么 TTL 是必选项，不是可选项

**本项目没有「新建/修改分类」的接口**（只有查树 + 两个删除）。这意味着：**任何直接的 SQL 改动，项目自身永远发现不了**，缓存会一直脏下去。

- **evict（主动删）** 保证的是**及时性**：正常业务路径走完，缓存立刻作废。
- **TTL（过期）** 保证的是**最终一致的下界**：无论如何，脏数据最多活 TTL 那么久。

> **`evict` 管及时，`TTL` 管兜底。两者缺一不可。**

### 5.4 事务和 Redis 的关系 —— 严格说：**没有关系**

这是最容易绕晕的地方，拆开看：

|  | 管什么 |
|---|---|
| `@Transactional` | 只管 **JDBC 那条连接**：开 `BEGIN`、`COMMIT`、`ROLLBACK`。它眼里只有 MySQL |
| `redisTemplate.delete()` | 对**另一个系统**的写 |

**两者之间没有任何自动协调**：DB 回滚了，Redis 不会跟着恢复；Redis 删失败了，DB 也不知道。

**恰恰因为它们没绑定，先后顺序才只能你自己排。** 如果 Spring 能自动协调，就不需要 `afterCommit` 了。

> 一句话：**一个方法里碰了两个数据源，`@Transactional` 只管其中一个。**

往深一层 —— 这就是「**本地事务跨不了系统**」。要跨 MySQL 和 Redis 保证强一致得上分布式事务（2PC / TCC / Saga），代价极高。所以工程上选择**最终一致**：提交后删 + TTL 兜底。

#### 问题 ①：删缓存的**顺序**不对（当前隐患）

现在 `evictCategoryTreeCache()` 会在**事务提交之前**执行。坏窗口是 `[evict, COMMIT]`：

```
T1(删分类5) ──BEGIN──deleteById(5)──evict()─────────COMMIT────►
                                      │               │
T2(查树) ─────────────────────────────┴──查库──回填───┴──────►
                                         ↑
                              窗口 = [evict, COMMIT]
                              T2 读不到 T1 未提交的删除 → 拿到【含分类5】的旧树
                              回填进 Redis。而 T1 已经 evict 过了，不会再删
                              → 脏缓存活满 TTL
```

**关键在窗口的方向。** 把 evict 挪到 **COMMIT 之后**，窗口变成 `[COMMIT, evict]`：这期间的读者查到的是**提交后**的新数据，回填的也是新数据，随后 evict 再把它删掉 —— 白白 miss 一次，**但不会脏**。

> **这就是「先提交、后删缓存」这条规矩的全部理由 —— 不是玄学，是窗口方向。**

**不会坏的情况**（先排除掉，别自己吓自己）：
- **事务回滚** → 缓存已删、DB 没变 → 下次读查库拿旧数据回填 → **数据是对的**，只是白删一次。无害。
- **无并发** → 删完提交 → 下次读查库看到新数据 → 正确。

#### 问题 ②：缓存故障**反噬主业务**

`redisTemplate.delete()` 在 Redis 连不上时抛 `RedisConnectionFailureException`。家谱：

```
RedisConnectionFailureException → DataAccessException → NestedRuntimeException → RuntimeException
```

而 `@Transactional` 默认 `rollbackFor = RuntimeException`。于是：

```
1. 用户点「删除分类」
2. DB 的 deleteById 执行完了（事务里，还没提交）
3. redisTemplate.delete() → Redis 连不上 → 抛 RuntimeException
4. 异常往外冒 → Spring 回滚事务 → 【DB 的删除也白做了】
5. 用户看到 500
```

**结果：Redis 一挂，分类就删不掉了。**

缓存是**旁路（sidecar）**—— 它存在的意义是让主业务更快。它坏了，主业务最多变**慢**，**绝不能变坏**。

**解药**：

```java
try {
    redisTemplate.delete(CATEGORY_TREE_KEY);
} catch (Exception e) {
    log.warn("分类树缓存失效失败，key={}，等 TTL 自然过期", CATEGORY_TREE_KEY, e);
}
```

⚠️ **catch 了就别再抛**，否则等于没 catch。**而吞掉失败 = 缓存没删成 = 脏数据活满 TTL** —— 所以 ② 的解药**依赖** TTL（呼应 5.3）。

#### 问题 ①② 的关系

| | 问题 | 症状 | 解药 |
|---|---|---|---|
| ① | **顺序**：删缓存跑在提交之前 | 并发下旧数据被回填成脏缓存 | 让 evict 等提交后再跑（5.5） |
| ② | **故障隔离**：Redis 删失败会怎样 | 异常冒泡 → 事务回滚 → **Redis 一挂就删不掉分类** | try-catch 吞掉 / 降级 |

**try-catch 治 ②，治不了 ①。「提交后再删」治 ①，治不了 ②。两个都得做，不能互相替代。**

### 5.5 四种解法（让 evict 在提交之后跑）

**方案 1 —— 推荐，改动最小：`TransactionSynchronizationManager.registerSynchronization`**

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override public void afterCommit() {
        evictCategoryTreeCache();   // ← 提交之后才跑
    }
});
```

- 必须在**事务内**注册（否则抛 `IllegalStateException`）
- 回滚时走 `afterCompletion(ROLLED_BACK)`，**不会**触发 `afterCommit` ✓

**方案 2 —— 更解耦：`@TransactionalEventListener(phase = AFTER_COMMIT)`**

```java
// 事件类
public record CategoryChangedEvent(Long categoryId) {}

// 发布（在事务方法里）
publisher.publishEvent(new CategoryChangedEvent(id));   // 只喊一声

// 监听
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCategoryChanged(CategoryChangedEvent event) {
    evictCategoryTreeCache();
}
```

**「解耦」具体在哪**：
1. **调用方不用知道「删缓存」这件事存在**。现在是「写删除逻辑的人得惦记着缓存」；改成事件后，`deleteCategory()` 只喊一声「分类变了」，不知道谁在听。
2. **以后加第三个动作不用改删除逻辑**。比如将来「删分类后通知管理员」「写审计日志」「同步到 ES」—— 用事件这些全是**新增一个监听器**，`deleteCategory()` 一行不动。

**三个关键点**：
- `phase = AFTER_COMMIT` → 效果等同方案 1，只是换了个表达
- 监听器**默认只在有事务时触发**（`fallbackExecution = false`）
- ⚠️ `AFTER_COMMIT` 时事务**已提交、连接可能已归还** —— **不要再操作数据库**（除非另开 `@Transactional(propagation = REQUIRES_NEW)`）。这是著名坑

**方案 3 —— 拆 bean：**

```java
@Service
public class KnowledgeCategoryTxService {
    @Transactional
    public void doDeleteCategory(Long id) { ...原来的 DB 操作... }
}

@Service
public class KnowledgeCategoryService {
    private final KnowledgeCategoryTxService txService;   // ← 注入进来的是代理

    public void deleteCategory(Long id) {
        txService.doDeleteCategory(id);   // 返回时事务已提交 ✓
        evictCategoryTreeCache();         // 自然落在提交之后 ✓
    }
}
```

**⚠️ 大坑：拆成同一个类里的两个方法没用！**

```java
public void deleteCategory(Long id) {
    this.doDeleteCategory(id);   // this = 真实对象 → 绕开代理 → 没事务 ✗
}
```

必须拆到**另一个 bean**，因为注入进来的是**代理**。

**方案 4 —— 延时双删（只当面试谈资）：**

删 → 提交 → sleep 500ms → 再删。**那个 500ms 是猜的，没有理论依据**，而且 sleep 占线程。被问到就说「知道，但我用 `afterCommit` 从根上避开了窗口」。

**排序**：

| 解法 | 评价 |
|---|---|
| 拆 bean | ✅ 最干净，还顺手解决顺序问题 |
| 方案 1（`registerSynchronization`） | ✅ 改动最小，推荐 |
| 方案 2（事件） | 🟡 更解耦，但多一个类，小项目属过度设计 |
| 延时双删 | 🟡 面试要会讲，但别当主方案 |

### 5.6 为什么 `this` 调用会「事务失效」—— 代理原理

**`@Transactional` 是靠代理实现的。**

```
【启动时，只做一次】
  Spring 扫到 KnowledgeCategoryService 上有 @Transactional
    → new 一个代理对象 Proxy@1a2b（内部持有真实对象 Ref@3c4d）
    → 把 Proxy@1a2b 放进容器
    → ★ 真实对象 Ref@3c4d 压根不进容器

【运行时】
  Controller 的字段 categoryService → 存的是 Proxy@1a2b
  controller.categoryService.deleteCategory() 落到 Proxy@1a2b 上：

      Proxy@1a2b.deleteCategory():
          tm.begin()                    ← 事务代码在这
          Ref@3c4d.deleteCategory()     ← 才轮到你的方法体
          tm.commit()                   ← 事务代码也在这
```

**关键三点**：

1. **代理是启动时创建一次的**，一个 bean 一个，全程复用 —— 不是「方法内创建」「每次创建」。
2. **代理是通过「替换容器里的对象」生效的**。Controller 注入的那一刻拿到的就已经是代理了。
3. **事务代码根本不在你的类里** —— 在一个 Spring 动态生成的东西里。所以 `this` 永远指向**原始对象**，而原始对象身上没有那层壳。`this.doDelete()` 就是一次普通方法调用，**谁来开事务？没人。**

**「静默失效」为什么最难查**：注解还在（注解只是元数据，谁读它才有用）、代码不报错（语法完全合法）、日志看不出来（没异常）。**只是没人生效而已。**

**代理的两种实现**：

| 方式 | 条件 | 做法 |
|---|---|---|
| JDK 动态代理 | 类**实现了接口** | 生成一个实现同一接口的类 |
| CGLIB | 类**没接口** | 用字节码生成它的**子类**，重写方法 |

`KnowledgeCategoryService` 没实现接口 → 走 **CGLIB** → 生成子类。

> 📌 顺便记住一个面试点：**这就是 `@Transactional` 不能加在 `final` 方法上的原因** —— 子类没法重写 final 方法。`private` 同理。

**想亲眼验证有没有事务**：`application.yml` 临时加

```yaml
logging:
  level:
    org.springframework.jdbc: debug
```

有事务时会打 `Creating new transaction with name [...]`；没有就什么都不打。

**三种解法的取舍**：

| 解法 | 评价 |
|---|---|
| 拆成真正的两个 bean | ✅ 最干净，还顺手解决顺序问题 |
| `@Lazy` 自注入 | 🟡 能跑，但读代码的人会懵（一个类为什么注入自己？），算 hack |
| `AopContext.currentProxy()` | 🔴 最差 —— 要开全局 `exposeProxy = true`，业务代码出现 `AopContext` 就跟 Spring 内部 API 绑死，单测还得自己塞 ThreadLocal |

`@Lazy` 的样子（知道就好，**优先选拆 bean**）：

```java
@Lazy
@Autowired
private KnowledgeCategoryService self;   // 注入自己，拿到的是代理

public void deleteCategory(Long id) {
    self.doDelete(id);   // 走代理 → 有事务 ✓
}
```

`AopContext` 的样子（**最不推荐**）：

```java
@EnableAspectJAutoProxy(exposeProxy = true)   // 全局开关
...
((KnowledgeCategoryService) AopContext.currentProxy()).doDelete(id);
```

它靠 ThreadLocal 把「当前代理」存起来，绕开 `this` 的限制 —— 代价是绑死框架。

> **另一个正当用途**：`@Lazy` 也常用来解**循环依赖**（A 依赖 B、B 依赖 A）。Spring Boot 2.6 起默认禁止循环依赖启动。但**真正该做的是把循环依赖重构掉**。

### 5.7 面试话术

> 「分类树用的是 Cache Aside 模式。**删缓存放在事务提交后**（`afterCommit` 钩子），避开『提交前删缓存 + 并发读回填旧值』的脏缓存窗口；Redis 故障用 try-catch 降级，不让缓存反噬主业务；**TTL 作为最终一致性的兜底**。
>
> 关于收益我很清楚：这个场景**延迟上的收益是负的** —— 本地 MySQL 查 4 行本来就只有十几毫秒。它的价值在**并发下卸载数据库读压力**，以及作为缓存层模式的落地。」

**这三句话，比「我加了缓存」值钱得多。**

---

## 六、深度加分点（面试话术锚）

不是背来的结论，每条都能在**本项目代码里指到出处**：

| # | 加分点 | 出处 |
|---|---|---|
| 1 | `embstr` 的 44 字节是 **64 − 16 − 3 − 1** 算出来的，不是拍脑袋 | 知识线实测 |
| 2 | List 的紧凑编码阈值按**字节**（8KB），Hash/Set/ZSet 按**个数**（512/128）—— 这个例外能说出来很加分 | 知识线实测 |
| 3 | `delete(K)` 返回 `Boolean`，`delete(Collection<K>)` 返回 `Long` | 第 1 课 |
| 4 | `getExpire` 的 `-1`（永久）要当作「**存在**」，只有 `-2` 是不存在 | 第 1 课 |
| 5 | `@class` 缺失 = 反序列化拿不回来。**且不是「写不写」的问题，是「类型解析器能不能实例化它」的问题** | 5.1 坑 A（已实测） |
| 6 | `MULTI/EXEC` 是**事务不是锁**，两者解决不同问题 | 知识线 `6.事务.md` |
| 7 | 乐观锁的本质是「失败重试」，**有副作用的操作不能重放**，所以必须用悲观锁 | 第 5、6 关 |
| 8 | 主从异步复制 → `SET NX` 锁会丢 → Redlock 的由来与争议 | 第 6 关 |
| 9 | 黑名单 TTL 设「token 剩余有效期」而非固定值：让 Redis 的 TTL 帮你自动清理 | 第 3 关 |
| 10 | 浏览量用 Redis 计数而非 `UPDATE ... +1`：避免热点行**行锁竞争** | 第 2 关 |
| 11 | Redis 6.0+ 是「**多线程 IO + 单线程命令执行**」，不是纯单线程 | 知识线 |
| 12 | `@Transactional` 靠**代理**实现 → `this` 调用绕开代理 → 事务静默失效。CGLIB 生成的是**子类**，所以 final 方法不能加事务 | 5.6 |
| 13 | 本地事务跨不了系统：`@Transactional` 只管 JDBC 连接，管不到 Redis → 顺序只能自己排 | 5.4 |

---

## 七、已知坑 / 笔记待补

### 知识线笔记里的小错（`RedisNotes/`）

- `2.常用命令.md` 写「查询键值对：`GET 键 值`」—— `GET` **只接一个键**，不接值
- `2.常用命令.md` 写「`EXISTS 键` 输出的是 0/1 布尔值」—— 严格说返回的是**个数**（0 或 1），不是布尔类型
- `3.常见数据结构.md` ZSet 两行留白：`ZINCRBY`、`ZREMRANGEBYRANK` —— 用户表示不补，留个记录

### 本项目接 Redis 时的坑（按踩坑概率排序）

1. **序列化器带不带 2** —— 最高频，见第三章坑 1
2. **往 Redis 塞 `List.of()` / `stream().toList()`** —— 写的时候不报错，读的时候才炸，见 5.1 坑 A
3. **`redisconfig` 忘了挂 `JavaTimeModule`** —— 带 `LocalDateTime` 的对象 **写**的时候就抛，见 5.1 坑 B
4. **读写 key 不一致** —— 「接口返回数据」≠「缓存生效」，判据是 MyBatis 日志，见 5.1 坑 D
5. **evict 跑在事务提交前** —— 脏缓存窗口，见 5.4
6. **evict 失败反噬主业务** —— 异常冒泡导致整个删除事务回滚，见 5.4
7. **`spring.redis` vs `spring.data.redis`** —— 静默失效，最难查（⚠️ 未实测，见第三章坑 2）
8. **`password` 空值** —— 没密码时整行不写
9. **`@CacheEvict` 漏写**（场景 B）—— 三个写文章的方法漏一个就留永久脏缓存
10. **`KnowledgeArticle.id` 是 String 不是 Long** —— 拼 key、写 SpEL 时注意
11. **`@Scheduled` 需要 `@EnableScheduling`** —— 忘了定时任务根本不跑，还不报错
12. **`redis.execute(...)` 的 lambda 歧义** —— `RedisCallback` 和 `SessionCallback` 两个重载，必须显式强转

---

## 附录 A：最小可行配置（实际落地的版本）

**`pom.xml`**（版本交给 Boot parent 管，**不要写 version**）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**`application.yml`**（无密码 → `password` 整行不写）

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      # password:            # ← 本机 Redis 无密码，这一行必须整个不写
      database: 0
      timeout: 3000ms
```

**`config/RedisConfig.java`**（✅ 线上实际版本；Boot 3.5.15 → **带 2**）

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // ⚠️ Boot 3 用【带 2】的；Boot 4 的等价类是 GenericJacksonJsonRedisSerializer（不带 2）
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer();

        // ★ 关键：序列化器内部是裸的 new ObjectMapper()，java.time 要手动挂
        json.configure(mapper -> {
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        });

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(json);
        template.setHashValueSerializer(json);
        template.afterPropertiesSet();
        return template;
    }
}
```

---

## 附录 B：文件与笔记索引

**本项目（ai-springboot）**

| 路径 | 作用 |
|---|---|
| `pom.xml` | 第 0 关加 Redis 依赖 ✅ |
| `src/main/resources/application.yml` | 第 0 关加 `spring.data.redis.*` ✅ |
| `config/RedisConfig.java` | ✅ 已建 |
| `service/KnowledgeCategoryService.java` | **第 1 关主战场**（`listAll` ✅ / 两处 `deleteCategory` ❌） |
| `controller/KnowledgeController.java` | 第 1 关（分类树 :38、两个删除 :46/:54、文章详情 :92）、第 7 关（秒传 :139） |
| `controller/UserController.java` | 第 3 关（logout :63）、第 5 关（login :40、register :48、日记防重 :71） |
| `service/UserService.java` | 第 3 关（logout 黑名单）、第 5 关（login/register 限流 + 双黑名单） |
| `service/DiaryService.java` | 第 5 关（`addEmotionDiary` 的 `SET NX` 防重） |
| `util/JwtAuthticationFilter.java` | 第 3 关（黑名单校验） |
| `common/FileCleanupTask.java` | 第 6 关（分布式锁） |
| `service/AnalyticsService.java` | 第 7 关（UV / 统计缓存） |
| `docs/Redis接入进度.md` | **本文件（唯一进度文档）** |
| `docs/DTO怎么写.md` | 知识笔记，无进度信息 |
| `docs/事务练习-删除知识分类.md` | 知识笔记，无进度信息（`@Transactional` 失效的 7 种场景见它的 7.1） |

**知识线（RedisNotes，另一个仓库）**

| 文件 | 内容 |
|---|---|
| `1.Redis是啥.md` ~ `9. 哨兵节点.md` | CLI 层基础 |
| `10.Java中使用Redis.md` | Java 接入，**第十章是知识线自己的路线图** |
| `demo/`、`pom.xml`、`src/` | 练习代码（嵌套 Maven 项目问题待理清） |

> ⚠️ 用户提过一次「11 开头的那个 md 是路线」，但 `RedisNotes/` 里**不存在 11 开头的文件**（只有 1~10）。他指的应该是 `10.Java中使用Redis.md` 里的**第十章**。别再提「那个 11 文件」。

---

## 八、第 2 关 + 场景B 收官记录（2026-10-11 ~ 10-13）

> 给"接手的 AI / 未来的自己"看：这一段是**第 1 关场景B 和第 2 关**踩过的坑、定下的设计、以及还没做的事。

### 8.1 第 2 关定下的两个设计（别再动摇）

**① 计数器存「增量」，不是「绝对值」**

| | 绝对值（`article:read:{id}` = 总数） | **增量（`article:read:delta:{id}`）** |
|---|---|---|
| 播种 | 要 `SETNX` 播种，否则 `INCR` 从 1 开始把库里的数抹掉 | **不用** —— 天然从 0 开始 |
| 返回给前端 | 直接用 `INCR` 的结果 | `DB基线 + delta` |
| 回写 SQL | `SET read_count = ?` 绝对赋值 | `read_count = read_count + ?` **相对更新** |
| 竞态 | **会永久倒退**（回写与并发访问交错） | 只造成一次性小偏差，**下个周期自愈** |

**理由**：相对更新天然并发安全 —— 它不覆盖别人的写。绝对值模型一旦和并发访问交错，会把计数写**倒退**，而且自己好不了。

**② 回写必须「认领」（`pop`），不能「读取」(`members`)**

因为回写是**相对更新**（`+delta`），**没有幂等性**：用 `members` 读的话两个实例读到同一批 → **重复计数**。
取的动作必须是原子认领（`pop`）。

> 这正是加分点 ⑦ 那句话的实例：「有副作用的操作不能重放」。

### 8.2 第 2 关踩过的坑（全是实测，教学价值很高）

| # | 坑 | 症状 | 真因 |
|---|---|---|---|
| 1 | **key 少了 `+ id`** | 所有文章共用一个 `article:read:delta:` | 字符串拼接漏了变量 |
| 2 | **cron 的 `*/30` 写在「分」上** | 以为每 30 秒，其实每 30 分钟 → "任务根本没跑" | **Spring cron 是 6 段，第一段是「秒」** |
| 3 | ⭐ **两个 Template 混用** | 脏集合成员带引号（`"e45f…"`，**34 字节**而非 32），任务永远查不到 key，**静默 `continue`** | **同一个字符串，一边用 String 序列化器、一边用 JSON 序列化器 → 字节不一致** |
| 4 | **`get` 而不是 `getAndDelete`** | key 永不删除，同一批增量被反复回写 | 忘了"取走"这个语义 |
| 5 | **`getAndDelete` 返回 `String` 不是 `Long`** | 编译不过 | **命令决定返回类型**：`INCR` 回整数，`GETDEL` 回批量字符串 |
| 6 | **`@Scheduled()` 空参** | 应用一启动就抛 `IllegalArgumentException` | 必须给 cron / fixedDelay / fixedRate 之一 |
| 7 | **`NULL + delta = NULL`**（MySQL） | 日志说"归档成功"，库里 `read_count` 纹丝不动 | 得写 `IFNULL(read_count, 0) + ?` |
| 8 | **`RedisTemplate` 存字符串会带引号** | `redis-cli` 里 `"abc"` 而不是 `abc` | `GenericJackson2JsonRedisSerializer` 把 String 也 JSON 化了 |

> **第 3 条是整晚最难的一个** —— 同一类 bug 的更深一层：
> 前几次是 **key 的字符串**对不上，这次是 **值的字节表示**对不上。
> **教训：只要一个 key 会被两个地方读写，就必须保证两边的序列化器一致。**

### 8.3 场景B 的关键决定

**① 为什么必须「拆 bean」**

`@Cacheable` 命中时**方法体整个不执行**。而 `getArticleDetail` 里有阅读量的 `INCR`（副作用）→ 贴上去阅读量就废了。所以拆成：

```
KnowledgeArticleService.getArticleDetail(id, user)   ← 取内容 + 权限校验 + 计数
        │ 跨 bean 调用（走代理 ✓）
        ▼
ArticleContentService.loadDetail(id)                 ← 纯读，贴 @Cacheable
```

**不能在同一个类里拆成两个方法** —— `this.loadContent()` 走原始对象、绕过代理，`@Cacheable` 静默失效（= 5.5 的「方案 3 拆 bean」）。

**② `unless` 一个人干了两件事**

```java
unless = "#result != null && #result.status != T(...ArticleStatus).PUBLISHED.getCode()"
```

| `#result` | unless | 结果 |
|---|---|---|
| `null`（文章不存在） | false | **写缓存（缓存 null）** → **防穿透** ✓ |
| 草稿 | true | 不写 → **防越权**（草稿永不进缓存）✓ |
| 已发布 | false | 写 ✓ |

**③ 只缓存已发布 → key 不需要带 user 维度** —— 比"在 key 里拼 userType"更硬：**即使 key 写错了，草稿也不会躺进缓存。**

**④ 注解流的 key 长这样**：`cacheName + "::" + key` = `article::42`
（跟手写流的 `article:read:delta:42` 是两种风格，别对不上）

### 8.4 三大问题（穿透 / 击穿 / 雪崩）—— 讨论结论

| 问题 | 在哪治 | 结论 |
|---|---|---|
| **穿透** | 数据层 | ✅ **已收尾**（2026-10-13 核对代码）：`unless` 已改对；`loadDetail:36` 已 `return null`，「文章不存在」挪到 `getArticleDetail:151` 抛；`CacheConfig` 未关 null 缓存 → null 真的写进缓存，穿透被挡住 |
| **击穿** | 数据层 | 🟡 **知道就行**：`@Cacheable(sync = true)`，但它和 `unless` **语义冲突**（一个要挡在方法外、一个要看方法结果），本项目不做 |
| **雪崩** | 配置层 | 🟡 **知道就行**：TTL 要加随机抖动，得重写 `RedisCacheManager.createRedisCache`。注解层没有这个属性，是**抽象层的代价**，不是漏洞 |

> ⚠️ **`CacheConfig` 里那个"TTL 抖动"是安慰剂**：
> `ThreadLocalRandom.current().nextInt(10)` 在 **bean 创建时只求值一次**，
> 所有 cache 拿到的是**同一个** TTL（比如 34 分钟）→ 不防雪崩。
> 真要做，抖动必须发生在**每个 key 写入时**，也就是重写 `createRedisCache`。

### 8.5 预习：分布式下的 `SecurityContext`

- `SecurityContextHolder` 是 **ThreadLocal** —— 只活在当前请求的那一个线程
- **换线程**（`@Async`）→ 拿不到 → 用 `DelegatingSecurityContextExecutor`
  （`MODE_INHERITABLETHREADLOCAL` 对**线程池**不可靠，因为线程是复用的）
- **跨进程**（微服务）→ ThreadLocal 彻底失效 → 只能靠协议承载：
  下游自己验 JWT / 网关注入内部头（必须防外部伪造）/ Token Relay
- **多实例** → **其实不是问题**，JWT 无状态，任何实例都能自己验签
- **但这正是第 3 关的入口**：JWT 无状态 = 好扩容 + **登不掉**

### 8.6 还没做的事（别忘）

> 状态核对于 **2026-10-13**（对着代码核的，不是对着记忆）。兜底 / `timeout` / `jti` 三项已于当日复查后勾掉。

1. ✅ **文章详情缓存的跨模块失效点已修**（2026-10-14 核对代码）—— `KnowledgeCategoryService` 新增 `evictArticleCache(List<String>)`，两个 `deleteCategory` 都先捞 `affectedArticles` 再 `evictAfterCommit(articleIds)`，`article` 缓存里的 `categoryName` 不再脏读。（仍是"缓存失效点散落在各模块"的好素材，面试能讲）
2. ✅ **防穿透已收尾** —— 见 8.4（`loadDetail:36` 已 `return null`）
3. ✅ 已修：`ArticleContentService` 那句错注释（`//这里判断用户状态`）已经不在了
4. ⬜ `ArticleReadCountTask:45` 的 `read_count = IFNULL(read_count,0) + delta` 仍是**裸字符串拼接**（来源是 Redis 的整数，
   眼下安全，但正式项目该走 `#{}` 参数绑定）
5. ⬜ `article:read:delta:<id>` **没有 TTL 兜底**（有回写补偿了，但进程直接挂掉时没人管）
6. ✅ **Redis 读路径兜底已还清**（第 1 关欠的账）—— 4 处代码 + 1 处配置全部落地，清单见 **9.4**
7. ✅ **`jti` 已补** —— `JwtTokenUtil:52` 已有 `.withJWTId(UUID.randomUUID().toString())`，同秒登录不再签出相同 token
8. ⬜ `JwtAuthticationFilter:98-100` 三行注释掉的死代码删掉（fail-open 已定，留一行说明即可）。
   > 本条原本还列着"无用 import 清理"清单，**已按用户约定删除**（2026-10-16）—— 用户会按 IDE 提示自行清理，**不要提醒、不要写进待办**。以后再看到这条，别把 import 加回来。
9. ✅ **`timeout` 已从 `3000ms` 调到 `500ms`** —— 见 **9.7 治法①**；Redis 挂时分类树接口 9.07s → 约 1.5s

---

## 九、第 3 关收官记录（2026-10-13）

> 第 3 关（JWT 登出黑名单）**功能已实测验收**。这一节记录：定下的设计、踩过的坑、以及**还没还的账**。

### 9.1 验收结果 ✅

用户实测链路：登录拿 token → `/api/user/current` 成功 → `/api/user/logout` → 同一 token 再调 `/api/user/current` **返回 401** → `redis-cli --scan` 能看到 `blacklist:token:<hash>` → `DEL` 掉之后**又能操作**。

**「写黑名单 → 查黑名单 → 拦 401」整条链路正确。**

### 9.2 定下的设计（别再动摇）

| 决定 | 内容 | 理由 |
|---|---|---|
| **TTL = token 剩余有效期** | `exp - now`（毫秒），`<= 0` 直接 `return` | 黑名单只需活到 token 自然过期；用 TTL 做自动清理，**零定时任务** |
| **key 用 `md5(token)`** | `blacklist:token:` + `DigestUtil.md5Hex(token)` | 明文 token 当 key 会撑大内存，且在 `redis-cli` / 日志里裸奔 |
| **per-token 一个 key，不用 Set** | `opsForValue().set(k, "1", ttl, MILLISECONDS)` | **Redis 的 TTL 是 key 级属性，不是成员级**。塞 Set 里：TTL 设短会放行仍有效的 token，设长会永久泄漏；且成员不会自动消失 |
| **黑名单校验放在「验签 + 查用户」之后** | `JwtAuthticationFilter` 第 3 步 | 放前面的话，无 token 时 key 变 `blacklist:token:null`，乱填 token 能刷 Redis |
| **filter 读黑名单 = fail-open** | Redis 挂 → 只记日志 → 放行 | 可用性优先：fail-closed 会让**全站要登录的接口全挂** |
| **logout 写黑名单 = fail-closed** | Redis 挂 → 抛「登出失败，请重试」 | 静默失败会**骗用户以为已登出**，而旧 token 在 Redis 恢复后仍然有效 |

> ⭐ **两个方向相反是刻意的。判据是「失败后用户会不会被误导」**：
> 会（登出）→ 宁可报错；不会（读缓存 / 查黑名单）→ 降级放行。
> **能讲出这个判据，比背结论值钱。**

### 9.3 第 3 关踩过的坑（全是实测）

| # | 坑 | 症状 | 真因 |
|---|---|---|---|
| 1 | ⭐ **写读 key 不一致（最致命）** | 登出后 token **照样能用**；`redis-cli` 里 key 明明存在 | `UserService` 写的是 `...md5Hex(token)`，`JwtAuthticationFilter` 读的是 `...token`（**漏了 md5**）→ 查询永远 null → **黑名单静默失效**。这是 5.1 坑 D / 8.2 坑 3 的**第三次复现** |
| 2 | **filter 里 `throw BusinessException`** | 返回 500 而非 401，且不是 `Result` 格式 | **filter 的异常到不了 `@RestControllerAdvice`**（`GlobalExceptionHandler` 只管 Controller 层）→ 冒泡到 Tomcat。正解：`clearSecurityContext()` + `ResponseUtil.WriteError(...)` + `return` |
| 3 | **`StringRedisTemplate` 字段没注入** | NPE | 手写流用的是**有状态 bean**，必须注入（对比：`DigestUtil` 是无状态工具类，静态调用即可） |
| 4 | **catch 范围过大** | token 非法被误记成「Redis 出问题」，日志误导 | `verifyToken` 的 `JWTVerificationException` 被一起吞了。**只包 `redis.opsForValue().set(...)` 那一行** |
| 5 | **降级保护加错层** | filter 已放行，业务层照样 500 | 降级是**每个 Redis 调用点**的责任，加在入口（filter）拦不住 Service 的异常 |
| 6 | **`NumberFormatException`** | 定时任务日志里出现 | 见 9.5 |

### 9.4 ✅ 已还清：Redis 读路径兜底（第 1 关欠的）

> **状态（2026-10-13 对着代码核对）：4 处代码 + 1 处配置全部落地。**
> 唯一需要你亲手改的是 `delta` 初值，见下面「三个必须注意的点」第 2 条 —— **已改**。

**A. 手写流 —— 每个调用点自己包（4 处，✅ 全已落地）**

| 位置 | 原现状 | 降级策略 | 日志级别 | 状态 |
|---|---|---|---|---|
| `KnowledgeCategoryService.listAll:48` | `opsForValue().get(...)` 裸调用 | **当 miss**（dto 置 null，继续往下查库） | `warn` | ✅ |
| `KnowledgeCategoryService.listAll:57` | 回填 `set(...)` 裸调用 | 只记日志，不影响返回 | `warn` | ✅ |
| `KnowledgeArticleService.getArticleDetail:163` | `increment(READ_DELTA_KEY+id)` | 降级，`delta` 给 **0** | `warn` | ✅ |
| `KnowledgeArticleService.getArticleDetail:164` | `opsForSet().add(READ_DIRTY_KEY,id)` | 同上 | `warn` | ✅ |

⚠️ **三个必须注意的点**：

1. `:163` 和 `:164` **是一个整体，必须一起包**。只包 163 会出现「计数加了但没进脏集 → 定时任务永不回写 MySQL → **计数丢失**」。（已满足：两处都包了）
2. ⭐ **包了之后 `delta` 就没值了，`dto.setReadCount(... + delta)` 会 NPE** → 初值必须给 `0`。**最容易漏的一处 —— 实测这里确实漏了**：原来写 `Integer delta = null;`，catch 里没赋 0 → Redis 一挂就 `int + null` → **NPE 500**。**已改成 `Integer delta = 0;`** ✅
3. `listAll:48` 的 catch 里**不能 `return` 空列表** —— 空列表会被上层当成「分类树真的是空的」，前端拿到空菜单。fail-open 的正确姿势是「**假装没命中缓存**」，不是「给个假值」。（已满足）

**B. 注解流 —— 一处配置全覆盖 ✅ 已落地**

`CacheConfig` 实现 `CachingConfigurer` + 自定义 `CacheErrorHandler`，一次性覆盖：
- `ArticleContentService.loadDetail` 的 `@Cacheable`
- `KnowledgeArticleService` 三个 `@CacheEvict`（`:132` / `:181` / `:192`）

**要点**：

- `CachingConfigurer` 的四个方法（`cacheManager` / `cacheResolver` / `keyGenerator` / `errorHandler`）**全是 `default` 方法、默认返回 null**（Spring 5.1+）→ **只需要 override `errorHandler()` 一个**，另三个删掉。
- ⚠️ **千万别给 `cacheManager()` 填东西** —— 填了会**顶掉自己的 `@Bean cacheManager`**。
- 正确的导入包（**两个接口各有一套「四个方法」，别混**）：
  - `org.springframework.cache.annotation.CachingConfigurer`（外层，含 `errorHandler()`）
  - `org.springframework.cache.interceptor.CacheErrorHandler`（内层，含 4 个 `handleCacheXxxError`）
- 默认的 `SimpleCacheErrorHandler` 是**原样重抛** → 这就是现在 Redis 一挂就 500 的原因。
- `handleCacheGetError` 不抛 = **当 miss = 自动回源查库**，行为天然正确。
- 内层 4 个 `handleCacheXxxError` **本来就是 `void`**（只记日志、不 return）；外层 `errorHandler()` 必须**返回对象**、不能改 `void`。

**⚠️ `@CacheEvict` 的隐藏问题**：它默认在方法**成功返回后**执行 → evict 失败时 **DB 已提交但接口 500** → 「**误导型失败**」（用户以为没改成，其实数据改了）。

**C. 日志分级（别四个都写 `error`）**

| 回调 | 失败后果 | 级别 |
|---|---|---|
| `handleCacheGetError` | 当 miss 回源 → **结果正确**，只是慢 | `warn` |
| `handleCachePutError` | 下次仍 miss → **结果正确** | `warn` |
| `handleCacheEvictError` | 数据已改但缓存未失效 → **TTL 内读到旧值** | `error` |
| `handleCacheClearError` | 同上，范围更大 | `error` |

**四个全写 `error` 的后果**：Redis 一挂，每个请求每条 error → **刷屏淹没真问题**。

**日志三件套**：上下文（`cache.getName()` + `key`，拼起来就是 Redis 里的真实 key，如 `article::42`）+ 后果（`结果正确` / `TTL 内读到旧值`）+ 动作（去查哪个 key / 检查 Redis 连通性）。

> 这条日志被触发 = **基础设施故障（Redis 挂了）**，不是业务 bug → 第一反应是查 Redis，不是翻业务代码。

**D. 已经做好的（不用动）**

`KnowledgeCategoryService:63-69` 的 evict、`JwtAuthticationFilter:88-101`、`UserService:119-124`、`ArticleReadCountTask` 4 处、`OSSUtil.delete`、`FileCleanupTask`、`FileCompensator`、`PsychologicalSupportService(.doOnError)`。

> **判据第一步是「它真的发 IO 吗」**：`OSSUtil.getUrl` 是**纯字符串拼接、不发网络请求** → 不需要兜底。
> **流式（SSE）场景的兜底不是 try-catch**（响应已开始写，没法抛给调用方）→ 只能 `sink.error(error)` 推进流里。**兜底形式取决于通信模型。**

### 9.5 `NumberFormatException` 的排查（第 2 关遗留）

**抛出点**：`ArticleReadCountTask:40` `Integer.valueOf(deltaStr)` —— 全项目**唯一**做「字符串转整数」的地方。

**最可能的原因：序列化器污染。** 若有任何地方用 `RedisTemplate<String,Object>`（`redisconfig` 里那个 JSON 序列化器）往同一个 key 写过 String，Jackson 会**带引号**存 → Redis 里是 `"1"` → `StringRedisTemplate` 读回来是带引号的 `"1"` → `Integer.valueOf("\"1\"")` 炸。**又是 8.2 坑 3 那一族。**

**诊断命令**：

```bash
redis-cli --scan --pattern "article:read:delta:*"
redis-cli GET article:read:delta:<id>              # 看有没有引号
redis-cli OBJECT ENCODING article:read:delta:<id>  # int / embstr / raw
```

🔴 **不要把 `catch` 换成 `Exception` 扩大包围圈。** NFE 和 Redis 故障是两类东西：

| | `RedisConnectionFailureException` | `NumberFormatException` |
|---|---|---|
| 性质 | 环境故障 | **数据 / 代码错误** |
| 会自愈吗 | 会（Redis 恢复就好） | **不会**（坏数据一直在） |
| 该吞吗 | 该（降级） | **不该**（要暴露） |

**吞掉 NFE 的丢数据链**：`getAndDelete` 读后即删（值已删）→ NFE 抛在 `Integer.valueOf` → catch 把 id 放回脏集 → 下轮 pop 出后 `getAndDelete` 返回 null → `continue` → **这次阅读增量永久丢失**，只留一条 `"单点增量失败，放回脏集合"` 的 warn（文案还误导成 Redis 抖动）。

**正解**：**区分着 catch** —— 环境异常降级；NFE 用 `log.error` 并**打出原始值**（`log.error("增量值非法, articleId={}, 原始值=[{}]", articleId, deltaStr)`）；「永远不可能成功」的坏数据不该放回脏集。

### 9.6 顺带讲清的三个概念

**① `evictAfterCommit` 为什么要套一层**（完整推演见 5.5 方案 1）

- Redis 操作**本身**与 DB 事务无关 ✅；但它的**执行时机必须锚定在事务边界上** ❌（这是**缓存一致性**问题，不是事务问题）
- 两层语义：`evictAfterCommit()` = **注册**（把「删缓存」挂到当前事务）；`evictCategoryTreeCache()` = **真正的动作**（DEL + try-catch）。拆开是为了复用 + 把「何时删」的决策集中一处
- **不能写在 `@Transactional` 方法体最后一行** —— 方法体执行完，**事务还没提交**（提交发生在方法返回后，由 AOP 代理做）

**② MySQL 事务 vs Redis 的三层「受限」**

| | MySQL | Redis |
|---|---|---|
| 隔离级别 | 有 | **没有** |
| 回滚 | 有 | **没有** |
| 可见性 | 提交后才对别人可见 | **写进去全世界立刻可见** |

→ 事务未提交时 DB 查不到新数据 → 任何「回源读 DB 再写 Redis」都拿到旧值（就是 5.4 问题 ①）。

**「出 null」的两个来源**：
- a) `@Cacheable` 把 null 当有效缓存值返回（`allowCacheNullValues` 默认 `true`）→ 命中时**方法体一行不执行**、直接返回 null → 下游 NPE（`loadDetail` 的 `if(a==null) return null` 就是为此）
- b) **`TransactionSynchronizationManager.registerSynchronization()` 在无事务时抛 `IllegalStateException`**（`Transaction synchronization is not active`）—— 现在两个调用点都在 `@Transactional` 里所以安全

**③ 「框架开销」是什么** —— 一个认证请求要走：
① 接包 + Security 过滤链（验签 + `getUserById` ← **第 1 次查库** + 查黑名单）② Spring MVC ③ AOP 拦截器 ④ 业务 + MyBatis（**第 2 次查库**）⑤ 序列化 + 同步日志

⭐ **每个认证请求查两次库**（filter 的 `getUserById` + 业务查询）—— 这是框架开销里最大的一块，也是后续可优化的点。

> **冷启动「首次秒级、之后 23ms」**：前几百次解释执行、类加载、首次分发 / 解析 / 构建；23ms 里 MySQL 只占 1~3ms（见 5.2），大头是框架开销 + **同步 debug 日志**。
> **Redis 挂后「首次慢、之后快」** = Lettuce 懒连接 + 首次等超时（`timeout: 3000ms`）→ 之后快速失败。**「变快是放弃，不是修好」。**

### 9.7 延迟治理：`timeout` + 熔断

#### 现象：Redis 挂掉后，分类树接口 **9.07s**

用户关掉 Redis 终端后访问 `/knowledge/category/tree`，TTFB **9065ms**（下载 0.41ms / 处理 0.05ms → 纯服务端等待，不是网络问题）。

**诊断（已核对代码，非推测）**：`SecurityConfig:25-30` 的 `PUBLIC_PATHS` = `/`、`/api/user/login`、`/api/user/add`、`/error`，**不含分类树** → filter 完整执行 → 一次请求碰 Redis **3 次**，各等满一次超时：

| # | 位置 | 等完后 |
|---|---|---|
| 1 | `JwtAuthticationFilter:89` 查黑名单 | fail-open，放行 |
| 2 | `KnowledgeCategoryService:48` 读缓存 | 当 miss，继续查库 |
| 3 | `KnowledgeCategoryService:57` 回填缓存 | 放弃回填 |

**3 × 3000ms = 9000ms ≈ 9065ms** ✅ 完全吻合。

> 反推：**兜底已经生效**（否则第 2 次就抛异常 → 500，总共只会 2 次超时 = 6s）。
> **但兜底只兜住了「异常」，没兜住「等待」。**

#### ⭐ 核心认知：降级有两个维度

| 维度 | 问题 | 靠什么解决 | 状态 |
|---|---|---|---|
| **正确性** | 会不会 500 / 数据会不会错 | **try-catch** | ✅ 已做 |
| **可用性** | 会不会变慢 / 慢多久 | **timeout + 熔断** | ❌ 没做 |

**`try-catch` 能让程序继续跑，但它管不了等待。**
选择 fail-open（Redis 挂就放行），就必须先付出"等完超时"的代价才能放行 —— **fail-open 的代价就是延迟**。

#### 为什么是"等满 3s"而不是"立即拒绝"

`spring.data.redis.timeout: 3000ms` 是**命令超时**（command timeout），**不是 TCP 连接超时**。Lettuce 的行为：

```
连接断了 → autoReconnect 尝试重连 → 命令进 pending 队列【继续等】
        → 一直等到 command timeout（3s）才抛 RedisConnectionFailureException
```

即使 Redis 进程被 kill（端口立刻关闭，connection refused 本该毫秒级），命令也要**等满 3 秒**。这 3 秒不是"网络慢"，是"**Lettuce 不肯放弃**"。

（Spring Boot 的 Lettuce 默认 `shareNativeConnection=true`，所有命令共享同一个 native connection —— 它挂了，所有命令在同一个队列里排队等。）

#### 治法 ①：调小 `timeout`（本项目的正解）✅ 已做

```yaml
spring:
  data:
    redis:
      timeout: 500ms      # ← 原来 3000ms（✅ 已改）
```

本项目实测 Redis 往返 **0.348 ~ 1.268ms**（见 5.2）→ 500ms 是 **400 倍余量**。
改完 9s → 1.5s。**一行改动、零风险。✅ 已改。**

#### 治法 ②：熔断器（Circuit Breaker）

用户自己描述出了完整机制，正是它的三态：

| 用户的话 | 专业叫法 | 行为 |
|---|---|---|
| "检测到 Redis 坏了，熔断" | **Closed → Open** | 失败率/次数超阈值 → 打开，之后所有请求**直接快速失败，根本不碰 Redis** |
| "过一段时间" | **Open** | 等 `waitDurationInOpenState`（如 30s），期间请求全秒回 |
| "发几个请求试探" | **Open → Half-Open** | 放**有限个数**（3~10 个）去真打 Redis |
| "逐渐升级" | **Half-Open → Closed** | 试探成功 → 完全恢复；失败 → 退回 Open 再等一轮 |

**关键在"打开期间不碰 Redis"** —— 这才是它治延迟的原理：第 1 个请求等 3s，后面全部 0ms 直接走降级分支。

**会被追问的五个设计点**：

1. **阈值按失败率还是失败次数？** 通常**两者都要**（至少 N 次调用 **且** 失败率超 X%）。只按次数，低流量时偶发失败会误触发；只按比例，分母太小不可信。
2. **半开放几个？** 放 1 个 → 偶发失败就反复横跳；放 100 个 → 等于没熔断。一般 **3~10 个**。
3. ⭐ **慢调用也算失败** —— 这条最重要，正好治上面那个「**不报错但很慢**」的问题。Resilience4j 的 `slowCallDurationThreshold`：超过 X ms 的调用算"慢调用"，慢调用率超阈值也触发熔断。
   **只看"抛没抛异常"是熔不断的** —— 网络卡住时请求会一直挂着不报错，永远到不了阈值。
4. **fallback 兜底方法** —— 本项目里 = 当 miss 回源查库，和 `CacheErrorHandler.handleCacheGetError` 是**同一个动作**。所以熔断器的 fallback 和现有降级逻辑天然是一套东西。
5. **包在哪一层？** 包住一个**统一的 Redis 封装**，比逐个调用点配更好（又一次印证"降级是每个调用点的责任"）。

#### 生产方案：Resilience4j vs Sentinel（与 Spring Cloud Alibaba 的关系）

| 方案 | 归属 | 引入方式 |
|---|---|---|
| Netflix **Hystrix** | Spring Cloud Netflix | 🔴 **2018 年停止维护**，别再用 |
| **Resilience4j** | 社区库，Spring Cloud Circuit Breaker 默认实现之一 | `resilience4j-spring-boot3` + `@CircuitBreaker(name="redis", fallbackMethod="...")` + yml 配置 |
| **Sentinel** | **Spring Cloud Alibaba 核心组件** | `sentinel-spring-boot-starter`，带控制台；功能更强（流控 / 熔断 / 热点 / 系统自适应保护） |

> ⚠️ **重要澄清：熔断器不需要 Spring Cloud。**
> Resilience4j 和 Sentinel **都是普通的 Spring Boot 库** —— 直接加到任何 Boot 项目就能用，**不需要 Nacos、不需要注册中心、不需要微服务架构**。
> **熔断 ≠ 微服务专属**，单体项目也能用，只是收益小。

**但本项目建议不上**：本地单机 Redis，挂了就是挂了，没有"网络抖动"这种需要精细判断的场景；引框架 = 引依赖 + 一层 AOP + 一堆配置，收益主要在**分布式 / 云环境**（那里 Redis 是远程的，网络真的会抖）。

**想理解原理可以手写**，核心就两个字段：

```java
public class SimpleCircuitBreaker {
    private final int failureThreshold;          // 连续失败几次就打开
    private final long openDurationMs;           // 打开后等多久
    private final AtomicInteger failures = new AtomicInteger();
    private volatile long openUntil = 0;         // 0 = 未打开

    public boolean allowRequest() {
        if (openUntil == 0) return true;                              // Closed：放行
        if (System.currentTimeMillis() < openUntil) return false;     // Open：快速失败
        return true;                                                  // Half-Open：放行试探
    }

    public void recordSuccess() { failures.set(0); openUntil = 0; }   // 试探成功 → 关闭
    public void recordFailure() {
        if (failures.incrementAndGet() >= failureThreshold) {
            openUntil = System.currentTimeMillis() + openDurationMs;  // 打开
        }
    }
}
```

> 骨架能跑通三态，但**离生产级差得远**：并发下的状态竞争、半开状态要限制并发数、失败统计要用**滑动窗口**而不是简单计数。所以它是**学习练习**，不是生产方案。

#### ⚠️ 纠正一个认知：熔断**不是**「融入 Redis 的最终方案」

熔断器是**通用可靠性组件**（对 MySQL、对第三方 API、对任何远程调用都一样），**不是 Redis 专属**。Redis 集成的深度是另一个维度：

| 维度 | 内容 | 在哪 |
|---|---|---|
| 数据结构用法 | String / List / Set / ZSet / Hash / BitMap / HLL / Stream | 第 4~7 关 |
| 一致性与降级 | 缓存一致性、穿透/击穿/雪崩、TTL、fail-open/closed、熔断 | 第 1~3 关 + 9.4 + 9.7 |
| **可用性架构** | **主从复制 → 哨兵（自动故障转移）→ Cluster（分片）** | 知识线 —— **这才是"最终级方案"** |
| 客户端层 | 连接池、序列化、Pipeline、Lua 脚本 | 零散 |

> **熔断 vs 高可用，治的是两头**：
> - **熔断** = Redis 挂的时候，我这边**优雅降级、别被拖垮**（**客户端**侧）
> - **高可用架构** = 让 Redis **尽量不挂 / 挂了自动恢复**（**服务端**侧）
>
> **熔断治不了"Redis 挂了"本身**，它只是"挂的时候别把我也带走"。

#### 面试话术

> 「我的降级一开始只兜住了异常，没兜住延迟 —— Redis 挂掉时接口要 9 秒才返回。定位发现是命令超时 3s 乘了三个 Redis 调用点。于是我做了两件事：一是把超时从 3s 降到 500ms（实测往返只有 0.35~1.27ms）；二是引入熔断器，失败后进入打开状态直接快速失败，半开时放少量请求试探。**这里最关键的一点是慢调用也要算失败** —— 否则请求一直挂着不报错，熔断永远不会触发。」

---

## 十、第 4 关：热门文章排行榜（ZSet）（2026-10-14 起）

> 状态：**✅ 已完成并落地**（2026-10-14）。收官记录见 **10.6**。

### 10.1 定下的两个决策

| 决策 | 选择 | 理由 |
|---|---|---|
| **热度语义** | **最近 7 天**（不是累计阅读量） | 累计会让老文章永远霸榜，"热门"退化成"最老" |
| **数据来源** | **实时累加 + 定时预热** | 实时保证新鲜度；定时把 7 天合并好，读榜不用每次 `ZUNIONSTORE` |

### 10.2 设计：天级 key 滑动窗口

| 环节 | 做法 |
|---|---|
| **天级 key** | `article:hot:2026-10-14` —— `getArticleDetail` 里 `ZINCRBY ... 1 <id>`，**和 `INCR delta` 放同一个 try**（一起成功/失败，不产生偏差） |
| **TTL = 8 天** | **必须显式 `expire`**（`incrementScore` 不管 TTL）。8 天 = 7 天窗口 + 1 天冗余 → **滚出窗口靠 TTL 自动完成，零清理任务**（同第 3 关黑名单的思想） |
| **合并 key** | `article:hot:7d` —— 定时任务 `unionAndStore` 最近 7 个天级 key（**聚合默认 SUM**） |
| **读榜** | `reverseRangeWithScores("article:hot:7d", 0, 9)` → 回库补全 → **按 ZSet 顺序重排** |

### 10.3 ⚠️ 「7 天窗口」和「DB 定时校正」为什么打架

一开始选了「7 天窗口 + 定时校正」，这两者在语义上冲突：

- **DB 里只有累计 `read_count`，没有按天数据** → 定时任务拿不到"某一天读了多少"，无法校正某一天的 key
- **"定时校正"只在累计榜上成立**（用 DB 权威总量覆盖 Redis 累加值）
- 所以定时任务的角色要**从"校正数值"改成"合并预热"**（`ZUNIONSTORE`）
- **7 天窗口天然不需要 DB 校正**：窗口滚动 = 自洁，7 天前的 key 自动消失，不积累漂移。只有**单调递增的累计榜**才会漂，才需要校正

### 10.4 四个坑

1. **时区** —— `LocalDate.now()` 用 JVM 默认时区；MySQL 配的是 `Asia/Shanghai`，两边可能不一致 → 跨零点 key 错位。统一，或显式 `LocalDate.now(ZoneId.of("Asia/Shanghai"))`
2. **`incrementScore` 不设 TTL** —— 天级 key 的 8 天 TTL 必须**显式** `expire`
3. **`selectBatchIds` 返回顺序不保证** —— 回库补全后必须**按 ZSet 顺序重排**，否则"第 1 名"会随机变
4. **必须用 `StringRedisTemplate`** —— 用 JSON 序列化器会把 member 写成带引号的 `"a1b2..."`，`redis-cli` 里对不上（8.2 坑 3 的**第四次复现**）

> 另：`score` 是 `double`；`KnowledgeArticle.id` 是 `String`（UUID）；`/knowledge/article/hot` 与 `/knowledge/article/{id}` 共存时**精确路径优先**。

### 10.5 验收

```bash
redis-cli --raw --scan --pattern "article:hot:*"
redis-cli --raw ZREVRANGE article:hot:7d 0 9 WITHSCORES
redis-cli TTL article:hot:<今天>        # 约 8 天
redis-cli TTL article:hot:7d            # 约 90 秒
```

### 10.6 ✅ 收官记录（2026-10-14）

**最终实现（4 处）**

| # | 位置 | 做什么 |
|---|---|---|
| 1 | `KnowledgeArticleService.getArticleDetail` | 非管理员分支里 `incrementScore("article:hot:"+今天, id, 1)` + `expire` 8 天（和 `INCR delta` 同一层 try） |
| 2 | `ArticleReadCountTask.preheatArticleHot()` | 每 30 秒 `unionAndStore` 最近 7 个天级 key → `article:hot:7d`，`expire` 90 秒 |
| 3 | `KnowledgeArticleService.getHotArticle()` | 取榜（**多取 `TOP*2`**）→ 保序取 id → 回库 + **过滤已发布** → `complementArticleDTO` 补名字 → 按榜单顺序取 + `limit(TOP)` |
| 4 | `KnowledgeController` + 前端 | `GET /api/knowledge/article/hot`；前端「推荐阅读」改调它 |

**定下来的设计（别再动摇）**

| 决策 | 内容 |
|---|---|
| 热度语义 | **最近 7 天**（不是累计 `read_count`） |
| 数据来源 | 实时 `ZINCRBY` 累加 + 定时 `ZUNIONSTORE` 预热 |
| 天级 key TTL | **8 天**（7 天窗口 + 1 天冗余）→ 滚出窗口靠 TTL，**零清理任务** |
| 合并 key TTL | **90 秒**（任务间隔 30 秒的 3 倍）→ 任务挂了能自愈，正常时不闪空 |
| 读榜失败 | **返回空数组**（`List.of()`）+ 前端 `v-if="list.length"` 隐藏；**不回库**（DB 只有累计值，拿它冒充 7 天热度是**数据欺骗**） |
| 过滤位置 | **补名字之前**（article 层）—— `complementArticleDTO` 只负责"补名字"，不该知道"已发布" |
| 定时任务线程池 | `spring.task.scheduling.pool.size: 2`（默认 1 → 多个 `@Scheduled` 会串行） |

**踩的坑**

| # | 坑 | 后果 | 教训 |
|---|---|---|---|
| 1 | 🔴 **定时任务 key 少冒号**（`"article:hot"+日期`） | 与写入端 `"article:hot:"` 对不上 → 合并 7 个空 key → **榜单永远空** | 第 3 关「写读 key 不一致」的**同构复现**。key 拼接要收敛到常量/工具方法，别手写两遍 |
| 2 | 过滤已发布放在"补名字之后" | 草稿白查作者名/分类名；过滤后可能不足 10 条 | 过滤前移 + 多取 2 倍 + `limit(TOP)`（`limit` 必须在 `filter(nonNull)` 之后） |
| 3 | `TOP` 写在方法内还用 `private static final` | 编译不过（局部变量不能用 `private`/`static`） | 常量提到类字段 |
| 4 | `Integer delta` / `increment(...).toString()` | IDE 警告 + NPE 风险 | 用 `int`；`Long v = increment(...)` 判 null 后 `.intValue()` |

**顺带重构**：`complementArticleDTO` 从「只服务分页（`Page` → `IPage`）」改成「接收 `List<KnowledgeArticle>`、返回 `Map<id, DTO>`」—— `selectPage` 和 `getHotArticle` 共用，`getHotArticle` 里原来的 `byId` 中间变量因此删掉。

---

## 十一、第 5 关：限流 + 防重复提交（2026-10-15）✅

> 状态：**✅ 已完成**。场景 A（登录/注册限流）与场景 B（日记防重）均已实现并实测。
> ⏭️ `@RateLimit` 注解 + 切面**有意推迟**到第 7 关之后（见 **11.6**）—— **不是漏做**。
> 📌 部署前检查项 + 遗留小优化见 **11.4**。

### 11.1 实际落地（跟原方案的差异）

| 场景 | 原方案（第四节） | **实际实现** | 为什么改 |
|---|---|---|---|
| **A 登录限流** | 固定窗口 `INCR login:try:<username>:<minute>` + `EXPIRE 60` | **滑动窗口**（ZSet 存时间戳）+ **用户名 / IP 双维度** | 滑动窗口没有固定窗口的**临界问题**；且要同时按 IP 和用户名两个维度限 |
| **B 日记防重** | `SET NX` 幂等键 = `userId + 内容摘要` | `SET NX`，key = **纯 `userId`**，TTL **15 秒** | 业务假设：**写一篇日记至少要 15 秒**，窗口内二次提交必为异常。不需要内容摘要（见 11.2） |

### 11.2 定下的设计（别再动摇）

| 决策 | 内容 |
|---|---|
| **限流算法** | **滑动窗口**（ZSet：member = `时间戳-UUID`，score = 时间戳）。⚠️ member **必须带 UUID** —— 纯时间戳做 member 会在同一毫秒被 ZSet **去重**，导致少算 |
| **双维度阈值** | 用户名 60 秒 **10** 次 / IP 60 秒 **30** 次（IP 阈值更大：NAT 出口后面可能有很多正常用户） |
| **超限处罚** | 写黑名单，TTL **3 分钟**（不是永久封） |
| **黑名单读取时机** | 应**先查黑名单、命中就早退** —— 否则被封的 IP 继续打，会一直白写 ZSet |
| **Redis 挂掉** | `times` 保持 `null` → 判断短路 → **放行**（fail-open）。登录接口可接受，但要记住「**Redis 一挂，DoS 防御归零**」 |
| **成功登录** | 只清**用户名**计数，**不清 IP** 计数 —— IP 计数代表"该 IP 的总请求量"，不该因一次成功就归零 |
| **日记防重锁** | 纯 `userId` + 15 秒；**成功后绝不 `delete`** —— 否则"提交成功 → 释放锁 → 再点一次 → 又能提交"，防重直接失效 |
| **防重 vs 幂等** | **防重锁必须等 TTL 自然过期**，与分布式锁「用完立刻释放」**语义相反** |

### 11.3 踩过的坑（教学价值高）

| # | 坑 | 后果 | 教训 |
|---|---|---|---|
| 1 | 🔴 **黑名单「读的 key」少拼后缀** —— 读 `get(LOGIN_IP_BAN)`，写 `LOGIN_IP_BAN + ip` | 读永远 `null` → **封禁形同虚设**（超限还拦得住，但"封 3 分钟"是假的：ZSet 1 分钟过期后立刻放行） | 第 3 关「写读 key 不一致」、第 4 关「少冒号」的**第三次同构复现**。**key 拼接要收敛到常量/工具方法，别手写两遍** |
| 2 | 🔴 **布尔判断方向写反**（`Boolean.TRUE.equals(locked)` 当成"抢锁失败"用） | 第一次提交被拒，**重复提交反而通过** | 遇到布尔判断，**先写三态真值表再写 if**，别凭直觉。`setIfAbsent` 三态：`true`=抢锁成功(放行) / `false`=重复(拒绝) / `null`=异常(**放行**) |
| 3 | 🔴 **变量名与值语义相反**：`Boolean passwordMatch = !passwordEncoder.matches(...)`，再 `if(!passwordMatch.equals(TRUE))` | 双重取反 → **密码错的能登录、密码对的被拒**（**认证绕过**） | 名字说"匹配"、值却是"不匹配"，后面判断必反。**变量名必须和值语义一致**，别叠加多个 `!` |
| 4 | `expire` 被当成"缩短 TTL"用 | `expire` 是**重置**不是缩短；攻击者每 5 秒内提交一次就能**无限续期** → 用户被永久锁死 | 认清 `expire` 的语义；防重锁在 TTL 内**不要做任何操作**。**✅ 已修（2026-10-15，整段删除）** |
| 5 | `Boolean locked = null` + `if(!locked)` | Redis 挂 → `null` 自动拆箱 → **NPE 500** | 用 `Boolean.FALSE.equals(x)`（常量在前，天然免疫 NPE） |

> 坑 1/2/3 都是**同一类**：**"成功"和"失败"两个分支被搞混**。写完布尔判断，用真值表过一遍，成本 30 秒，能省 3 小时。

### 11.4 ⚠️ 待办（接手先看这里）

> 状态核对：**2026-10-15 15:36**。**编译错误已清零**（`UserService` 的 `sw.prettyPrint()` 已删、`common/BCryptExecute.java` 已整体删除 —— 异步化方案放弃）；**`DiaryService` 的 `expire` 段已删** ✅。

| 优先级 | 事项 | 位置 |
|---|---|---|
| ✅ | ~~`expire` 段~~ **已于 2026-10-15 删掉** —— `expire` 是**续期**不是缩短，与"防续期"意图相反；被脚本反复触发会把锁无限续期。重复提交时什么都不做、直接 `throw` 即可（锁的 TTL 来自 `setIfAbsent` 的第三个参数，不受影响） | `DiaryService` ✅ |
| 📌 | **`getClientIp()` 只取 `getRemoteAddr()`** —— **本地无 Nginx 时这是对的、不用改**；**部署到有反代的服务器之前必修**。正确顺序：**`X-Real-IP` 优先**（Nginx 用 `$remote_addr` **覆盖**写入，伪造不了）→ 再 `X-Forwarded-For`（**取最后一个**，因为 `$proxy_add_x_forwarded_for` 是**追加**语义，第一个可能是客户端伪造的）→ 最后 `getRemoteAddr()` 兜底。⚠️ **IP 不可信时不要拿它当限流键** —— 会退化成"所有人共用 127.0.0.1 → 一起被封"，比不限流更糟 | `UserService:193` |
| 🟡 | **本地会拿到 IPv6 回环 `0:0:0:0:0:0:0:1`**（2026-10-17 实测 `login:try:ip:0:0:0:0:0:0:0:1`）—— `localhost` 优先解析成 `::1`，所以 `getRemoteAddr()` 返回 IPv6 形式。**同一个客户端可能一会是 `127.0.0.1`、一会是 `::1` → 两套独立计数器 → 限流被稀释一半**（key 里带冒号本身合法，Redis key 是二进制安全的，不影响功能）。归一化写法见下 | `UserService:193` |

```java
private String getClientIp(HttpServletRequest request){
    String ip=request.getRemoteAddr();
    if(ip==null) return "unknown";
    // IPv6 回环 → 统一成 IPv4 回环，否则同一台机器两套计数器
    if("0:0:0:0:0:0:0:1".equals(ip)||"::1".equals(ip)) return "127.0.0.1";
    // IPv4-mapped IPv6（::ffff:192.168.1.5）→ 剥掉前缀
    if(ip.startsWith("::ffff:")) return ip.substring(7);
    return ip;
}
```

> 判断"是不是 IPv6"只需 `ip.contains(":")`（IPv4 地址里不可能有冒号）。**不要**用 `InetAddress.getByName(ip).getHostAddress()` 去"归一化" —— 它会把 `127.0.0.1` 也变成 `0:0:0:0:0:0:0:1`，方向反了。
| 🟡 | **黑名单命中应早退** —— 命中后不要再走 `zadd` / `zCard`（被封的 IP 继续打会白写 Redis） | `UserService.checkIpLimit` |
| ⏭️ | **`@RateLimit` 注解 + 切面 —— 已决定推迟，不是漏做**。做完第 7 关后统一做「手写流 → 注解流」改造，见 **11.6** | 新增 |
| 🟢 | `IP_LIMIT_KEY` 定义了没用（要么用要么删）；`log.info("日记重复提交")` 建议降 `debug` | `DiaryService:40` / `:84` |
| 🟡 | **`diary:dup:<userId>` 15 秒锁的语义是混的**（2026-10-17 用户报"日记只能写一篇"）。它既不是**防重复提交**（真正的防重复要看**内容** —— 双击、网络重试送来的是同一份内容），也不是**"一天一篇"的业务规则**（那该用 DB 唯一索引 `(user_id, diary_date)`）。当前实现是**按 userId 的粗暴限流**：15 秒内同一用户写不了第二篇，**"补记昨天 + 今天"会被误伤**。三条路：① 缩短到 3 秒（只防双击，最省事）② 换成内容指纹 `MD5(userId+diaryDate+content)`、TTL 60 秒（真正的防重复）③ 若业务就是"一天一篇"→ DB 加唯一索引，Redis 锁可直接删。**✅ 2026-10-17 用户已采用 ②**：`DiaryService:85-88` 先算 `fp=md5(userId|diaryDate|diaryContent)`，锁 key 变成 `diary:dup:<fp>`、TTL 60 秒，insert 失败时 `releaseDupLock` 主动删锁。**遗留**：`:84` 注释还写着"5s"，实际是 60s | `DiaryService:81` |

> ⚠️ **做拦截器时的大坑**：**不要复用 `JwtAuthticationFilter.shouldNotFilter()`**（它基于 `SecurityConfig.isPublicPATH()` 跳过公开路径），因为 `/api/user/login`、`/api/user/add` **正好在白名单里** —— 复用会导致**最该限流的登录接口反而不限流**。

### 11.5 性能排查结论（别再去优化 BCrypt）

用 `StopWatch` 分段打点实测：**第一次请求 DB 与 BCrypt 各占一半；第二次之后大头基本是 BCrypt**。

- **DB 那半是冷启动**（HikariCP 建连 + MyBatis 初始化 + JIT），稳定后 DB 不是瓶颈 → **不用再查 DB**
- **BCrypt 100ms 是设计代价，不是 bug**：strength=10 → 2¹⁰ 轮迭代。**不要为了"看起来快"降 strength**（安全性等比例下降）
- **被拉黑时 7ms** = 早退跳过了 DB + BCrypt → 反过来印证 BCrypt 就是大头
- **真正的风险不是"慢"，是"堵"**：BCrypt 是 **CPU 密集**操作，会**占住 Tomcat 工作线程**；高并发下 200 个线程全被占满 → **连首页、文章列表这些不需要 BCrypt 的接口也一起卡死**
- **所以第 5 关限流的真正价值**：BCrypt 慢意味着攻击者**成本极低**就能让服务器 CPU 满载（每个登录请求强制一次 100ms 计算）→ **限流是保护这个 CPU 黑洞的唯一防线**
- 若真要优化：**BCrypt 异步化**（丢到独立线程池，线程数 ≈ **核数 + 1**，Tomcat 线程立刻释放）。⚠️ 异步线程里 `RequestContextHolder` **拿不到 request** —— 必须提前把 IP 等纯数据取出来再传进去
- 其他低成本项：`WHERE username = ? OR email = ?` 用 `EXPLAIN` 看是否全表扫描（OR 条件难走索引）；前端做 token 续期，减少登录接口调用频次

### 11.6 ⏭️ 后续规划：「手写流 → 注解流」统一改造（第 7 关之后做）

**决定（2026-10-15）**：用户决定**先做完第 7 关**，再把项目里「能换成注解流的手写流」逐步换掉。**`@RateLimit` 属于这一批，不是漏做。**

**为什么先不做**：第 7 关要试 5 种高级数据结构（String+JSON / BitMap / HyperLogLog / Stream / 秒传），会写出更多"手写流"。**等模式铺开、看清哪些真的重复了再抽象，比现在提前抽更准** —— 提前抽容易抽错。

**要改造的清单（届时核对）**

| 现在（手写流） | 目标（注解流） | 值不值得 |
|---|---|---|
| `UserService.checkIpLimit` + `login` 里的滑动窗口 | `@RateLimit` + `@Aspect` | ⭐⭐⭐ **最该抽** —— login / register 各抄了一遍 |
| `KnowledgeCategoryService.listAll` 手写 Cache Aside | 看是否换 `@Cacheable` | ⭐ 它需要 `try-catch` 降级 + 复杂 key，换注解可能反而失控 |
| `DiaryService` 的 `SET NX` 防重 | 可选 `@Idempotent` | ⭐ 只有 1 个调用点，暂时不值得 |

**⚠️ 抽象前先问三个问题**（避免为抽象而抽象）：
1. **重复了几次？** 只出现 1 次的逻辑，抽出来只是把代码搬到另一个文件
2. **逻辑稳定吗？** 还在改的逻辑，抽成注解后改起来更麻烦
3. **出问题好不好查？** 注解流 = 多一层代理，调试成本上升（回想第 1 关查 `@Cacheable` 不生效花了多久）

**注解流的 4 个失效条件**（跟 `@Cacheable` **完全同构**，可直接复用第 1 关经验）：
1. 类不是 Spring bean → 没有代理
2. `this.xxx()` 内部调用 → 不走代理
3. 方法不是 `public` → CGLIB 拦不住
4. 注解 `@Retention` 不是 `RUNTIME` → 反射读不到

**配套：反代 IP 的处理（部署前必修，本地不用动）**
- **最省事的后端兜底**：Spring Boot 内置 `server.tomcat.remoteip.remote-ip-header: X-Forwarded-For`（底层是 Tomcat 的 `RemoteIpValve`）→ **`getRemoteAddr()` 自动返回真实 IP，业务代码一行不用改**（另需 `internal-proxies` 声明可信代理，防伪造）
- 另一条路：**PROXY Protocol**（TCP 层传真实 IP，不依赖 HTTP header）
- 拿不到真实 IP 时的降级：**用户名维度限流**（已有，防撞库，不依赖 IP）+ 全局兜底限流 + Nginx 层 `limit_req`（Nginx 天然知道真实 IP，且不占后端资源）

### 11.7 📅 已登记待开发：用户日历模块（2026-10-16 用户提出）

**需求原话**：做「**仿 GitHub 贡献图**」的用户日历 —— **左右分栏，左栏上方恒定是用户信息简介**。

**开工前置条件（用户明确指定）**：
1. **第 7 关完成**
2. **「手写流 → 注解流」改造完成**（见 **11.6**）

**⚠️ 开工前必须先定的两件事**（不先定，数据结构必选错）：

| 待定项 | 说明 |
|---|---|
| **① 强度怎么定义？** | GitHub 的日历是**计数**（当天 commit/PR/issue 数）→ 色块分 5 档。**BitMap 一个 bit 只能表示 0/1，做不了分档** → 日历**不能用 BitMap**。要能存计数 → 用 **Hash**（`HINCRBY calendar:<userId>:<yyyy-MM> <day> 1` + `HGETALL` 一次拿全月，前端按值分档）或 **ZSet**（member = 日期，score = 次数） |
| **② 强度取自什么数据？** | ⚠️ `emotion_diary` 表**一天最多一条**（`(userId, diaryDate)`）→ "当天日记数"恒为 **0 或 1** → **又变回布尔，日历就没有强度可分**。所以强度要么取 **`moodScore`**（语义变成"当天心情好坏"）、要么取**字数**、要么干脆承认"日历只表示有没有记录"（那 BitMap 反而对） |

**内存账（BitMap 的价值边界，面试能讲）**：

| 结构 | 一个用户一个月 | 能存什么 |
|---|---|---|
| **BitMap** | 31 bit = **4 字节** | 只能 0/1 |
| **Hash** | 31 个 field ≈ **几百字节 ~ 1KB** | **计数**，可做强度分档 |

→ **BitMap 省约 100 倍内存，代价是只能存布尔。** 所以：**签到用 BitMap，日历用 Hash** —— 两个不同需求，别用一个结构硬套。

**已发现的现成素材（可直接复用）**：
- `service/AnalyticsService.java:38` 已有 `RECENT_DAYS = 30` 窗口概念；`:86-114` 已按自然日分组并**补齐没有记录的日期** → 日历"按天取数 + 补空"的逻辑可直接抄
- `:102-108` 已在内存里算"每天有哪些活跃用户"（`Set<Long> dayActiveUserIds`）→ **这就是第 7 关场景 2（HyperLogLog UV）的真实落点**
- 左栏"用户信息简介" → `UserService.getUserById:142` 返回的 `UserDetailResponseDTO` 就是现成的

**⚠️ 做日历时会撞上的两个性能问题（顺带发现）**：

1. **`UserService.getUserById:142` 没有缓存** —— 而 `JwtAuthticationFilter:78` **每个需要认证的请求都调它一次** → **每个请求打一次 `user` 表**。这是「手写流 → 注解流」改造的**头号候选**（加 `@Cacheable`，注意用户信息变更时要 evict）。顺带说明：`JwtAuthticationFilter:19` 已经 import 了 `Cacheable` 却没用 —— 说明当初就打算加，没加完。
2. **`AnalyticsService.getOverview:54-57` 用 `selectList(null)` 把 user / diary / session / message 四张表全量拉进内存再聚合** —— 数据量一上来必炸。这正是"**该把聚合搬到 Redis**"的典型证据（HLL 算 UV、Hash 存日聚合）。

**认证链路现状（回答"是不是先查库拿 userId"）**：
`JwtAuthticationFilter:57` 取 token → `:62` `validateToken` **解出 userId（不查库，JWT 自包含）** → `:78` **`getUserById` 查库**（目的是校验 `UserStatus.NORMAL`，`:86`）→ `:107-113` 塞进 `SecurityContext` → `@AuthenticationPrincipal` 取值。
→ **userId 来自 token，但每个认证请求确实查了一次库**（因为要拿"最新"用户状态）。

**概念归档**

- **ZSet 底层** = 跳表（按 score 排序，`ZREVRANGE` O(log N)）+ 哈希表（member→score，`ZSCORE` O(1)）→ 本质是"**按 value 排序的 Map**"
- Java `switch` 的**箭头语法 `case X ->` 不穿透**，不需要 `break`（只有传统 `case X:` 才穿透）
- `selectBatchIds` 查不到返回**空 List**（非 null），且**返回顺序不保证**
- **`loadDetail` × N vs `selectBatchIds`**：前者缓存命中 0 次查询、未命中因 **N+1** 放大到 3N 次；后者恒定 3 次 → **稳定性 > 峰值性能**
- `CacheManager` 是**注解流的后端接线员**（`@Cacheable → CacheInterceptor → CacheManager → Cache → RedisTemplate → Redis`），`StringRedisTemplate` 是手写流工具，两条流落在同一个 Redis

## 十二、第 6 关：分布式锁（2026-10-16）✅

> 状态：**✅ 已完成**。锚点 `common/FileCleanupTask.java`，多实例验收通过。
> ⚠️ 遗留一项：`LOCK_TTL` 与"任务最坏耗时"未对齐（见 **12.4**）。

### 12.1 实际落地

| 项 | 内容 |
|---|---|
| **加锁** | `redis.opsForValue().setIfAbsent(LOCK_KEY, token, LOCK_TTL)` —— 一条命令**原子**完成「不存在则设置 + 设过期」，对应 `SET key value NX EX 30` |
| **锁值** | 每次执行生成 `UUID.randomUUID().toString()`，作为"这一次执行的身份" |
| **释放** | `DefaultRedisScript<Long>` 静态常量 + `redis.execute(UNLOCK_SCRIPT, List.of(LOCK_KEY), token)` —— Lua 里**先比对 value 再 `del`** |
| **抢锁失败** | `log.info` + `return`（**不抛异常** —— 定时任务抢不到锁是正常现象） |
| **Redis 异常** | `catch` → `log.warn(..., e)` + `return` → **fail-close**（宁可这轮不清理，也不能多实例重复删） |
| **结构** | `try { 业务 } finally { try { Lua 释放 } catch { warn } }` —— finally 里**再包一层**，防释放失败**掩盖业务异常** |

### 12.2 定下的设计（别再动摇）

| 决策 | 内容 |
|---|---|
| **抢锁三要素** | **`NX`** 管"谁能拿到"（互斥性）、**UUID value** 管"删对"（安全性）、**`EX`** 管"崩了怎么办"（防死锁）—— **缺一不可**。只写 `SET` 不带 `NX` = 后来者覆盖前者 = 人人都是主人，锁完全失效 |
| **必须用 `StringRedisTemplate`** | 用 `RedisTemplate<String,Object>`（JSON 序列化器）会把 UUID 存成**带引号**的 `"xxx"`，而 Lua 的 `ARGV[1]` 不带引号 → **比对永不相等 → 锁永远删不掉**，且**不报错**（只是安静地"释放失败"）。与第 2 关 `article:read:delta` 的引号坑**同源** |
| **释放必须原子** | `GET` 判断通过到 `DEL` 之间锁可能刚好过期被他人拿到 → 必须 Lua 一把梭 |
| **`KEYS`/`ARGV` 下标从 1 开始** | Lua 新手必错点（不是 0） |
| **抢锁失败不抛异常** | 定时任务场景，"别人在跑"是**预期内的正常分支** |
| **Redis 挂了 → fail-close** | **与第 5 关登录限流的 fail-open 相反** —— 同一个问题、不同业务、答案相反 |
| **cron 是 6 字段** | Spring `@Scheduled` 用 **秒 分 时 日 月 周**（6 个），**不是 Quartz 的 7 字段**（多一个"年"）。写错会让**应用启动直接失败** |

### 12.3 踩过的坑

| # | 坑 | 后果 | 教训 |
|---|---|---|---|
| 1 | 🔴 **cron 写成了 7 字段** `0 /20 * * * * ?`（少一个 `*`） | `IllegalArgumentException: Cron expression must consist of 6 fields (found 7 ...)` → **应用启动失败** | **实测确认**（拿 `.m2` 里的 `spring-context-6.2.19.jar` 跑 `CronExpression.parse`）：Spring 是 **6 字段**，正确值 **`0 */20 * * * ?`**。**别再凭"印象里的标准写法"写 cron** |
| 2 | 释放锁用 `DEL` | 会删掉**别人的**锁（TTL 到期后 A 还在跑、B 已接手，A 跑完把 B 的锁删了 → C 又能进来） | 必须"value 存 UUID + Lua 比对后删" |
| 3 | `SETNX` + `EXPIRE` 两条命令 | 中间进程崩溃 → 锁**永不过期** → 死锁 | 用 `SET NX EX` 一条命令原子完成 |
| 4 | 验收脚手架残留 | 临时的 `cron="0/10 ..."` 和 `Thread.sleep(5000)` 容易忘删 | 验收清单里**必须包含"还原临时改动"这一步** |

> 坑 1 值得单独记：这是本项目**第三次**"凭印象写字符串"翻车（第 3 关 key 读写不一致 → 第 4 关少冒号 → 第 6 关 cron 字段数）。**凡是"格式约定"，都要用工具验证，不要凭记忆。**

### 12.4 ⚠️ 遗留（接手先看）

| 优先级 | 事项 |
|---|---|
| 🟡 | **`LOCK_TTL = 30 秒` 与"任务最坏耗时"未对齐** —— 每个 `ossUtil.delete` 是一次网络 IO，孤儿文件上千时任务可能跑超过 30 秒 → 锁提前过期 → **互斥失效**（Lua 比对能防**误删**，但防不住两个实例**同时跑**）。三条出路：**限制单批处理量**（推荐）/ 调大 TTL / 看门狗续期。定了要把理由写进注释 |

### 12.5 验收方法（已实测通过）

**前提**：`sys_file_info` 里要有 `is_temp=1` 且 `create_time` 早于 10 分钟前的记录（否则任务在 `orphans.isEmpty()` 处提前返回，你会**误以为锁没生效**）。

| # | 方法 | 期望 |
|---|---|---|
| 1 | `redis-cli SET lock:fileCleanup someone-else NX EX 300` 手动占锁 → 等 cron | 日志出现"抢锁失败，跳过本次执行"，且**没有**"发现 N 个待清理文件" |
| 2 | 任务执行中 `redis-cli --raw GET lock:fileCleanup` | 一串 **UUID，不带引号**（带引号 = 用错模板） |
| 3 | 任务跑完后 `redis-cli EXISTS lock:fileCleanup` | `0` → Lua 释放成功 |
| 4 | **脱离 Java 单独跑 Lua**：`EVAL "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end" 1 lock:fileCleanup someone-else` | 返回 `0` 且锁还在；换成自己的值 → 返回 `1` 且锁消失。**不用掐时机就能验证"不删别人的锁"** |
| 5 | **两个实例**：IDEA 复制 Run Configuration，**Program arguments** 填 `--server.port=0`（随机端口，永不冲突） | 只有一个打印"发现 N 个待清理文件" |
| 6 | 停掉 Redis | 日志出现"抢锁异常，本轮跳过"，任务**不执行**（fail-close） |

> ⚠️ **IDEA 填参数的坑**：`--server.port=8081` 必须填在 **Program arguments**，**不是** "Active profiles"。填错会报 `Profile '--server.port=8081' must start and end with a letter or digit`（Spring 把它当成了环境配置名）。
> 记忆：**`--` 是给"应用"的（Program arguments），`-D` 是给"JVM"的（VM options）。**

### 12.6 概念归档

- **"抢"的本质**：Redis **单线程**把并发的 `SET NX` 串行化 → 只有第一个返回 `true`，后面全 `false`
- **单实例内不会并发**：Spring 用 `ReschedulingRunnable`，**上一次跑完才 `schedule()` 下一次** → 同一个 `@Scheduled` 方法在同一 JVM 内串行。**并发只来自多实例**
- **"必须互斥" vs "希望互斥"**：前者（扣款、生成订单号）锁失效 = **数据错**；后者（本任务，`deleteById` 与 OSS delete 都幂等）= 只是**重复劳动 + 白花 OSS 请求费**。分清这个，才知道锁值不值得上 Redlock
- **多实例防重复的两条路**：**互斥锁**（本关，任务整体不可拆）vs **任务分发**（`ArticleReadCountTask` 的 `pop` 认领，任务可拆成一条条 → 多实例并行反而更快）
- **Redlock**：主从**异步复制** → 主挂了锁可能丢 → N 个独立 master + 过半成功。争议：Kleppmann 批评它依赖时钟/GC 停顿，建议 **fencing token**；antirez 有公开反驳。**本场景单实例锁足够**，强一致场景用 ZooKeeper/etcd（CP）
- **`@Scheduled` 的 cron 是 6 字段**（秒 分 时 日 月 周），**不是 Quartz 的 7 字段**
- **`spring.task.scheduling.pool.size: 2`** 但项目有 3 个 `@Scheduled` 方法 → 任务会抢线程，触发可能不准时

---

## 十三、第 7 关场景 2：HyperLogLog UV（2026-10-17）✅

### 13.1 写到哪了

| 文件 | 状态 |
|---|---|
| `DiaryService.addEmotionDiary` 里 `PFADD uv:<diaryDate> <userId>` + `EXPIRE 40天` | ✅ 已修（key 只拼一次，`add`/`expire` 共用） |
| `ConsultationSessionsService.createSession` 里 `PFADD uv:<startedAt.toLocalDate()>` + `EXPIRE 40天` | ✅ 已补（2026-10-17，UV 写侧口径与回源对齐） |
| `AnalyticsService.getUVTrend()` `:133-182` | ✅ 已写（逐天读 → 缺失记账 → 查库 + 回存 → 组装） |
| `AnalyticsService.queryUvDetailFromDb()` `:184-200` | ✅ 已写（一次范围查 + `computeIfAbsent` 分组） |
| `AnalyticsService.writeBackToRedis()` `:202-216` | ✅ 已修（空集合写墓碑 `uv:empty:<date>`） |
| `KnowledgeController` 的 `GET /data-analytics/uv-trend` | ✅ 已挂（`/api/data-analytics/uv-trend`，`:168`） |
| **验收** | ✅ **降级链路已跑通**（停 Redis / key 缺失 → `code:200`，数字来自 DB）；✅ **墓碑已实测生效**（见下） |

**墓碑实测证据（2026-10-17）**：
```
redis-cli --scan --pattern "uv:*"     → 6 个 HLL（09-29/09-30/10-01/10-05/10-06/10-16）+ 24 个墓碑，30 天全覆盖
redis-cli TTL uv:empty:2026-10-17     → 3455898（≈40 天，TTL 生效）
redis-cli PFCOUNT uv:2026-10-16       → 2（与接口返回的 activeUsers: 2 一致）
```
> ⚠️ 上面那批数字是**回源**写进去的（`writeBackToRedis`），不是实时写入。写侧 key 拼错的问题已在 2026-10-17 修掉，之后的新数据才走实时写入。
> **验收要重做一次**：`redis-cli DEL uv:<今天>` → 调一次接口 → 数字应来自回源；再写一篇日记 → `PFCOUNT` 应 +1。

---

### 13.2 ⭐ 心智模型：这玩意儿就是 **Cache Aside（旁路缓存）**

**一句话**：**读缓存 → 没有就查库 → 查完顺手把缓存补上。**

拆成三条路径，就这三条，没有第四条：

| 路径 | 触发条件 | 做什么 | 能不能补缓存 |
|---|---|---|---|
| **① 正常** | Redis 有值 | 直接用，不碰 DB | —— |
| **② 个别天缺** | `EXISTS = 0` | 只查那几天 | ✅ **能补**（Redis 是好的） |
| **③ 整段挂** | 抛异常 | 剩余天数全查库 | ❌ **补不了**（Redis 不可用） |

代码里的三个变量正好对应这三条：
- `uvByDay` → 结果容器（30 天，最后统一组装）
- `needDb` → "**需要查库的天**"的记账本（②③ 都往这里记）
- `redisDown` → 标记"是③不是②"，**因为③回存也没用，别白试 30 次**

> **为什么"回存失败"不算 bug**：回存只是**优化**，不是必须完成的动作。它有一个**免费的重试机制** —— 下次请求会再补一次。所以 `writeBackToRedis` 里 catch 住只 warn（fail-open）。
> **对比**：`FileCompensator` 删 OSS 失败必须异步重试 3 次，因为**没有免费重试**（删完就没人再管了，文件会一直占存储费）。**判据：这个动作有没有"下一次"**。

---

### 13.3 为什么要 `EXISTS` —— `0` 的二义性

这是整段逻辑最绕的地方，但只绕在一个点上：

> **`PFCOUNT` 返回 `0` 的时候，你无法区分两件事。**

| `PFCOUNT` | `EXISTS` | 含义 | 该不该查库 |
|---|---|---|---|
| `> 0` | —— | 有数据 | ❌ 不用 |
| `0` | **`1`** | 那天**真没人**访问 | ❌ **不用**（`0` 就是正确答案） |
| `0` | **`0`** | key **丢了**（被清 / 误删） | ✅ **要查** |
| 抛异常 | —— | Redis 挂了 | ✅ 要查（整段） |

**所以 `EXISTS` 的唯一作用就是"区分器"**：把"真没人"和"数据丢了"分开。

> ⚠️ **陷阱**：如果你图省事，把判据写成"`count == 0` 就查库"，那么**所有没人访问的天都会被查库** —— 而你项目里 30 天有 **23 天是空的**，等于**每次请求都查库，缓存彻底失效**。
> ⚠️ **另一个陷阱**：`PFCOUNT` 在 key 不存在时返回 **`0` 而不是 `null`**。**回源的判据永远是"抛异常"或"`EXISTS = 0`"，不是"值是 0"**。

---

### 13.4 🔴 "空的天"最阴 —— 两道门都堵死了

**问题**：某天 DB 里也没数据（比如 09-22）→ 查库拿到**空集合** → 我们想"建一个**基数 0 的空 HLL** 表示这天是 0" → **建不出来** → 这个 key 永远不存在 → 下次又被判成"②缺失" → **每次都白查库**。

**为什么建不出来？两道校验，一道都绕不过**（已实测 + 读源码确认）：

| 尝试 | 卡在哪 | 证据 |
|---|---|---|
| `opsForHyperLogLog().add(key, new String[0])` | **Spring 连接层** | `LettuceHyperLogLogCommands:39` `Assert.notEmpty(values, "PFADD requires at least one non 'null' value.")` |
| `RedisCallback` 里 `conn.pfAdd(key.getBytes(), new byte[0][])` | **同上，是同一个方法** | 绕不过 |
| `opsForHyperLogLog().union(key)`（无源 `PFMERGE`） | **Lettuce 层** | `RedisCommandBuilder:2296` `LettuceAssert.notEmpty(sourcekeys, "Sourcekeys must not be empty")` |

**但 Redis 原生是支持的**（`redis-cli` 实测）：
```
PFADD   wb_probe_hll   → 1    ; PFCOUNT → 0 ; EXISTS → 1   ✅ key 建出来了
PFMERGE wb_probe2      → OK   ; PFCOUNT → 0 ; EXISTS → 1   ✅ key 建出来了
```

> **教训**：Spring Data Redis 是 **"Spring 层 + Lettuce 层"两道门**。判断"某个 API 能不能这么用"必须**两层都查**，只查一层会得出错误结论。
> **查证方法**：`.m2` 里有 `spring-data-redis-3.5.12-sources.jar` 和 `lettuce-core-6.6.0.RELEASE-sources.jar`，解压后 `grep -rn "关键字"` 就能看到真实的 `Assert` 位置。
> **最省事的判断顺序**：① `redis-cli` 测 Redis 原生行为 → ② 查 Spring 源码 → ③ 查 Lettuce 源码。

---

### 13.5 ✅ 改法：墓碑 key（"缓存空对象"）

既然造不出"空 HLL"，就**换一种方式记住"这天查过了，确实是 0"**：

```java
private static final String UV_EMPTY_KEY = "uv:empty:";   // 墓碑：标记"这天查过库，结果是 0"

// ① writeBackToRedis —— 空集合时写墓碑，不写 HLL
private void writeBackToRedis(LocalDate day, Set<Long> userIds){
    try{
        if(userIds.isEmpty()){
            // 空集合不能 PFADD（Spring 拦）、也不能无源 PFMERGE（Lettuce 拦）
            // → 用 String 类型的墓碑 key 代替"空 HLL"
            redis.opsForValue().set(UV_EMPTY_KEY + day, "1", UV_TTL);
            return;
        }
        String key = UV_KEY + day;                 // ← 只拼一次，expire 复用同一个变量
        redis.opsForHyperLogLog().add(key,
                userIds.stream().map(String::valueOf).toArray(String[]::new));
        redis.expire(key, UV_TTL);
    }catch(Exception e){
        log.warn("UV回存失败，date={}", day, e);    // fail-open
    }
}

// ② 读取侧判据 —— "两个都没有"才算缺失
Long count = redis.opsForHyperLogLog().size(key);
if (count == null || count == 0L) {
    boolean hasData    = Boolean.TRUE.equals(redis.hasKey(key));
    boolean knownEmpty = Boolean.TRUE.equals(redis.hasKey(UV_EMPTY_KEY + day));
    if (!hasData && !knownEmpty) {                 // ← 既没数据、又没查过 → 真缺失
        needDb.add(day);
        continue;
    }
}
```

**为什么推荐这个而不是"干脆跳过空天"**：跳过的话空天的 key 永远不存在 → 每次请求都查库 → **缓存等于没做**（你 30 天里 23 天空）。

**为什么这个方案好**：它就是缓存系统的标准模式 —— **"缓存空对象"（cache null），防缓存穿透的经典手段之一**，面试可以直接讲；而且**不依赖任何实现细节**（不像 `union` 那样依赖"某个方法碰巧没加校验"）。

---

### 13.6 📌 待办（2026-10-17 17:50 更新）

> **场景 2 已于 2026-10-17 收尾。** 原来 8 条待办修完 8 条，剩下的是"优化项"不是"bug"。

**✅ 已修完（别再翻旧账）**

| # | 原问题 | 现状 |
|---|---|---|
| 1 | `DiaryService` 日期拼两遍 → key `uv:2026-10-172026-10-17` | ✅ 改 `String key=UV_KEY+diary.getDiaryDate()`，`add`/`expire` 共用 |
| 2 | `expire(UV_KEY, ...)` 少日期 → `TTL=-1` 永久堆积 | ✅ 改 `expire(key, ...)` |
| 3 | `if(redis.expire(...))` 拆箱 + 条件反了 | ✅ `!Boolean.TRUE.equals(redis.expire(key,UV_TTL))` |
| 4 | `hasKey` 无条件算 → 30 天 90 次往返 | ✅ 收回 `if(count==null\|\|count==0L)` 里面 |
| 5 | `UV_TTL` 写侧 30 天 / 读侧 40 天 | ✅ 统一 40 天 |
| 6 | `ConsultationSessionsService` 无 `PFADD` → UV 口径打架 | ✅ `insert` 后补 `PFADD uv:<startedAt.toLocalDate()>` + `EXPIRE 40天` |
| 7 | `GlobalExceptionHandler` 把所有唯一键冲突报成"用户名/邮箱已注册" | ✅ 按 `e.getMessage()` 表名分派 |
| 8 | 日记防重复语义混（15 秒 userId 锁） | ✅ 改内容指纹 `md5(userId\|diaryDate\|content)` + 60 秒；前置判重放抢锁前；失败主动释放锁 |

**🟡 还剩（优化项，不阻塞第 7 关收尾）**

| # | 事项 | 位置 |
|---|---|---|
| 1 | **key 拼接收敛** —— `uv:` 现在在 **3 个文件**里各拼一遍（`DiaryService` / `AnalyticsService` / `ConsultationSessionsService`），`expire` 又各拼一遍。这是翻车四次的根因。收敛到 `util/RedisKeys.uv(LocalDate)` + 共用 `UV_TTL` | 三处 |
| 2 | `UserActivityItem` 复用 → `getUVTrend` 只填 2 个字段，另 3 个恒 `null`。新建 `UvTrendItem {date, activeUsers}` | `AnalyticsService` + `KnowledgeController:168` |
| 3 | `DiaryService` 的 `//写一篇日记5s` 注释，实际是 60 秒 | `DiaryService` |
| 4 | `DiaryPageQueryDTO.setSize` 没上限 → `size=100000` 一次拉全表 | `DiaryPageQueryDTO` |
| 5 | IDOR：`/emotion-diary/admin/page` 的 `userId` 来自前端 query 且无校验 | `KnowledgeController:129` |

**📅 已登记、暂缓**
- 前端「回显我的日记」（用**页签**不用按钮）+ 后端 `GET /emotion-diary/mine` 强制覆盖 `userId` → 与用户日历模块同一批做

**📖 两条别忘的原理（原 13.6 #1 的内容，压缩保留）**

1. **日期后缀绝对不能删** —— 它的作用不是"防止天数不对"，而是**按天分桶的唯一手段**。HLL 是"一个 key = 一个集合"，删掉后缀 → 30 天全挤进 `uv:` 一个 HLL → `PFCOUNT` 出来的是**累计去重人数**（只增不减），曲线变成单调上升的线。**要改的只是"日期取谁"，不是"要不要日期"。**
2. **写侧和读侧必须用同一个"日期定义"** —— 读侧 `queryUvDetailFromDb` 按 `diary_date` / `startedAt.toLocalDate()` 归桶，写侧就必须也按它，不能用 `LocalDate.now()`。

**🔵 已验证 OK**
- `AnalyticsService` 的 `.lt(max.plusDays(1).atStartOfDay())` —— 半开区间正确（原先误写 `le`，会把第二天整点创建的会话多算进来）
- `writeBackToRedis` 的 `key` 只拼一次、`add` 与 `expire` 复用同一变量 —— 结构上消灭了"拼接不一致"
- `getUVTrend` 的 `catch` 只认异常、`EXISTS` 只认缺失 —— **两者不混用**，方向正确
- 墓碑实测：6 个 HLL + 24 个墓碑覆盖 30 天，`TTL uv:empty:<date>` ≈ 40 天

---

### 13.7 概念归档

- **`count == null` 几乎恒为假**：`opsForHyperLogLog().size()` 来自 `PFCOUNT` 的整数回复，**实际不会返回 null**（只有 pipeline 里才可能）。`count == null` 是防御性写法，真正生效的是 `count == 0L`
- **`catch (Exception e)` 的代价**：它会把**自己代码的 bug** 误判成**"Redis 挂了"**（本项目已踩过：`uvByDay` 未初始化 → NPE 在 try 里 → 被 catch 吞掉 → 日志打"Redis读取失败"，误导排查方向）。
  **对策：`try` 块里只放"会因为 Redis 而失败"的语句，自己的业务逻辑放外面**
- **Helpful NullPointerException（JDK 14+，JEP 358）**：`Cannot invoke "java.util.Map.put(Object, Object)" because "uvByDay" is null` 这种措辞是 JVM 自动补的，**不是反射报错**，只是用了 `类名.方法名` 的全限定写法
- **`computeIfAbsent(key, k -> new HashSet<>())`** = 没有就造一个放进去并返回它。**别用 `getOrDefault(key, new HashSet<>())`** —— 它返回的临时 Set **没有放回 map**，`add` 进去就**静默丢失**
- **HLL 能不能"从 DB 重建"**：**能**。`DEL key` + 重新 `PFADD` 全部元素即可（没有"设置基数"的操作，但可以把元素重新喂一遍）。前提是**元素在 DB 里有明细** —— 本项目 `uv:` 装的是 userId，`emotion_diary` + `consultation_session` 有明细 → **可精确重建**。"回存"就是重建
- **`PFADD` 返回 `1/0` 不能判断"元素是否新增"**，只表示"基数估计是否可能变了"
- **12KB 是 HLL 的【上限】不是恒定值**：有 `sparse`/`raw` 两种编码，`OBJECT ENCODING` 可看，阈值 `hll-sparse-max-bytes` 默认 3000
- ⭐ **缓存不能用来做"写路径的准入判断"**（2026-10-17 用户问"判重为什么要查库，我想缓存全覆盖"）
  - **判重 / 库存 / 余额 / 唯一性** 要的是**绝对真相**；缓存只能给"大概率对"
  - **缓存里没有 ≠ 没写过** —— 写日记时并没有同步维护这份缓存 → 一旦 miss 就**误判放行** → 还是撞 DB 唯一索引 → 又回到那个错乱提示
  - **读路径可以容忍 1% 的旧数据，写路径不能容忍 1% 的错误放行**
  - 一句话：**缓存是"加速读"，不是"替代真相"**
  - 这次 `selectCount` 也不贵：`UNIQUE(user_id, diary_date)` 走索引、等值查找 O(log n)，而且**写日记是全站最低频的写操作**（一天一篇，第二篇就被拦），不是 N+1
  - 真想省这一次查询，正确做法是**删掉前置判重、只留 `GlobalExceptionHandler` 按表名分派**（正常路径 0 次查询，重复路径才走一次异常）——**这是取舍，不是"加缓存"**
- **`UNIQUE(user_id, diary_date)` 是建表时就有的业务规则**，不是后来加的（`EmotionDiary:33` 注释写明）。用户"以前以为能写多篇"，只是因为**没撞过**（每天只写一篇）。现在的"一天一篇"提示不是新限制，是把已有的 DB 约束**翻译成人话**

---

### 13.8 📄 一页复盘（第 8 关面试包装直接抄这里）

**一句话**：把「每日独立访客数」从「全量拉表到内存去重」换成「Redis HyperLogLog 估算 + 缓存缺失时回源 DB」，**误差 0.81%，内存从几百 MB 降到 360KB**。

**数据流（四件事）**

| 环节 | 方法 | 干什么 |
|---|---|---|
| 写 | `DiaryService` 里 `PFADD uv:<date> <userId>` + `EXPIRE 40天` | 记一笔"这天谁来过" |
| 读 | `AnalyticsService.getUVTrend()` | 30 天逐天 `PFCOUNT`，缺失的记账 |
| 回源 | `queryUvDetailFromDb(List<LocalDate>)` | 一次范围查（2 条 SQL）+ `computeIfAbsent` 按天分组 |
| 回存 | `writeBackToRedis(day, userIds)` | 有人 → `PFADD`；没人 → **墓碑** `uv:empty:<day>` |

**碰到的 10 个坑（按发生顺序）**

| # | 坑 | 根因 | 怎么改 |
|---|---|---|---|
| 1 | `uvByDay` 是 `null` → NPE，日志却报"Redis 读取失败" | `catch(Exception)` 把自己的 bug 当成 Redis 故障 | 初始化 `new LinkedHashMap<>()`；**`put` 移出 `try`** |
| 2 | 咨询会话时间区间多算第二天整点 | 半开区间写成了闭区间 `le` | 改 `lt`（`[min 00:00, max+1 00:00)`） |
| 3 | 空集合 `PFADD` 报 `requires at least one non 'null' value` | **Spring** `LettuceHyperLogLogCommands:39` `Assert.notEmpty` | 换思路 |
| 4 | 改用无源 `PFMERGE`（`union(key)`）还是报错 | **Lettuce** `RedisCommandBuilder:2296` `LettuceAssert.notEmpty(sourcekeys)` | 确认"造不出空 HLL"，改用墓碑 |
| 5 | 空天的 key 永远建不出来 → 每次请求都查库 | 见 3、4，两道门都堵死 | **墓碑 key `uv:empty:<day>`**（缓存空对象） |
| 6 | `TTL uv:2026-10-17` = `-1`（永不过期） | `expire(UV_KEY, ...)` 少了日期 → 作用在不存在的 key 上 → 返回 `false` 不抛异常 | key 只拼一次，`add` 和 `expire` 共用同一个变量 |
| 7 | `if(redis.expire(...))` IDE 报拆箱 NPE | `expire` 返回 `Boolean`，`if` 要 `boolean` | `Boolean.FALSE.equals(...)`（**且原条件反了**） |
| 8 | 30 天打了 90 次 Redis 往返 | `hasKey` 写在 `if` 外面，有数据的天也问 `EXISTS` | 收回 `if(count==null\|\|count==0L)` 里面 |
| 9 | 写侧记进"今天"、回源归到"昨天" → 数字跳 | 写侧 `LocalDate.now()`，读侧按 `diary_date` | **写读同口径**，统一用 `diary.getDiaryDate()` |
| 10 | key 变成 `uv:2026-10-172026-10-17` | 把"换成"做成了"追加" | 三行、`String key=...` 只拼一次 |

**三个最值得讲的认知**

1. **`0` 是二义的** —— `PFCOUNT` 返回 `0` 可能是"真没人"也可能是"key 丢了"，必须再问一次 `EXISTS` 才能区分。判据：**回源看"抛异常"或"`EXISTS=0`"，永远不看"值是 0"**
2. **造不出"空 HLL"** —— Redis 原生支持 `PFADD` 空参 / 无源 `PFMERGE`，但 **Spring 和 Lettuce 各加了一道 `notEmpty`**。结论：用**墓碑 key** 记住"这天查过、结果是 0"（缓存空对象的经典套路，防缓存穿透）
3. **Spring Data Redis 是两层门** —— 判断"某 API 能不能这么用"必须**两层都查**：`spring-data-redis` 的 `*Commands` 是适配层，真正拼命令的是 `lettuce-core` 的 `RedisCommandBuilder`。查证顺序：`redis-cli` 测原生 → Spring 源码 → Lettuce 源码

**顺带修的（不属于场景 2，但同一批）**

- `GlobalExceptionHandler` 把**所有**唯一键冲突都翻译成"该用户名或邮箱已被注册" → 改成按 `e.getMessage()` 里的表名分派
- 日记防重复：改成**内容指纹** `md5(userId|diaryDate|content)` + 60 秒；前置判重放在抢锁**之前**；`insert` 失败主动释放锁
- 认知纠正：**缓存不能做"写路径的准入判断"**（见 13.7）

---

## 十四、第 7 关场景 4/5 + 日记模块收尾（2026-10-18）✅

### 14.1 本次落地清单

| 组 | 项 | 内容 | 状态 |
|---|---|---|---|
| A 还债 | A1 | 新建 `util/RedisKeys.java`（`uv` / `uvEmpty` / `UV_TTL`） | ✅ |
| | A2 | `UvTrendItem` 瘦身成 `date` / `activeUsers` 两字段 | ✅ |
| | A3 | 注释 5s→60s | ✅ |
| | A4 | `DiaryPageQueryDTO.setSize` 封顶 50 | ✅ |
| | **A5** | **IDOR + 越权删除**（见 14.2） | ✅ |
| B 遗留 | B1 | `FileCleanupTask.LOCK_TTL` 30 秒 → 15 分钟（见 14.3） | ✅ |
| 场景 4 | — | 文件秒传（见 14.4） | ✅ |
| 场景 5 | — | AI 情绪分析结果缓存（见 14.5） | ✅ |
| 日记模块 | — | `PUT /emotion-diary/{id}` 编辑接口（见 14.6） | ✅ |
| 前端 | — | 日记页加「写日记 / 我的日记」切换、列表、编辑回填 | ✅ |

### 14.2 🔴 A5：两个越权漏洞

**漏洞 1 — IDOR（水平越权读）**

`GET /emotion-diary/admin/page` 没加权限注解，任何登录用户都能拉全站日记，还能用 `?userId=` 精准查别人。

**漏洞 2 — 越权删除（比 IDOR 严重）**

```java
// 原来
diaryService.deleteEmotionDiary(diaryId);   // 只按 id 删，没有任何归属校验
```

任何登录用户传任意 id 就能删别人的日记，**不可恢复**。

**修法（三层）**

| 层 | 做法 |
|---|---|
| 入口 | 管理端两个接口加 `@PreAuthorize("hasRole('2')")` |
| Service | C 端统一 `eq(EmotionDiary::getUserId, userId)` |
| 接口拆分 | C 端 `/emotion-diary/page`、`/emotion-diary/{id}`；管理端 `/emotion-diary/admin/*` |

**`hasRole('2')` 的前缀规则**（面试点）：`hasRole` 会自动补 `ROLE_`，实际检查 `ROLE_2`；`hasAuthority('2')` 才是精确匹配。本项目 filter 塞的是 `"ROLE_"+roleType`，正好对上。若 filter 塞裸 `2`，`hasRole` 恒失败。

**删不到时统一说「不存在」** —— 区分「不存在」和「不是你的」等于给攻击者一个 id 枚举接口。

### 14.3 ⚠️ `isAdmin &&` 是开关，`if(!isAdmin)` 才是锁

为了消除 C 端 `user_id=10 AND user_id=10` 的重复条件，把 `:53` 改成：

```java
qw.eq(isAdmin && query.getUserId()!=null, EmotionDiary::getUserId, query.getUserId())
```

**这一步是对的，但它只是「给管理端开窗」**。C 端的安全完全依赖下面这段：

```java
if(!isAdmin){
    qw.eq(EmotionDiary::getUserId, userId);   // C 端唯一约束
}
```

改的时候这段被一起删掉了 → C 端 `isAdmin=false` 时上面短路、下面没有 → **WHERE 里没有任何 user_id 条件 → 查出全量 18 条**。

| | `isAdmin &&` | `if(!isAdmin)` | 结果 |
|---|---|---|---|
| 管理端 | 生效，可按 userId 筛选 | 跳过 | 传了就过滤，不传全量 |
| C 端（正常） | 短路 | **执行** | `user_id=10` |
| C 端（删了锁） | 短路 | **没有** | **全量** |

**教训**：一个 `boolean` 参数控制两条分支时，两条分支必须**成对存在**。开关只决定「要不要用前端的值」，锁才决定「你能看到谁」。

### 14.4 场景 4：文件秒传

**设计（五步）**

| 步骤 | 动作 | 关键点 |
|---|---|---|
| 1 | 算内容 md5 | `DigestUtils.md5DigestAsHex(InputStream)` **流式读**，不用 `getBytes()`（50MB 视频会整个进堆） |
| 2 | 查 `file:md5:<md5>` | 命中就拿到已有 `objectKey` |
| 3 | 没命中才 `ossUtil.upload` | 命中直接跳过 OSS |
| 4 | **仍然写一条 `sys_file_info`** | 业务方要能按自己的 `businessId` 查到，秒传省的是上传不是入库 |
| 5 | 真传才回填缓存（TTL 7 天） | 读写全 try-catch，缓存挂了退化到正常上传 |

**🔴 最容易写错的一步：秒传写库失败时禁止补偿删除**

```java
} catch (Exception e) {
    if(hit){
        // 这个 objectKey 是别人上传时创建的，可能被 N 条记录引用
        // 删了会把别人的记录全部搞挂
        log.warn("秒传写库失败，objectKey={}",objectKey,e);
    }else{
        fileCompensator.compensateDelete(objectKey);   // 只有自己刚传的才能删
    }
}
```

**三个已知坑（面试会追问）**

1. **md5 碰撞** —— 理论存在，实际极低；在意就换 SHA-256，或 md5 + size 双条件
2. **秒传跨用户越权** —— A 传过的文件，B 秒传能拿到同一个 `objectKey`。本项目 OSS 是公开直链，可接受；私密文件必须校验权限，不能只认 md5
3. **删除引用计数** —— 两条记录指向同一个 objectKey，删一条连带删 OSS，另一条悬空。本项目永久文件不自动删，风险低；真要做就得维护引用计数

**概念澄清：句柄 ≠ 引用计数**

- **句柄**解决「怎么访问」：一个标识符，程序拿着它去内核查表（`HANDLE` / fd / `FileDescriptor`），一次打开一个句柄
- **引用计数**解决「什么时候能销毁」：多少使用者持有，归零才释放

`objectKey` 不是句柄，是资源的逻辑地址；两条记录指向它叫**共享引用**。

### 14.5 场景 5：AI 情绪分析结果缓存

**key 的选择是分水岭**

| 方案 | 效果 |
|---|---|
| `ai:emotion:<sessionId>` | ❌ 永远命中第一次的结果，情绪再也不更新 |
| `ai:emotion:analysis:<md5(conversation)>` | ✅ 内容变了才重算 |

**命中率低是设计意图，不是缺陷**

正常聊天每轮 `conversation` 都在变 → md5 必变 → 永不命中。它挡的是**异常场景**：

- 前端断线重连，同一条 AI 回复触发两次 `doOnComplete`
- 网络抖动导致请求重试
- 多实例部署时同一会话被两个节点处理

**两个必判的空**

1. `redis.get(key)` 返回 null → **先判 null 再 `JSONUtil.toBean`**，别把 null 丢进去（Hutool 碰巧返回 null，换库就炸）
2. `.entity(Class)` 在模型返回非 JSON / 空串时**返回 null，不抛异常** → 必须判空，否则 `result.getPrimaryEmotion()` NPE 被外层 catch 吞掉，日志里什么都看不到

**为什么这里的 md5 不用判空，FileService 的就要**

| | 赋值方式 | 可能为 null |
|---|---|---|
| `FileService` | `md5=null` + `try{ md5=... }catch{}` | ✅ 可能（`getInputStream` 抛 `IOException`） |
| `EmotionAnalysisService` | `md5=直接调用()` | ❌ 不可能（`getBytes(UTF_8)` 不抛受检异常） |

**判断条件和赋值方式必须匹配**。`!=null` 描述的是「有没有失败路径」，不是「这个变量叫 md5」。

### 14.6 编辑接口：`PUT /emotion-diary/{id}`

```java
LambdaUpdateWrapper<EmotionDiary> uw=new LambdaUpdateWrapper<EmotionDiary>()
        .eq(EmotionDiary::getId,diaryId)
        .eq(EmotionDiary::getUserId,userId)          // ← 这一行就是鉴权
        .set(EmotionDiary::getMoodScore,dto.getMoodScore())
        ...
        .set(EmotionDiary::getUpdatedAt,LocalDateTime.now());

if(emotionDiaryMapper.update(null,uw)==0){
    throw new BusinessException("要修改的数据不存在");
}
```

**三个关键点**

1. **`update(null, wrapper)` 的 null** —— 传 entity 的话，它的非 null 字段也会进 `SET`，和手写的 `set` 打架；传 null 表示「完全按 wrapper 更新」
2. **`diaryDate` 绝对不能 set** —— `UNIQUE(user_id, diary_date)`，改日期撞库后 `GlobalExceptionHandler` 会报「今天已经写过日记了」，用户完全看不懂
3. **DTO 里绝不能加 `userId`** —— 身份只能来自 `@AuthenticationPrincipal`（JWT），`@RequestBody` 是前端可控的。加了就等于让攻击者自己填身份证号

**两个 Java 坑**

- `Long != Long` 比的是**引用**，只有 -128~127 走缓存，超出必错 → 包装类型一律 `equals`
- `new LambdaUpdateWrapper<>()` 在链式调用里**必然推断失败**：泛型自引用 + 链式 + 菱形，三者凑齐时 Java 一次性推断不做迭代，退化到 `Object`，然后 `set` 签名变成 `SFunction<Object,?>`，`EmotionDiary::getMoodScore` 类型不匹配 → 编译器尝试把它当静态方法引用 → 报「无法从 static 上下文引用非 static 方法」。**这个报错是假线索，根因是类型推歪了。**

### 14.7 验收记录（2026-10-18 实测，双 token 真调）

| # | 用例 | 结果 |
|---|---|---|
| 1 | C 端 `GET /emotion-diary/admin/page` | `A0300 没有权限执行该操作` ✅ |
| 2 | C 端 `DELETE /emotion-diary/admin/999999` | `A0300` ✅ |
| 3 | C 端 `GET /emotion-diary/page` | `total=4`，全 `userId=10` ✅ |
| 4 | C 端 `?userId=11` 伪造 | 仍 `total=4` 全 `userId=10` ✅ |
| 5 | C 端 `DELETE /emotion-diary/31`（别人的） | 「要删除的数据不存在」，复查 id=31 仍在 ✅ |
| 6 | 管理员 `?userId=11` | `total=1`，只有 id=31 ✅ |
| 7 | 秒传：同文件传两次 | `filePath` 完全一致；`file:md5:*` TTL 604787 ✅ |
| 8 | AI 缓存写入 | `ai:emotion:analysis:<md5>` 值完整 JSON，TTL 3584（1 小时倒计时）✅ |

### 14.8 ⚠️ 遗留（明天总决战）

| 优先级 | 项 | 说明 |
|---|---|---|
| 🔴 | **场景 3 Stream 任务队列** | 第 7 关最后一关，从零搭生产者/消费者/PEL/XACK，预计 4 小时 |
| 🟡 | 编辑接口限流 | 照抄 `addEmotionDiary` 的计数器模板，key 用 `diary:user:edit:<userId>`，阈值 20 次/分钟 |
| 🟡 | 新增日期范围 | 现在 `@PastOrPresent` 允许写到 1970 年，建议加「只能补写最近 N 天」 |
| 🟡 | 第 8 关面试包装 | 直接抄 13.8 + 14.2~14.6，整理成「问题—方案—踩坑—收益」四段式 |
| ⬜ | 前端响应式细节 | `.emotion-name` 的 `padding: 0 75px` 让每行只放 4 个卡片，可改 `0 12px` + `min-width` |
| ⬜ | 手写流 → 注解流改造 | 见 11.6，第 7 关之后做 |

**安全防线现状**（防刷用）

| 层 | 措施 | 状态 |
|---|---|---|
| 身份 | 必须登录（JWT） | ✅ |
| 归属 | `eq(userId)` | ✅ |
| 一天一篇 | `UNIQUE(user_id, diary_date)` | ✅ |
| 新增频率 | `diary:user:<userId>` 5 次/分钟 | ✅ |
| 重复内容 | `diary:dup:<md5>` 60 秒 | ✅ |
| 编辑频率 | 无 | ❌ 待补 |
| 可写日期范围 | 只挡未来 | ⚠️ 待收窄 |

> IP 层限流暂不做：所有写操作都要登录，token 已限定身份；真有几百账号同时刷属于攻击，该在网关层解决。

---

*本文档最后更新：2026-10-18。换电脑后：装 Redis（下 Release 不是 Source）→ 拉 `AI-mentalHealthSystem` → 读本文档第 0 节继续。*
