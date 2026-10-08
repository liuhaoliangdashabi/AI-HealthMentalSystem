# Redis 接入进度与路线图

> 面向 ai-springboot 项目。这份文件既是**进度看板**，也是**交接文档** —— 换电脑、换 AI、隔一个月回来，读这一篇就能接着干。
>
> 主线：**按项目的真实功能点排序，每个功能点挂上它需要的 Redis 知识点**。不炫技，只做面试讲得出口、线上站得住的东西。
>
> 整理日期：2026-10-08

---

## ⚠️ 交接给下一个 AI（先读这段）

1. **`ai-springboot/` 是只读的。** 代码一个字都不许改，除非用户明确说「这次你来写」。约定是**我搭架子、用户写主体**：可以给配置片段、给带 TODO 的骨架、给验收标准，业务逻辑留给用户填，填完我批改。本文件是唯一例外——用户 2026-10-08 明确要求建在 `docs/` 下。
2. **Boot 版本决定序列化器，抄错必崩**：本项目是 Spring Boot **3.5.15**（Jackson **2**）→ 只能用 `GenericJackson2JsonRedisSerializer`（**带 2**）。网上 95% 的中文教程是 Boot 3 之前写的，抄之前先看包名。
3. **别主动提议给这个项目换端口。** 用户 2026-10-08 明确表态「就要 8080」。
4. 用户**还没启动 Redis**（2026-10-08 时点），别催，也别在没启动时就说「跑一下验证」。
5. 学习笔记在另一个仓库（`RedisNotes`），那里才是讲原理的地方；这份文件只讲**怎么接进这个项目**。
6. **`AI-mentalHealthSystem/demo/` 是用户自己的练兵场，不要管理它。** 别提议往里加东西、别给它写大纲、别说「要不要我帮你把这里改成……」。他 2026-10-08 的原话：「Demo 不要，这就是个练兵场，这不需要你管理」。**参考可以，动手不行。**
7. **执行顺序是「先学全、再落地」**（见下一节）。用户还没开始学全，所以现在**不要建议动任何一关**。他问什么答什么。

---

## 目录

- [一、这份文档是什么](#一这份文档是什么)
  - [执行顺序：先学全，再落地](#执行顺序先学全再落地)
- [二、现状盘点：两条线各自走到哪了](#二现状盘点两条线各自走到哪了)
- [三、先把环境和版本钉死（防坑清单）](#三先把环境和版本钉死防坑清单)
- [四、落地路线图：功能点 → 知识点](#四落地路线图功能点--知识点)
- [五、进度看板](#五进度看板)
- [六、深度加分点（面试话术锚）](#六深度加分点面试话术锚)
- [七、已知坑 / 笔记待补](#七已知坑--笔记待补)
- [附录 A：最小可行配置（照抄）](#附录-a最小可行配置照抄)
- [附录 B：文件与笔记索引](#附录-b文件与笔记索引)

---

## 一、这份文档是什么

学习 Redis 有**两条线**，别混：

| 线 | 载体 | 回答的问题 |
|---|---|---|
| **知识线** | `RedisNotes/1~10.md`（独立仓库） | Redis 是什么、命令怎么敲、为什么快 |
| **落地线**（本文档） | `ai-springboot/docs/Redis接入进度.md` | **这个项目里，哪里该用 Redis、怎么用、用了有什么收益** |

知识线已经跑到第 10 课（`10.Java中使用Redis.md`，48KB，含完整的第十章路线图）。本文档不重复它，只做**映射**：把知识线的第 N 课，挂到本项目的具体文件、具体行号上。

### 执行顺序：先学全，再落地

用户 2026-10-08 明确表态「我打算学全 Redis」。所以工作分两阶段，**阶段一没走完，阶段二一行都不动**：

| 阶段 | 做什么 | 在哪 | 本文档的角色 |
|---|---|---|---|
| **阶段一 · 学全** | 走完知识线第 2~9 课 + 补齐 5 个知识洞 | `RedisNotes/` | **不管**。只记一句：它是阶段二的门槛 |
| **阶段二 · 落地** | 把 Redis 接进 ai-springboot，共九关 | 本项目 | 本文档正文（第四、五章） |

**为什么这么排**：第 1 关（缓存一致性）、第 6 关（分布式锁）这些是面试的分数大头，但光看会「知道但说不出来」。理论先成体系，落地时才有得对号入座；顺序反了就是照着抄，抄完也讲不出口。

> **本文档不写完整代码。** 它写的是：锚点（改哪个文件）+ 方案（用什么结构）+ 产出（应该多出哪些类/注解）+ 验收（`redis-cli` 敲什么能看到效果）。代码由用户自己写。

---

## 二、现状盘点：两条线各自走到哪了

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
| 端口 | 8080（默认值，yml 里没写 `server.port`） | — |

**关键：`pom.xml` 里目前没有任何 redis 依赖，`application.yml` 里没有任何 `spring.data.redis.*`。接入是从零开始。**

### 2.2 这个项目为什么值得接 Redis（真实锚点）

不是「为了用而用」，下面每一条都能对应到代码里的一个具体缺陷或成本：

| 锚点（文件:行） | 现状 | Redis 能解决什么 |
|---|---|---|
| `controller/UserController.java:51` `logout()` | **空实现**，只打日志就 `Result.success()` | JWT 无状态 → 登出后旧 token 仍然有效，**这是真实安全漏洞**。Redis 黑名单可修 |
| `entity/KnowledgeArticle.java:62` `readCount` | 字段存在，但详情接口没看到维护逻辑 | 浏览量 `INCR` + 定时回写，避免每看一次打一次库 |
| `controller/KnowledgeController.java:38` `categoryTree()` | 每次请求都查库 | 分类树读多写少、变更极少 → 缓存收益率最高 |
| `controller/KnowledgeController.java:92` `getArticleDetail()` | 每次请求都查库 | 文章详情是典型热点读 |
| `entity/UserFavorite.java` | 只有 `(userId, articleId)`，无去重保障 | 收藏/点赞去重 → Set；计数 → `INCR` |
| `controller/UserController.java:30` `login()` | 无限流 | 登录接口是撞库的天然目标 → `INCR`+`EXPIRE` 限流 |
| `controller/UserController.java:57` `addEmotionDiary()` | 无幂等 | 前端狂点会写入多条日记 → `SET NX` 防重 |
| `common/FileCleanupTask.java` + `entity/SysFileInfo.java` | 定时清理临时文件 | 多实例部署会重复跑 → 分布式锁 |
| `entity/AiAnalysisTask.java` | 有 `status`/`priority`/`retryCount`/`maxRetryCount` | 这本身就是一张任务队列表 → Redis Stream / ZSet 延时队列 |
| `entity/EmotionDiary.java:35` `diaryDate` + `moodScore` | 一天一条 | 天然是「签到」模型 → BitMap 统计连签 |
| `service/AnalyticsService.java` | 聚合统计，开销大 | 缓存 + 定时刷新；UV 用 HyperLogLog |

### 2.3 知识线（RedisNotes）走到哪了

- `1`~`9.md`：CLI 层面已过一遍（基础命令、五种结构、发布订阅、新版结构、事务、持久化、主从、哨兵）
- `10.Java中使用Redis.md`：**已完成**环境搭建、Maven 依赖、`StringRedisTemplate` 六个 TODO、编码阈值实测、序列化原理、IO 多路复用、分布式锁原理
- 其中**第十章**是知识线自己的路线图（10.1 已完成 / 10.2 下次继续 / 10.3 第 1~9 课 / 10.4 笔记待补）
- **5 个知识洞**：第 10 章的 10.3 没列到，但「学全」绕不开 —— ① Pipeline 管道 ② Lua 脚本 ③ 客户端选型（Lettuce / Jedis / Redisson）④ 运维命令（`SLOWLOG` / `INFO` / `MONITOR` / `MEMORY USAGE`）⑤ 版本新特性（6.0 多线程 IO、7.0 Sharded Pub/Sub、Functions）。**这几条归阶段一，本文档不展开**

> ⚠️ 用户提过一次「11 开头的那个 md 是路线」，但 `RedisNotes/` 里**不存在 11 开头的文件**（只有 1~10）。他指的应该是 `10.Java中使用Redis.md` 里的**第十章**。下次别提「那个 11 文件」。

### 2.4 两条线的对接点

| 知识线（RedisNotes 第十章） | 落地线（本项目） |
|---|---|
| 第 2 课 序列化（差实操） | 第 0 关：`RedisConfig` 选型 |
| 第 3 课 五种数据结构 | 第 1、4 关 |
| 第 4 课 缓存实战 ⭐⭐⭐⭐⭐ | 第 1 关 |
| 第 5 课 分布式锁 + 限流 | 第 5、6 关 |
| 第 6 课 高级数据结构 | 第 7 关 |
| 第 7 课 高可用与运维 | 本项目单体，暂不涉及 |
| 第 9 课 面试冲刺 | 第 8 关 |

> 这张表同时也是**启动顺序表**：左列没走完，右列一关都不能动。详见下一节「执行顺序」。

---

## 三、先把环境和版本钉死（防坑清单）

**这一节必须在写第一行代码前过一遍，四条里踩任何一条都会卡半天。**

| # | 坑 | 正确做法 |
|---|---|---|
| 1 | **序列化器版本** | Boot 3.5.15 → Jackson 2 → `GenericJackson2JsonRedisSerializer`（**带 2**）。判据：看 Jackson 包名，有 `tools.jackson` 才用不带 2 的 |
| 2 | **配置前缀** | 是 `spring.data.redis.*`。写 `spring.redis.*`（Boot 2.x 老写法）**不报错、静默忽略**，你会以为连上了其实连的是默认 localhost:6379 —— 极难排查 |
| 3 | **没密码就别写 password** | 写 `spring.data.redis.password=`（空值）Lettuce 仍会发 `AUTH`，会收到 `ERR Client sent AUTH, but no password is set` → 连接失败。**整行不写** |
| 4 | **`@class` 缺失** | `GenericJackson2JsonRedisSerializer.builder().build()` 默认不写 `@class`，配上泛型擦除 → 取出来只能是 `LinkedHashMap`，强转报 `ClassCastException` |

**本机 Redis 环境（2026-10-06 搭好）**

- 服务端：`E:\Downloads\Redis-8.10.2-Windows-x64-msys2-with-Service\Redis-8.10.2-Windows-x64-msys2-with-Service\`（`redis-server.exe` / `redis-cli.exe` / `redis.conf` 都在这一层）
- 启动方式：**手工跑 `redis-server`**，没注册成 Windows 服务、没带配置文件 → 默认 `protected-mode yes` + 无密码。只接受本机连接，练习安全
- 端口 6379，无密码
- ⚠️ 真实下载目录在 **`E:\Downloads`**，不是 `C:\Users\lenovo\Downloads`（那个是空的）

**验收入口**（Redis 起来后）

```bash
redis-cli.exe ping          # → PONG
redis-cli.exe --raw         # 中文原文显示
DBSIZE                      # 当前库几个 key
```

---

## 四、落地路线图：功能点 → 知识点

> 分 9 关（第 0 关是地基）。每关给出：**锚点 / 方案 / 产出 / 验收 / 对应知识课**。
>
> **⛔ 整章的前提：阶段一（学全）走完才启动。** 下面每关的「必须已学完」列的是**最低门槛** —— 课没学完就动这一关，等于照着抄。
>
> 关卡之间也有依赖：后一关会用到前一关的基建，跳着做会返工。

**关卡 × 前置课一览**（从左到右就是建议顺序）

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

> ⚠️ 第 1 关排在最前面是有意的：缓存一致性、三大问题是**面试权重最高**的（⭐⭐⭐⭐⭐），也是最容易「知道但讲不出来」的，值得在它上面多花时间。别因为它叫「第 1 关」就急着做完往下走。

### 第 0 关：地基（依赖 + 配置 + 序列化）⭐ 必做

| 项 | 内容 |
|---|---|
| **锚点** | `pom.xml`（加依赖）、`src/main/resources/application.yml`（加配置）、`config/` 包（新建 `RedisConfig.java`，与现有 `MpConfig`/`JwtConfig` 并列） |
| **方案** | 依赖 `spring-boot-starter-data-redis`（版本由 Boot parent 管，**不写 version**）；配置 `spring.data.redis.host/port`；`RedisConfig` 暴露 `RedisTemplate<String,Object>`（JSON 序列化） |
| **产出** | `config/RedisConfig.java`；能同时注入 `StringRedisTemplate` 和 `RedisTemplate<String,Object>` |
| **验收** | 启动不报错；写一个临时测试塞个对象进去，`redis-cli --raw GET <key>` 看到**干净 JSON**（不是乱码、不是 `\xac\xed` 开头） |
| **知识课** | 第 2 课（序列化）—— 这是把知识线第 2 课的「差实操」补齐 |

> 💡 为什么 value 用 JSON 而不是 `StringRedisTemplate` 一把梭：本项目缓存的是 `ArticleResponseDTO`、分类树这类**对象**，手写 `ObjectMapper` 转换会在每个 Service 里重复一遍。JSON 序列化器一次性解决。但**计数、锁、黑名单这类单值仍用 `StringRedisTemplate`**，别把简单的搞复杂。

---

### 第 1 关：缓存实战（三大问题 + 一致性）⭐⭐⭐⭐⭐

**这是面试第一高频，也是收益率最高的一关。选两个对比鲜明的场景做。**

**场景 A：分类树缓存 —— 最干净的缓存**

| 项 | 内容 |
|---|---|
| **锚点** | `controller/KnowledgeController.java:38` `categoryTree()` → `service/KnowledgeCategoryService.java` |
| **写操作（要删缓存的）** | `KnowledgeController.java:46` 和 `:54` 两个 `deleteCategory` |
| **方案** | `@Cacheable(value="categoryTree", key="'all'")`；两个删除方法上 `@CacheEvict(value="categoryTree", allEntries=true)` |
| **为什么它最简单** | 全量一个 key、变更极少、数据量小 → 不会遇到分页 key 爆炸，适合先跑通 |

**场景 B：文章详情缓存 —— 一致性训练场**

| 项 | 内容 |
|---|---|
| **锚点** | `KnowledgeController.java:92` `getArticleDetail(@PathVariable String id)` ⚠️ `KnowledgeArticle.id` 是 **String** |
| **写操作（三个，都要删缓存）** | `:100` `updateArticleDetail`、`:108` `deleteArticle`、`:84` `putArticleStatus` |
| **方案** | `@Cacheable(value="article", key="#id")` + 三个写方法 `@CacheEvict(value="article", key="#id")` |
| **一致性要求** | **Cache Aside：先更新 DB → 再删缓存，不是更新缓存。** 三个写操作漏掉任何一个，都会留下永久脏缓存 —— 这是最容易翻车的地方，写完逐个核对 |

**必答的三大问题（用本项目的场景讲，别背定义）**

| 问题 | 在本项目的触发点 | 解法 |
|---|---|---|
| **穿透** | 请求 `getArticleDetail(id="乱填")`，DB 查不到 → 每次都打库 | 缓存空值（TTL 60s 短一点）；或上布隆过滤器 |
| **击穿** | 热门文章 key 到期的瞬间，并发全打到 DB | 互斥锁（`SET NX`）只放一个去查；或逻辑过期（不设物理 TTL） |
| **雪崩** | 项目重启后大量 key 同一时刻过期 | TTL 加随机值，如 `30min + random(0,5min)` |

**验收**：缓存生效（第二次请求不打 SQL，看 MyBatis 日志）；改一篇文章后，`GET` 立刻拿到新值（说明 `@CacheEvict` 生效）。

**知识课**：第 3 课（结构选型）+ 第 4 课（⭐⭐⭐⭐⭐ 主课）

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

> 这一关的特别之处：它**修的是一个真实存在的安全缺陷**，不是「加个缓存」这种可有可无的优化。面试讲这个比讲缓存有说服力得多。

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
| 2 | TTL = token 的**剩余**有效期 | `JwtTokenUtil.generateToken()` 用 `withExpiresAt(now + expiration)`；解析 `DecodedJWT.getExpiresAt()` 减去当前时间 |
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
| **锚点** | `entity/KnowledgeArticle.java:62` `readCount`、`controller/KnowledgeController.java:64` `articlePage()`（如果支持按热度排序） |
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

**项目经验怎么讲（模板）**

> 「项目的知识库文章详情接口是读多写少，我加了 Redis 缓存，用 Cache Aside 模式，**先更新 DB 再删缓存**。针对缓存穿透我缓存了空值并设了短 TTL…」

**场景设计题（面试最爱问，用本项目当素材）**

- 「你的分布式锁有什么问题？主从切换时会怎样？」→ 第 6 关
- 「缓存和数据库如何保证一致性？」→ 第 1 关
- 「设计一个排行榜，实时更新前 100 名」→ 第 4 关
- 「JWT 怎么做登出？」→ 第 3 关（**这题很多人答不上来，答得出是加分项**）

**知识课**：第 9 课（⭐⭐⭐⭐⭐）

---

## 五、进度看板

### 阶段一 · 学全（先做，做完才动阶段二）

> 明细归 `RedisNotes` 管，这里只放**门槛判断** —— 阶段二能不能启动，看这一节。

- [x] 第 1 课 `StringRedisTemplate` 基础（六个 TODO 已批改）
- [ ] 第 2 课 序列化 —— 三个实操：JDK 序列化看乱码 + `STRLEN` 量字节 / 对比 JSON 与 JDK 体积 / 换 JSON 序列化器看干净 JSON
- [ ] 第 3~9 课
- [ ] 5 个知识洞：Pipeline / Lua / 客户端选型 / 运维命令 / 版本新特性
- [ ] 理清 `RedisNotes/` 下**嵌套的两个 Maven 项目**（根 `pom.xml` 与 `demo/pom.xml` 互不认识，`Config` 配在 `demo` 里对根项目无效）—— 不理清，序列化实操会报 `No qualifying bean`
- [ ] **🚧 门槛：以上全部完成后，才启动阶段二**

### 阶段二 · 落地（全部未启动，等阶段一）

- [ ] **第 0 关** 地基：依赖 + 配置 + `RedisConfig`
- [ ] **第 1 关** 缓存实战（分类树 → 文章详情）
- [ ] **第 2 关** 浏览量 `INCR` + 定时回写
- [ ] **第 3 关** JWT 登出黑名单
- [ ] **第 4 关** 热门文章排行榜（ZSet）
- [ ] **第 5 关** 限流 + 防重复提交
- [ ] **第 6 关** 分布式锁（FileCleanupTask）
- [ ] **第 7 关** 高级结构（AI 结果缓存 / 签到 / UV / 队列 / 秒传）
- [ ] **第 8 关** 面试包装

### 阻塞项

- [ ] **阶段二整体 blocked by 阶段一** —— 2026-10-08 用户决定先学全，所以上面九关**现在一个都不要碰**
- [ ] Redis 服务端**还没启动**（用户 2026-10-08 表示暂不启动）→ 两个阶段的验收都做不了，先别催

---

## 六、深度加分点（面试话术锚）

不是背来的结论，每条都能在**本项目代码里指到出处**：

| # | 加分点 | 出处 |
|---|---|---|
| 1 | `embstr` 的 44 字节是 **64 − 16 − 3 − 1** 算出来的，不是拍脑袋 | 知识线实测 |
| 2 | List 的紧凑编码阈值按**字节**（8KB），Hash/Set/ZSet 按**个数**（512/128）—— 这个例外能说出来很加分 | 知识线实测 |
| 3 | `delete(K)` 返回 `Boolean`，`delete(Collection<K>)` 返回 `Long` | 第 1 课 |
| 4 | `getExpire` 的 `-1`（永久）要当作「**存在**」，只有 `-2` 是不存在 | 第 1 课 |
| 5 | 没有 `@class` + 泛型擦除 → Jackson 只能还原成 `LinkedHashMap` | 第 0 关会踩 |
| 6 | `MULTI/EXEC` 是**事务不是锁**，两者解决不同问题 | 知识线 `6.事务.md` |
| 7 | 乐观锁的本质是「失败重试」，**有副作用的操作不能重放**，所以必须用悲观锁 | 第 5、6 关 |
| 8 | 主从异步复制 → `SET NX` 锁会丢 → Redlock 的由来与争议 | 第 6 关 |
| 9 | 黑名单 TTL 设「token 剩余有效期」而非固定值：让 Redis 的 TTL 帮你自动清理 | 第 3 关 |
| 10 | 浏览量用 Redis 计数而非 `UPDATE ... +1`：避免热点行**行锁竞争** | 第 2 关 |
| 11 | Redis 6.0+ 是「**多线程 IO + 单线程命令执行**」，不是纯单线程 | 知识线 |

---

## 七、已知坑 / 笔记待补

### 知识线笔记里的小错（`RedisNotes/`）

- `2.常用命令.md` 写「查询键值对：`GET 键 值`」—— `GET` **只接一个键**，不接值
- `2.常用命令.md` 写「`EXISTS 键` 输出的是 0/1 布尔值」—— 严格说返回的是**个数**（0 或 1），不是布尔类型
- `3.常见数据结构.md` ZSet 两行留白：`ZINCRBY`（成员分数增加）、`ZREMRANGEBYRANK`（按排名范围删除）—— 用户表示不补，留个记录

### 本项目接 Redis 时的坑（按踩坑概率排序）

1. **序列化器带不带 2** —— 见第 0 关，最高频
2. **`spring.redis` vs `spring.data.redis`** —— 静默失效，最难查
3. **`password` 空值** —— 没密码时整行不写
4. **`@CacheEvict` 漏写** —— 三个写文章的方法（`updateArticleDetail`/`deleteArticle`/`putArticleStatus`）漏一个就留永久脏缓存
5. **`KnowledgeArticle.id` 是 String 不是 Long** —— 拼 key、写 `@Cacheable` 的 SpEL 时注意
6. **`@Scheduled` 需要 `@EnableScheduling`** —— 忘了定时任务根本不跑，还不报错
7. **`redis.execute(...)` 的 lambda 歧义** —— `RedisCallback` 和 `SessionCallback` 两个重载，必须显式强转

---

## 附录 A：最小可行配置（照抄）

> ⚠️ 仅供第 0 关参考。写代码前先跟用户报备要动哪几个文件。

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

**`config/RedisConfig.java`**（Boot 3.5.15 → **带 2**）

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // ⚠️ Boot 3 用带 2 的；Boot 4 的等价类是 GenericJacksonJsonRedisSerializer（不带 2）
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
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
| `pom.xml` | 第 0 关加 Redis 依赖 |
| `src/main/resources/application.yml` | 第 0 关加 `spring.data.redis.*` |
| `src/main/java/org/example/aispringboot/config/` | 新建 `RedisConfig.java`（与 `MpConfig`/`JwtConfig` 并列） |
| `controller/UserController.java` | 第 3 关（logout）、第 5 关（login 限流、日记防重） |
| `controller/KnowledgeController.java` | 第 1 关（分类树/文章详情缓存）、第 7 关（秒传） |
| `util/JwtAuthticationFilter.java` | 第 3 关（黑名单校验） |
| `common/FileCleanupTask.java` | 第 6 关（分布式锁） |
| `service/AnalyticsService.java` | 第 7 关（UV / 统计缓存） |
| `docs/` | `DTO怎么写.md`、`事务练习-删除知识分类.md`、本文件 |

**知识线（RedisNotes，另一个仓库）**

| 文件 | 内容 |
|---|---|
| `1.Redis是啥.md` ~ `9. 哨兵节点.md` | CLI 层基础 |
| `10.Java中使用Redis.md` | Java 接入，**第十章是知识线自己的路线图** |
| `demo/`、`pom.xml`、`src/` | 练习代码（嵌套 Maven 项目问题待理清） |

---

*本文档由 2026-10-08 的项目扫描生成。换电脑后：装 Redis（下 Release 不是 Source）→ 拉 `AI-mentalHealthSystem` → 读本文档第 0 关继续。*
