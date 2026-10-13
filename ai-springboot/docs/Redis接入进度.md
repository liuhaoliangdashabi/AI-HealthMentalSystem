# Redis 接入进度

> **这是本项目的唯一进度文档。** 想知道「Redis 接得怎么样了、下一步干什么」，读这一篇就够了，不用翻别的文件，也不用重新扫代码。
>
> 更新日期：**2026-10-13**
>
> `docs/` 下另外两篇（`DTO怎么写.md`、`事务练习-删除知识分类.md`）是**知识笔记**，不含进度信息。

---

## 0. AI 先读这段（当前进度快照）

### 0.1 一句话状态

**第 0 关 ✅、第 1 关（场景A 分类树 + 场景B 文章详情）✅、第 2 关（浏览量 `INCR` + 定时回写）✅、第 3 关（JWT 登出黑名单）✅ —— 全部完成并实测验收。**

**第 1 关欠的「兜底账」也已还清 ✅**（4 处代码 + 1 处配置，见 **9.4**）：手写流 4 处 try-catch、注解流 `CacheErrorHandler`、`timeout` 3000→500ms，全部落地。顺带把 **8.6-#7 的 `jti`** 也补了。

**下一步：清 8.6 剩下的几笔小账**（跨模块失效点 / 裸拼 SQL / 增量 key 无 TTL / 无用 import），然后开 **第 4 关：热门文章排行榜（ZSet）**。

### 0.2 进度表

| 关卡 | 主题 | 状态 |
|---|---|---|
| **第 0 关** | 地基：依赖 + 配置 + 序列化 | ✅ **完成**（2026-10-08） |
| **第 1 关** | 场景A：分类树缓存（**手写流** Cache Aside） | ✅ **完成**（2026-10-10） |
| **第 1 关** | 场景B：文章详情缓存（**注解流** `@Cacheable`） | ✅ **完成**（2026-10-13） |
| **第 2 关** | 浏览量 `INCR` + 定时回写 | ✅ **完成**（2026-10-11） |
| **第 3 关** | JWT 登出黑名单 ⭐ 最出彩 | ✅ **完成**（2026-10-13，收官记录见**第九节**） |
| **兜底账** | 第 1 关欠的读路径降级 + `timeout` 调优 | ✅ **完成**（2026-10-13，清单见 **9.4**） |
| 第 4 关 | 热门文章排行榜（ZSet） | ⬜ 未开始 |
| 第 5 关 | 限流 + 防重复提交 | ⬜ 未开始 |
| 第 6 关 | 分布式锁 | ⬜ 未开始 |
| 第 7 关 | 高级数据结构 | ⬜ 未开始 |
| 第 8 关 | 面试包装 | ⬜ 未开始 |

> 第 1 关的「三大问题」已讨论完，**只有"穿透"落地了**（见 8.4）。

### 0.3 已经落地的文件（接手前先看这张表）

| 文件 | 状态 |
|---|---|
| `config/RedisConfig.java` | ✅ `RedisTemplate<String,Object>`（JSON + `JavaTimeModule`） |
| `config/CacheConfig.java` | ✅ `@EnableCaching` + `RedisCacheManager`（JSON 序列化 + TTL）+ **`implements CachingConfigurer` 自定义 `CacheErrorHandler`（注解流降级）** |
| `service/KnowledgeCategoryService.java` | ✅ 场景A：`listAll` 手写 Cache Aside（**get / set 各自 try-catch 降级**）+ `evictCategoryTreeCache`（`try-catch` 降级）+ **`afterCommit` 钩子** |
| `service/ArticleContentService.java` | ✅ 场景B：**拆出来的纯读 bean**，`loadDetail` 贴 `@Cacheable` |
| `service/KnowledgeArticleService.java` | ✅ 三个写点贴 `@CacheEvict`；`getArticleDetail` 只做「取内容 → 权限 → 计数」，**计数两处已 try-catch 降级（`delta` 初值 `0`，避免 NPE）** |
| `common/ArticleReadCountTask.java` | ✅ 第 2 关回写任务：`pop` 认领 + `getAndDelete` + 失败 `SADD` 放回 |
| `service/UserService.java` | ✅ 第 3 关：`logout(token)` 写黑名单（`StringRedisTemplate` + TTL=剩余有效期，**fail-closed**） |
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

### 0.4 下一步就做这个

**兜底账 ✅ 已还清**（2026-10-13 对着代码核对，非凭记忆）：

1. ✅ 手写流 4 处 try-catch 降级 —— `KnowledgeCategoryService.listAll` 的 get / set、`KnowledgeArticleService.getArticleDetail` 的 `increment` / `SADD`（见 **9.4-A**）
2. ✅ 注解流 `CacheErrorHandler` —— `CacheConfig` 已 `implements CachingConfigurer`，4 个回调按 warn / error 分级（见 **9.4-B/C**）
3. ✅ `spring.data.redis.timeout` 已从 `3000ms` 调到 `500ms`（见 **9.7 治法①**）
4. ✅ 顺带补了 `JwtTokenUtil` 的 **`jti`**（8.6-#7）

> ⭐ **唯一需要你亲手改的**：`KnowledgeArticleService.getArticleDetail` 里 `delta` 的初值。
> 原来写的是 `Integer delta = null;`，catch 里没赋 0 → Redis 一挂就 `int + null` → **NPE 500**（正是 9.4 第 2 点警告的坑）。
> **已改成 `Integer delta = 0;`** ✅（1 行）

**下一步：清 8.6 剩下的几笔小账**（都不急，按序）：

1. ⬜ ⭐ **跨模块失效点**（8.6-#1）—— `deleteCategory` 改 `article.category_id` 却没失效文章缓存 → `categoryName` 脏读。面试好素材
2. ⬜ `ArticleReadCountTask:45` 裸字符串拼 SQL → 走 `#{}`（8.6-#4）
3. ⬜ `article:read:delta:<id>` 补 TTL 兜底（8.6-#5）
4. ⬜ 无用 import 清理 3 处（8.6-#8）

清完再开 **第 4 关：热门文章排行榜（ZSet）**（需求原文见第四节）。

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
| `controller/UserController.java:30` `login()` | 无限流 | 登录接口是撞库的天然目标 → `INCR`+`EXPIRE` 限流 |
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
| **方案** | `spring-boot-starter-data-redis`（**不写 version**，由 Boot parent 管）；`spring.data.redis.host/port/database/timeout`；`RedisConfig` 暴露 `RedisTemplate<String,Object>`（JSON 序列化） |
| **产出** | `config/RedisConfig.java`（实际实现见附录 A）；能同时注入 `StringRedisTemplate` 和 `RedisTemplate<String,Object>` |
| **验收** | ✅ 启动不报错；✅ 测试 `src/test/java/.../Test/Redis/RedisStartTest.java` 跑通；✅ `redis-cli --raw GET <key>` 看到干净 JSON |
| **顺带完成** | 用户把 **8 个 response DTO 全都补了 `@NoArgsConstructor + @AllArgsConstructor`**（原计划留到第 1 关做） |
| **知识课** | 第 2 课（序列化） |

> 💡 为什么 value 用 JSON 而不是 `StringRedisTemplate` 一把梭：本项目缓存的是 `ArticleResponseDTO`、分类树这类**对象**，手写 `ObjectMapper` 转换会在每个 Service 里重复一遍。但**计数、锁、黑名单这类单值仍用 `StringRedisTemplate`**，别把简单的搞复杂。

---

### 第 1 关：缓存实战（三大问题 + 一致性）⭐⭐⭐⭐⭐ 🔧 进行中

**这是面试第一高频，也是收益率最高的一关。选两个对比鲜明的场景做。**

#### 场景 A：分类树缓存 —— 最干净的缓存（🔧 进行中，见第五节）

| 项 | 内容 |
|---|---|
| **锚点** | `controller/KnowledgeController.java:38` `categoryTree()` → `service/KnowledgeCategoryService.java` |
| **写操作（要删缓存的）** | `KnowledgeController.java:46` 和 `:54` 两个 `deleteCategory` |
| **方案** | 手动 Cache Aside（没用 `@Cacheable` 注解，直接 `redisTemplate` 读写，便于逐步理解） |
| **为什么它最简单** | 全量一个 key、变更极少、数据量小 → 不会遇到分页 key 爆炸 |

#### 场景 B：文章详情缓存 —— 一致性训练场（⬜ 未开始）

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

### 第 4 关：热门文章排行榜（ZSet）

| 项 | 内容 |
|---|---|
| **锚点** | `entity/KnowledgeArticle.java:62` `readCount`、`controller/KnowledgeController.java:64` `articlePage()` |
| **方案** | ZSet `article:hot`，`ZINCRBY article:hot 1 <articleId>`；取榜 `ZREVRANGE article:hot 0 9 WITHSCORES` |
| **和 DB 的关系** | DB 是**权威数据**，ZSet 只是**加速视图**。可以定时用 DB 的 `read_count` 全量重建（简单、不会漂），也可以实时累加（快、会漂）——**面试要能说出这个取舍** |
| **加分点** | ① 取 Top N 用 `ZREVRANGE` 是 O(log N + M)，别 `ZRANGE` 全量再截断 ② 大榜单翻页仍有性能问题，真实场景只缓存前 N 页 ③ 底层是跳表 + 哈希，能说出跳表查询复杂度 |
| **验收** | `redis-cli --raw ZREVRANGE article:hot 0 9 WITHSCORES` 看到的排名和分数符合预期 |

**知识课**：第 3 课（ZSet）+ 第 6 课

---

### 第 5 关：限流 + 防重复提交

**场景 A：登录接口限流**

| 项 | 内容 |
|---|---|
| **锚点** | `controller/UserController.java:30` `login()` |
| **方案** | 固定窗口：`INCR login:try:<username>:<minute>` + `EXPIRE 60`；超阈值拒绝 |
| **四算法取舍** | 固定窗口（最简单，有**临界问题**：59s 和 61s 各打满一轮 = 2 倍流量）/ 滑动窗口（ZSet 存时间戳，精确但费内存）/ 令牌桶（允许突发，最常用）/ 漏桶（恒定速率） |
| **产出** | `@RateLimit` 注解 + 拦截器（可复用） |

**场景 B：情绪日记防重复提交**

| 项 | 内容 |
|---|---|
| **锚点** | `controller/UserController.java:57` `addEmotionDiary()` |
| **方案** | `SET NX` 幂等键（`userId + 内容摘要`），TTL 几秒；抢到才放行 |
| **加分点** | 幂等 vs 防重的区别；为什么锁的粒度选「几秒」而不是「一分钟」 |

**知识课**：第 5 课（⭐⭐⭐⭐）

---

### 第 6 关：分布式锁（用在真实定时任务上）

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
| **文件秒传** | `SET NX` | `controller/KnowledgeController.java:139` `uploadFile()` + `entity/SysFileInfo.java` | 文件 MD5 做 key，已存在就直接返回 URL |

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

**修法**：`RedisConfig` 里 `json.configure(mapper -> {...})`，见附录 A。

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
3. **`RedisConfig` 忘了挂 `JavaTimeModule`** —— 带 `LocalDateTime` 的对象**写**的时候就抛，见 5.1 坑 B
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
| `controller/UserController.java` | 第 3 关（logout :51）、第 5 关（login :30、日记防重 :57） |
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

1. ⬜ ⭐ **文章详情缓存的跨模块失效点** —— `KnowledgeCategoryService.deleteCategory` 会改 `article.category_id`，
   但那边**没有 `@CacheEvict`**（`evictAfterCommit()` 只删 `cache:category:tree`）→ 那些文章缓存里的 `categoryName` **一直是旧的**。
   （这是"缓存失效点散落在各个模块"的最好例子，面试能讲）**← 下一步第一件事**
2. ✅ **防穿透已收尾** —— 见 8.4（`loadDetail:36` 已 `return null`）
3. ✅ 已修：`ArticleContentService` 那句错注释（`//这里判断用户状态`）已经不在了
4. ⬜ `ArticleReadCountTask:45` 的 `read_count = IFNULL(read_count,0) + delta` 仍是**裸字符串拼接**（来源是 Redis 的整数，
   眼下安全，但正式项目该走 `#{}` 参数绑定）
5. ⬜ `article:read:delta:<id>` **没有 TTL 兜底**（有回写补偿了，但进程直接挂掉时没人管）
6. ✅ **Redis 读路径兜底已还清**（第 1 关欠的账）—— 4 处代码 + 1 处配置全部落地，清单见 **9.4**
7. ✅ **`jti` 已补** —— `JwtTokenUtil:52` 已有 `.withJWTId(UUID.randomUUID().toString())`，同秒登录不再签出相同 token
8. ⬜ 无用 import 清理：`UserService:3` logback `TimeUtil`、`:17` `CachePut`、`JwtAuthticationFilter:19` `Cacheable`；`UserService:36` 的 `redis` 字段加 `final`
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

**最可能的原因：序列化器污染。** 若有任何地方用 `RedisTemplate<String,Object>`（`RedisConfig` 里那个 JSON 序列化器）往同一个 key 写过 String，Jackson 会**带引号**存 → Redis 里是 `"1"` → `StringRedisTemplate` 读回来是带引号的 `"1"` → `Integer.valueOf("\"1\"")` 炸。**又是 8.2 坑 3 那一族。**

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

*本文档最后更新：2026-10-13。换电脑后：装 Redis（下 Release 不是 Source）→ 拉 `AI-mentalHealthSystem` → 读本文档第 0 节继续。*
