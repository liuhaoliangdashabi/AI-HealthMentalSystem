# Redis 接入进度

> **这是本项目的唯一进度文档。** 想知道「Redis 接得怎么样了、下一步干什么」，读这一篇就够了，不用翻别的文件，也不用重新扫代码。
>
> 更新日期：**2026-10-09**
>
> `docs/` 下另外两篇（`DTO怎么写.md`、`事务练习-删除知识分类.md`）是**知识笔记**，不含进度信息。

---

## 0. AI 先读这段（当前进度快照）

### 0.1 一句话状态

**第 0 关（地基）已完成，第 1 关（缓存实战）做到一半：读路径已跑通，写路径的缓存失效还没写。**

### 0.2 进度表

| 关卡 | 主题 | 状态 |
|---|---|---|
| **第 0 关** | 地基：依赖 + 配置 + 序列化 | ✅ **完成**（2026-10-08） |
| **第 1 关** | 缓存实战：分类树缓存 | 🔧 **进行中** —— 读路径 ✅ / 写路径 ❌ |
| 第 2 关 | 浏览量 `INCR` + 定时回写 | ⬜ 未开始 |
| 第 3 关 | JWT 登出黑名单 ⭐ 最出彩 | ⬜ 未开始 |
| 第 4 关 | 热门文章排行榜（ZSet） | ⬜ 未开始 |
| 第 5 关 | 限流 + 防重复提交 | ⬜ 未开始 |
| 第 6 关 | 分布式锁 | ⬜ 未开始 |
| 第 7 关 | 高级数据结构 | ⬜ 未开始 |
| 第 8 关 | 面试包装 | ⬜ 未开始 |

### 0.3 第 1 关的精确状态（改代码前必读）

锚点：`service/KnowledgeCategoryService.java`

| 部分 | 状态 | 说明 |
|---|---|---|
| 读路径 `listAll()` | ✅ 用户已填完 | 查缓存 → 未命中查库 → 回填（**带 TTL**）→ 返回 |
| 缓存 key / TTL | ✅ | `cache:category:tree` / `Duration.ofMinutes(10)` |
| 写路径 `deleteCategory(Long)` | ❌ **只有 TODO 注释，没调用** | 约 `:90` |
| 写路径 `deleteCategory(Long,Long)` | ❌ **只有 TODO 注释，没调用** | 约 `:112` |
| `evictCategoryTreeCache()` 方法体 | ✅ 已写（`redisTemplate.delete(key)`） | 只是没人调它 |

> 🔴 **这是当前挂在身上的真 bug**：删了分类，缓存原封不动，**脏数据能活满 10 分钟 TTL**。

### 0.4 下一步就做这个（三步走，别一次全上）

```
① 加最朴素的一行
   在两句 categoryMapper.deleteById(categoryId); 的下一行加：
       evictCategoryTreeCache();

② 验证它真的删了
   redis-cli TTL cache:category:tree      # 删之前：正数（如 543）
   调 DELETE /api/knowledge/category/{非1的、真实存在的分类}
   redis-cli TTL cache:category:tree      # 删之后：应为 -2

③ 停掉 Redis 再删一次分类 ← 别跳过这步
   会看到接口报 500、分类删不掉。这是「缓存反噬主业务」（见 5.4）
   亲手撞一次，比看一百行解释管用

④ 再上 afterCommit 钩子 + try-catch 降级（见 5.5 的四种解法）
```

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

*本文档最后更新：2026-10-09。换电脑后：装 Redis（下 Release 不是 Source）→ 拉 `AI-mentalHealthSystem` → 读本文档第 0 节继续。*
