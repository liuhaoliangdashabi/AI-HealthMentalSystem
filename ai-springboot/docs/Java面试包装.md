# Java 面试包装（并发 + 集合）

> 配套文档：`Redis面试包装.md`（中间件篇）。这份是**语言篇**。
>
> 两份的分工必须记清楚：
>
> | 文档 | 回答的问题 | 面试官听完的判断 |
> |---|---|---|
> | Redis 篇 | 你会用中间件吗 | 「这人懂分布式状态怎么放」 |
> | **Java 篇（本文）** | 你懂语言和并发吗 | 「这人写代码不会写出线上事故」 |
>
> **线程池为什么不塞进 Redis 篇**：Redis 篇的主线是「Redis 解决了哪五类问题」，
> 八节全部围绕这条线。硬插一节线程池，主线就断了。而且线程池的密度
> （七个参数 / 四种拒绝策略 / 状态机 / `@Async` 失效场景）远不止一节能装下。

---

## 0. 一句话定位

**Redis 篇讲「我把状态放对了地方」，Java 篇讲「我把线程管住了」。**

后者在面试里的权重其实更高——因为几乎每个项目都有线程池，但只有一部分项目真的用 Redis。
线程池答不上来，面试官会直接怀疑你没写过生产代码。

---

## 1. 简历怎么写

### 版本 A：一行

> 负责后端并发模型治理：自建 `@Async` 线程池（有界队列 + 背压），修复异步任务异常静默丢失问题。

### 版本 B：三行（推荐）

> - 自建 `ThreadPoolTaskExecutor` 替换 Spring Boot 默认配置，用有界队列 + `CallerRunsPolicy` 把「无限堆积到 OOM」改成「队列满时降级为同步执行」
> - 修复 `@Async void` 方法的异常黑洞：任务失败只进日志、调用方无感知，改为可观测的失败上报
> - 定时任务线程池 poolSize 从默认 1 调到 4，解决单个慢任务阻塞其余 5 个任务的问题

### 版本 C：一段（自我评价里用）

> 写并发代码时会先问三个问题：这段状态谁能改？改的时候有没有别人在读？
> 池满了之后任务去哪？项目里的异步补偿、定时回写、消息消费都按这个思路做过加固。

---

## 2. 项目里的五个并发场景（都是真代码，不是编的）

这一节是整份文档的地基。**面试时不要背概念，讲你项目里的这几处。**

### 2.1 `@Async` 异步补偿删 OSS 文件

`service/FileCompensator.java:18`

```java
@Async
public void compensateDelete(String objectKey){
    for (int i = 0; i <= 3; i++) {
        try{
            ossUtil.delete(objectKey);
            return;
        }catch(Exception e){
            log.warn("补偿删除失败，第{}次尝试", i);
        }
    }
    throw new BusinessException("三次补偿删除失败，需要人工干预");  // ← 这行永远不会被调用方看到
}
```

**这里藏着一个真 bug**：方法返回 `void`，`throw` 发生在 worker 线程里，
调用方（主线程）**根本收不到**这个异常。Spring 只会把它交给
`AsyncUncaughtExceptionHandler`，默认实现就是打一条 error 日志。

所以注释里写的「报告上级」实际上只写进了日志文件。**这是「异步」最典型的认知陷阱**：
异步的代价不是慢，是**错误失去了传播路径**。

三种修法（面试时说出取舍就是加分）：

| 修法 | 代价 |
|---|---|
| 返回 `CompletableFuture<Void>`，调用方 `.exceptionally()` 接 | 调用方要改成异步写法，侵入性大 |
| 配 `AsyncUncaughtExceptionHandler`，在里面发告警/写死信表 | 最轻，但只是「有人能看到」，不是「有人处理」 |
| 失败写 Redis 死信队列，定时任务捞出来重试 | 最重，但真正闭环 —— 和 `AiTaskConsumer` 的死信是同一套思路 |

### 2.2 定时任务线程池

`resources/application.yml:41`

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 4   # 默认值为1，注意跌过坑
```

**默认 `poolSize = 1`** —— 项目里 6 个 `@Scheduled` 方法共用这一个线程。
只要有一个任务卡住（比如 `AiTaskConsumer` 里那次 5~10 秒的模型调用），
其余 5 个全部排队。表现是「所有定时任务一起变慢」，但日志上看不出谁堵了谁。

这个坑的隐蔽性在于：**代码完全正确，问题出在配置的默认值上**。

### 2.3 Redis Stream 的单线程消费

`service/AiTaskConsumer.java:67`

```java
@Scheduled(fixedDelay=800)
public void consumeAiTask(){
    List<MapRecord<String,Object,Object>> records = redis.opsForStream().read(
          Consumer.from(RedisKeys.AI_TASK_GROUP, consumerName),
          StreamReadOptions.empty().count(10),
          StreamOffset.create(RedisKeys.AI_TASK_STREAM, ReadOffset.lastConsumed()));
    ...
}
```

**为什么这里是单线程，而不是再开个线程池并行处理？**

这是面试里非常值钱的一段回答：

> 单线程消费是**故意的**。同一个 `sessionId` 的分析任务如果被两个线程并行处理，
> 后写的会覆盖先写的，用户看到情绪花园在「愉悦」和「焦虑」之间来回跳。
>
> 要提升吞吐，正确的做法不是在这个方法里开线程池，而是
> **多实例部署 + 不同的 `consumerName`** —— 消费者组保证同一条消息只投给组内一个人，
> 水平扩展由 Redis 负责，JVM 内部不需要并发。

**核心认知：并发的边界应该划在「进程之间」，而不是「线程之间」。**
进程内开线程池解决不了顺序问题，只会把顺序问题变成偶发的脏数据。

### 2.4 流式响应里的 `AtomicBoolean` 首字标记

`AiService/PsychologicalSupportService.java:63`

```java
return Flux.create(sink->{
    AtomicBoolean firstToken = new AtomicBoolean(true);
    ...
});
```

**为什么不用普通 `boolean`？** 两个理由，第二个才是关键：

1. **语法层面**：lambda 捕获的局部变量必须是 effectively final，普通 `boolean` 改不了。
2. **并发层面**：`Flux.create` 的 `sink` 回调可能在别的线程触发，
   `boolean` 的写入不保证对其他线程可见。

第 1 点是「不得不」，第 2 点是「应该」。面试时说第 2 点，说明你想过内存模型。

### 2.5 `ThreadLocalRandom` 做 TTL 抖动

`config/redisconfig/CacheConfig.java:45`

```java
.entryTtl(Duration.ofMinutes(30 + ThreadLocalRandom.current().nextInt(10)))
```

**为什么加随机？** 防止缓存**同时过期**（雪崩）。
一批 key 在同一秒写入、TTL 相同，就会在同一秒集体失效，请求瞬间全打到数据库。

**为什么用 `ThreadLocalRandom` 而不是 `Random`？**
`Random` 内部是一个 `AtomicLong`，多线程调 `nextInt()` 会 CAS 自旋抢同一个种子。
高并发下这是真实的竞争点。`ThreadLocalRandom` 每个线程一个种子，无竞争。
（JDK 7 引入，JDK 17 上 `Random` 也做了改进，但 `ThreadLocalRandom` 依然是首选。）

---

## 3. 线程池：七个参数与一条主线

### 3.1 七个参数

```java
new ThreadPoolExecutor(
    int corePoolSize,        // ① 核心线程数：常驻，默认不回收
    int maximumPoolSize,     // ② 最大线程数
    long keepAliveTime,      // ③ 非核心线程空闲存活时间
    TimeUnit unit,           // ④ 时间单位
    BlockingQueue<Runnable> workQueue,   // ⑤ 任务队列
    ThreadFactory threadFactory,         // ⑥ 线程工厂（起名用，排查问题全靠它）
    RejectedExecutionHandler handler     // ⑦ 拒绝策略
);
```

**⑥ 线程工厂为什么重要**：不设的话线程名是 `pool-1-thread-3`，
`jstack` 里根本分不清这是哪个业务池。设成 `async-` 前缀，一眼就能定位。

### 3.2 流转顺序（最容易被记错的一点）

```
提交任务
   │
   ├─ 当前线程数 < corePoolSize ？ ──是──▶ 新建核心线程执行
   │
   ├─ 否 ──▶ 尝试放入 workQueue
   │            │
   │            ├─ 队列没满 ──▶ 入队，等空闲线程来取
   │            │
   │            └─ 队列满了 ──▶ 当前线程数 < maximumPoolSize ？
   │                              ├─ 是 ──▶ 新建非核心线程执行
   │                              └─ 否 ──▶ 触发拒绝策略
```

**必须背下来的一句话：队列满了才创建非核心线程。**

这是 `ThreadPoolExecutor` 和大多数人直觉相反的地方。直觉是「线程不够就加线程」，
实际是「先排队，队伍排不下了才加人」。所以如果你给了一个**无界队列**，
`maximumPoolSize` 这个参数**永远不会生效**。

### 3.3 四种拒绝策略

| 策略 | 行为 | 什么时候用 |
|---|---|---|
| `AbortPolicy`（默认） | 抛 `RejectedExecutionException` | 宁可报错也不能悄悄丢 |
| `CallerRunsPolicy` | **调用方线程自己跑这个任务** | 不能丢、但可以慢（本项目选这个） |
| `DiscardPolicy` | 静默丢弃，什么都不发生 | 几乎不该用（丢了没人知道） |
| `DiscardOldestPolicy` | 丢掉队列里最老的，再试一次 | 只保留最新状态的场景（如行情推送） |

**`CallerRunsPolicy` 的副作用要主动说**：
它让调用方线程同步执行任务，等于**给上游施加背压**。这既是优点也是风险——
如果调用方是 Tomcat 的工作线程，极端情况下所有请求线程都在跑异步任务，
新请求进不来，服务看起来像「假死」。所以它适合「任务量可控、不能丢」的场景，
不适合「流量突刺、可以降级」的场景。

还有一行容易被忽略的源码：

```java
public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
    if (!e.isShutdown()) {
        r.run();          // ← 注意这个判断
    }
}
```

**线程池已经 shutdown 时，`CallerRunsPolicy` 会直接丢弃任务**，不跑也不报错。

### 3.4 Spring Boot 默认线程池的 OOM 陷阱

`TaskExecutionProperties` 的默认值：

| 参数 | 默认值 | 后果 |
|---|---|---|
| `corePoolSize` | 8 | —— |
| `maxPoolSize` | `Integer.MAX_VALUE` | 永远用不上（见 3.2） |
| `queueCapacity` | `Integer.MAX_VALUE` | **队列永远装不满** |

三者组合的结果：

- 队列永远不满 → **永远不会创建第 9 个线程**
- 永远不会触发拒绝 → **没有任何背压**
- 任务无限入队 → **堆内存无限增长 → OOM**

**这是「看起来最安全、实际最危险」的配置**：所有参数都很大，像是「容量充足」，
实际上是「把过载问题从线程池推迟到了 GC」。

本项目的 `config/AsyncConfig.java:34` 改成 `core=4 / max=8 / queue=200`，
队列有界之后 `maxPoolSize` 才真正生效，拒绝策略才有机会被触发。

### 3.5 `@Async` 找线程池的 fallback 链

`AsyncExecutionAspectSupport.getDefaultExecutor()` 的逻辑（简化）：

```
1. beanFactory.getBean(TaskExecutor.class)
     ├─ 找到唯一一个 ──▶ 用它
     ├─ 找到多个 → NoUniqueBeanDefinitionException
     └─ 一个都没有 → NoSuchBeanDefinitionException
2. catch 之后：beanFactory.getBean("taskExecutor", Executor.class)
     ├─ 找到 ──▶ 用它
     └─ 找不到 ──▶ 返回 null
3. 返回 null ──▶ 退化成 SimpleAsyncTaskExecutor（每次 new 一个线程，等于没有池）
```

**为什么会「找到多个」？** 因为 `ThreadPoolTaskScheduler`（`@Scheduled` 用的那个）
**也实现了 `TaskExecutor` 接口**。项目里一旦同时有调度器和执行器，就会踩这一步。

这就是 `AsyncConfig.java:34` 为什么这么写：

```java
@Bean(name = {"applicationTaskExecutor", "taskExecutor"})   // ← 必须双名
```

Spring Boot 自动配置本来就注册了这两个名字（`applicationTaskExecutor` 是主名，
`taskExecutor` 是别名）。我们自定义 `@Bean` 会**覆盖**自动配置，
如果只给一个名字，第 2 步的 `getBean("taskExecutor")` 就找不到，
最终静默退化成 `SimpleAsyncTaskExecutor`——**不报错，但线程池等于没配**。

### 3.6 两种异常，一种收得到一种收不到

这是线程池最容易答错的一题：

| 异常类型 | 发生在哪 | 调用方收得到吗 |
|---|---|---|
| **拒绝异常**（队列满 + 线程满） | `submit()` 内部，**调用方线程** | ✅ 收得到，会往上抛 |
| **任务执行异常**（业务代码 `throw`） | worker 线程 | ❌ 收不到（`void` 返回值时） |

所以 `AbortPolicy` 并不可怕——它的异常会传回调用方。
真正危险的是**方法体里抛的异常**：`@Async void` 会把它吞掉，只留一条日志。

`FileCompensator` 踩的就是第二种（见 2.1）。

### 3.7 线程数怎么定

| 任务类型 | 公式 | 说明 |
|---|---|---|
| CPU 密集 | `N + 1` | 多 1 个是为了在某线程偶尔缺页时顶上 |
| IO 密集 | `N × (1 + 等待时间 / 计算时间)` | 也可粗略用 `2N` |
| 混合 | 拆成两个池 | 别混在一起，否则 IO 阻塞会拖死 CPU 任务 |

**N 是几核？** 注意容器环境：`Runtime.getRuntime().availableProcessors()`
在容器里可能拿到**宿主机**的核数（老版本 JDK 有这个问题，JDK 10+ 支持 cgroup 限制）。

**面试加分的一句**：公式只是初值。真实值要靠压测——先按公式给，再看
队列长度、拒绝次数、任务耗时 P99 三个指标调。

### 3.8 关闭时怎么不丢任务

```java
exec.setWaitForTasksToCompleteOnShutdown(true);   // 不立刻中断，让队列里的跑完
exec.setAwaitTerminationSeconds(30);              // 最多等 30 秒
```

**不设会怎样？** 发版时 JVM 直接退出，队列里的任务凭空消失。
`FileCompensator` 的任务如果正卡在这一刻，OSS 上就真留下孤儿文件了——
**补偿机制本身被发版干掉了**，这是最讽刺的一类 bug。

`awaitTerminationSeconds` 不能给太大：Spring 会阻塞关闭流程，
超过这个时间还是会被强杀。30 秒是个折中。

---

## 4. 线程安全：三要素与五个工具

### 4.1 三要素

| 要素 | 含义 | 破坏后的表现 |
|---|---|---|
| **原子性** | 一个或多个操作不可被中断 | `i++` 丢更新 |
| **可见性** | 一个线程的写，另一个线程能立刻看到 | 死循环读不到标志位 |
| **有序性** | 指令按代码顺序执行（编译器和 CPU 都可能重排） | 半初始化对象被发布 |

**`i++` 为什么不是原子的**：它编译成三条指令——读 `i`、加 1、写回。
两个线程交错执行就会丢更新。这是最经典的面试开场题。

### 4.2 `volatile` 管什么

**只管可见性 + 有序性，不管原子性。**

- 可见性：写操作立即刷到主存，读操作直接从主存读，不走 CPU 缓存。
- 有序性：插入内存屏障，禁止指令重排。

**它不能替代锁**：`volatile int i; i++` 依然会丢更新。

**DCL 单例为什么要 `volatile`**（必须能讲清）：

```java
public class Singleton {
    private static volatile Singleton instance;   // ← volatile 不能省

    public static Singleton getInstance() {
        if (instance == null) {                   // 第一次检查（无锁，为了性能）
            synchronized (Singleton.class) {
                if (instance == null) {           // 第二次检查（防止重复创建）
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```

`instance = new Singleton()` 是三步：

1. 分配内存
2. **初始化对象**（调构造器）
3. 把引用指向内存地址

**第 2 步和第 3 步可以重排**。重排后，另一个线程在第一次检查时
看到 `instance != null`（因为第 3 步已完成），于是直接返回——
**拿到一个构造器还没跑完的半成品对象**。`volatile` 禁止这个重排。

### 4.3 `synchronized` 锁升级

```
无锁 → 偏向锁 → 轻量级锁（CAS 自旋） → 重量级锁（OS monitor，挂起线程）
```

- **偏向锁**：只有一个线程访问，把线程 ID 记在对象头，之后进来不 CAS。
- **轻量级锁**：有竞争但很短暂，用 CAS 自旋，不挂起线程（挂起/唤醒要陷入内核态，很贵）。
- **重量级锁**：自旋失败次数多了，升级为 OS 互斥量，线程真正挂起。

**⚠️ 版本变化（说出来显得你关注新特性）**：
JDK 15 起偏向锁**默认关闭**（`-XX:-UseBiasedLocking`），JDK 18 彻底移除。
原因是偏向锁的撤销成本高，而现代应用里「真正无竞争」的场景比想象中少。
**项目用 JDK 17，偏向锁还在但已废弃**——面试时别把「偏向锁」当默认行为讲。

### 4.4 `AtomicBoolean` vs `volatile boolean`

| | 可见性 | 原子性 | 能做「检查并设置」吗 |
|---|---|---|---|
| `volatile boolean` | ✅ | ❌ | ❌ |
| `AtomicBoolean` | ✅ | ✅ | ✅ `compareAndSet(false, true)` |

**典型场景：只执行一次**。

```java
// ❌ 错的：两个线程可能同时通过判断
if (!flag) {
    flag = true;
    doSomething();
}

// ✅ 对的：CAS 保证只有一个线程能成功
if (flag.compareAndSet(false, true)) {
    doSomething();
}
```

本项目 `PsychologicalSupportService.java:63` 用 `AtomicBoolean` 存首字标记，
更多是为了绕开 lambda 的 effectively final 限制（见 2.4），
但如果这个标记要跨线程判断「是不是第一个 token」，就必须用 CAS 版本。

### 4.5 `ThreadLocal` 的内存泄漏

`ThreadLocalMap` 的 `Entry` 结构：

```java
static class Entry extends WeakReference<ThreadLocal<?>> {
    Object value;      // ← 强引用
}
```

- **key 是弱引用**：`ThreadLocal` 对象没人引用时会被 GC。
- **value 是强引用**：key 被回收后，Entry 变成 `key=null` 但 `value` 还在。
- **线程不销毁**：线程池里的线程是复用的，永远不会死。
- **结果**：这个 `value` 永远无法被回收，**线程活多久就泄漏多久**。

**唯一正确姿势**：

```java
try {
    threadLocal.set(xxx);
    // 业务
} finally {
    threadLocal.remove();   // ← 必须在 finally 里
}
```

---

## 5. 集合：选型与六个坑

### 5.1 四大家族

| 接口 | 有序 | 可重复 | 允许 null | 常用实现 |
|---|---|---|---|---|
| `List` | ✅ 插入序 | ✅ | ✅ | `ArrayList`（`LinkedList` 几乎不该用） |
| `Set` | `HashSet` 无序 / `LinkedHashSet` 插入序 / `TreeSet` 排序 | ❌ | `HashSet` 允许 1 个 | 同左 |
| `Map` | `HashMap` 无序 / `LinkedHashMap` 插入序 / `TreeMap` 排序 | key 不可重复 | `HashMap` 允许 1 个 null key | 同左 |
| `Queue` | ✅ | ✅ | `ArrayDeque` 不允许 | `ArrayDeque` / `PriorityQueue` |

**`LinkedList` 为什么几乎不该用**：

- 它只在「头部插入/删除」是 O(1)，但 `ArrayList` 尾部追加也是 O(1) 摊销。
- 它的内存开销是 `ArrayList` 的 3~4 倍（每个节点一个 `Node` 对象 + 两个指针）。
- **CPU 缓存不友好**：节点在堆上散落，遍历时 cache miss 极高。
- 实际测试里 `ArrayList` 在几乎所有场景都更快，包括中间插入（因为 `System.arraycopy` 是内存块拷贝，极快）。

**面试标准答法**：`LinkedList` 的唯一合理场景是把它当 `Deque` 用（`ArrayDeque` 更好），
或者需要在遍历中大量删除当前节点。

### 5.2 `HashMap` 的三个数字

| 数字 | 含义 | 为什么是这个数 |
|---|---|---|
| **16** | 默认容量 | 2 的幂，`(n-1) & hash` 才能等价于取模 |
| **0.75** | 负载因子 | 空间与冲突的折中（泊松分布下 0.75 时冲突概率可接受） |
| **8** | 链表转红黑树阈值 | 链表长度 8 时，红黑树的查找优势超过其维护成本 |

**退化的阈值是 6，不是 8** —— 中间留了 7 作为缓冲，避免在 7/8 之间反复转换。

**⚠️ 链表长度到 8 也不一定树化**：还有个隐藏条件 `MIN_TREEIFY_CAPACITY = 64`。
table 长度不足 64 时，`treeifyBin()` 会**先扩容**（`resize()`），而不是转红黑树。
因为小 table 上冲突多，通常是因为桶太少，扩容比树化更划算。

**这是面试高频追问**：「链表长度到 8 一定变红黑树吗？」→ 不一定，还要看 table 长度。

**为什么容量必须是 2 的幂**（这是核心）：

```java
// HashMap 取桶
int index = (n - 1) & hash;
```

`&` 只在 `n` 是 2 的幂时，才等于 `hash % n`。但**首要理由不是「快」，是「防负数」**：

```java
-5 % 8 == -5      // Java 的 % 对负数返回负数 → 直接当数组下标会越界
```

而 `(n-1) & hash` 的结果永远是 `[0, n-1]` 的非负数（因为 `n-1` 的高位全是 0）。
**用 `&` 是为了让负数哈希也能落到合法桶位**，快只是顺带的好处。

所以扩容是 `n * 2`，不是 `n * 1.5`。

**扰动函数**：

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

把高 16 位异或到低 16 位。因为桶位只用到低位，高位的差异会被浪费掉——
异或一下，让高位也参与运算，减少冲突。

### 5.3 `Arrays.asList` 的三个坑

```java
List<String> list = Arrays.asList("a", "b", "c");
```

**坑 1：返回的不是 `java.util.ArrayList`**

返回的是 `java.util.Arrays$ArrayList` —— 一个**静态内部类**，
和 `java.util.ArrayList` **没有任何继承关系**（都只实现了 `List`）。

| 操作 | 结果 |
|---|---|
| `get` / `set` | ✅ 正常 |
| `add` / `remove` / `clear` | ❌ `UnsupportedOperationException` |

**`set` 能改，而且改的是原数组**：

```java
String[] arr = {"a", "b", "c"};
List<String> list = Arrays.asList(arr);
list.set(0, "x");
System.out.println(arr[0]);   // "x"  ← 原数组被改了
```

因为它内部就持有那个数组的引用，`set` 就是 `a[i] = element`。

**坑 2：`Arrays.asList(int[])` 返回 `List<int[]>`，size = 1**

```java
int[] arr = {1, 2, 3};
List<int[]> list = Arrays.asList(arr);   // 注意泛型是 int[]
System.out.println(list.size());         // 1，不是 3
```

因为泛型不能是基本类型，`Arrays.asList` 的签名是 `asList(T... a)`，
`int[]` 被当成**一个** `T` 对象塞进变长参数里。

**你说的 `vector<vector<int>>` 类比完全正确**：外层 `vector` 的长度就是 1，
里面那个 `int[]` 才是 3 个元素。想拿 3 个元素得写 `Arrays.asList(1, 2, 3)`（自动装箱）。

**坑 3：`asList` 允许 null，`List.of` 不允许**（见 5.4）

### 5.4 `List.of` vs `Arrays.asList`

| | `Arrays.asList` | `List.of`（JDK 9+） |
|---|---|---|
| 可变性 | 半可变（`set` 可以） | **完全不可变**（全部抛异常） |
| null 元素 | 允许 | **抛 `NullPointerException`** |
| 底层 | 包装原数组 | 独立的不可变实现 |
| 用途 | 临时包装一个已有数组 | 常量列表 |

**`List.of` 为什么拒绝 null**：不可变集合一旦允许 null，
`contains(null)` 和 `indexOf(null)` 的语义会变得模糊，且容易掩盖空指针问题。
这是 JDK 9 的刻意设计。

**⚠️ 一个关键纠正**：`List.of` 不可变 ≠ 线程安全。

不可变对象天然线程安全，是因为**它没有可写状态**，不是因为加了锁。
这两个概念必须分开：
- 不可变 → 没人能改 → 天然安全
- 线程安全 → 可以被多线程同时访问而不出错（不可变是其中一种实现方式，加锁是另一种）

**你说的「滚动数组复用」方案**（预先建一个 `ArrayList`，每次 `clear()` 再填）：

- **`clear()` 需要吗？** 需要。`clear()` 把 `size` 置 0，元素引用置 null（帮助 GC）。
- **生产环境适用吗？** 分场景：
  - ✅ **循环内高频复用**（比如每轮处理一批数据）：值得，能减少 GC 压力。
  - ❌ **跨请求复用**：**绝对不行**。`ArrayList` 不是线程安全的，
    而且被别的代码持有引用时会读到脏数据。
  - ⚠️ 单线程 + 明确的作用域内复用，才安全。

### 5.5 `subList` 是视图，不是拷贝

```java
List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5));
List<Integer> sub = list.subList(1, 4);   // [2, 3, 4]
sub.set(0, 99);
System.out.println(list);                 // [1, 99, 3, 4, 5]  ← 原 list 被改了
```

**它是视图**：`subList` 返回 `ArrayList$SubList`，内部持有 `parent` 引用 + `offset` + `size`，
所有操作都转发给原 list。

**第二个坑：原 list 结构性修改会让 sub 失效**

```java
List<Integer> sub = list.subList(1, 4);
list.add(6);                    // 结构性修改（改变 size）
sub.get(0);                     // ❌ ConcurrentModificationException
```

因为 `SubList` 记录了创建时的 `modCount`，原 list 一改就对不上。

**正确用法**：要一份独立的，就 `new ArrayList<>(list.subList(a, b))`。
**真实场景**：分页。`subList` 做内存分页很方便，但一定要拷贝出来再返回，
否则调用方拿到的是视图，后续任何修改都会穿透到原集合。

### 5.6 `Collectors.toMap` 的 null 与重复 key

```java
Map<String, Integer> map = list.stream()
    .collect(Collectors.toMap(User::getName, User::getAge));
```

**问题 1：key 重复 → `IllegalStateException: Duplicate key`**

修法：给第三个参数（合并函数）

```java
.collect(Collectors.toMap(User::getName, User::getAge, (a, b) -> a))   // 保留先出现的
.collect(Collectors.toMap(User::getName, User::getAge, (a, b) -> b))   // 保留后出现的
.collect(Collectors.toMap(User::getName, User::getAge, Integer::sum))  // 累加
```

**问题 2：value 为 null → `NullPointerException`（而且信息极其误导）**

根因在 `HashMap.merge()`：

```java
public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    if (value == null)
        throw new NullPointerException();     // ← 第一行就炸
    ...
}
```

`Collectors.toMap` 内部就是用 `map.merge()` 实现的，所以**只要有一个 value 是 null，整个收集就失败**。
注意：报错信息里**不会告诉你哪个元素是 null**，排查很痛苦。

**问题 3：key 为 null —— 反而不会炸**

这点很反直觉，我实测过（JDK 17）：

```java
Map<String,Integer> m = Stream.of(new U(null,1), new U("b",2))
    .collect(Collectors.toMap(U::getName, U::getAge));
// → size=2, keys=[null, b]   正常跑完，没有异常
```

原因：`HashMap.merge()` **只判 value，不判 key**；而 `hash(null)` 返回 `0`，
是个合法的桶下标。所以 null key 在 `HashMap` 里是被允许的（只能有一个）。

**真正拒绝 null key 的是 `groupingBy`** —— 见 6.2，两者在这一点上正好相反。

**你说的「有没有 `SetMap`」—— 没有这个东西。**
`Set` 和 `Map` 是两个独立的顶层接口，不存在交集类型。你想表达的应该是
「用 `Map<K, Set<V>>` 来表达一对多」——那个用 `groupingBy` 就行：

```java
Map<String, List<User>> byDept = list.stream()
    .collect(Collectors.groupingBy(User::getDept));
```

**真想用 `toMap` 又怕 null，三种做法**：

```java
// ① 先过滤（最简单，但会丢数据）
list.stream().filter(u -> u.getAge() != null)
    .collect(Collectors.toMap(User::getName, User::getAge));

// ② 用 Optional 包一层（不丢数据，但类型变丑）
list.stream().collect(Collectors.toMap(
    User::getName,
    u -> Optional.ofNullable(u.getAge())));

// ③ 手写收集器（最灵活，能放 null）
Map<String, Integer> map = new HashMap<>();
list.forEach(u -> map.put(u.getName(), u.getAge()));   // ← 老老实实 for 循环
```

**面试时的标准答法**：`toMap` 只卡两件事 ——
**key 不能重复**、**value 不能 null**。key 为 null 反而是允许的（实测过）。

真要宽松语义（value 允许 null），就别用 `toMap`，老老实实写：

```java
Map<String, Integer> map = new HashMap<>();
list.forEach(u -> map.put(u.getName(), u.getAge()));
```

### 5.7 `ConcurrentHashMap` 为什么拒绝 null

`HashMap` 允许 null，`ConcurrentHashMap` 直接 `NullPointerException`。

**根因**：并发下 `get(key)` 返回 null 时，**无法区分两种情况**：

1. key 不存在
2. key 存在，但 value 就是 null

单线程可以用 `containsKey()` 二次确认。但并发下，`containsKey` 和 `get` 之间
状态可能已经被别的线程改了，**二次确认本身不可靠**。

所以 Doug Lea 的选择是：**直接禁止 null，让「返回 null」只有一种含义**。
这是「用接口约束换取语义确定性」的经典案例。

---

## 6. Stream 的两个易错点

### 6.1 `orElse` vs `orElseGet`

```java
// orElse：参数是「值」，无论 Optional 是否为空都会求值
String a = optional.orElse(expensiveMethod());     // ❌ expensiveMethod 一定执行

// orElseGet：参数是 Supplier，只在为空时求值
String b = optional.orElseGet(() -> expensiveMethod());   // ✅ 惰性
```

**什么时候会踩**：`orElse` 里调了个查库方法。即使 Optional 有值，
这次查库也已经发生了——**白查一次，而且日志里看不出来**。

### 6.2 `groupingBy` 的三参数

```java
Collectors.groupingBy(
    classifier,        // ① 分组依据
    mapFactory,        // ② 用什么 Map 装（默认 HashMap，要保序给 LinkedHashMap::new）
    downstream         // ③ 每组内的收集方式（默认 toList）
);
```

**典型用法**：

```java
// 每组计数
Map<String, Long> count = list.stream()
    .collect(Collectors.groupingBy(User::getDept, Collectors.counting()));

// 每组取最大值
Map<String, Optional<User>> oldest = list.stream()
    .collect(Collectors.groupingBy(User::getDept,
        Collectors.maxBy(Comparator.comparing(User::getAge))));

// 保序
Map<String, List<User>> ordered = list.stream()
    .collect(Collectors.groupingBy(User::getDept, LinkedHashMap::new, Collectors.toList()));
```

**⚠️ 这两者对 null 的态度正好相反，是最容易记混的一点（实测于 JDK 17）：**

| | null key | null value |
|---|---|---|
| `toMap` | ✅ **允许**，落到 HashMap 的 0 号桶 | ❌ 裸 NPE（`HashMap.merge` 第一行） |
| `groupingBy` | ❌ NPE，**有明确 message** | ✅ 允许（`toList` 能装 null 元素） |

实测输出：

```
toMap null KEY       -> OK (no exception), size=2, keys=[null, b]
toMap null VALUE     -> NullPointerException: null
toMap dup KEY        -> IllegalStateException: Duplicate key a (attempted merging values 1 and 2)
groupingBy null KEY  -> NullPointerException: element cannot be mapped to a null key
groupingBy null elem -> OK (no exception), size=1, listSize=2
```

**为什么相反**：

- `groupingBy` 显式加了 `Objects.requireNonNull(classifier.apply(t), "element cannot be mapped to a null key")`，
  因为 null key 通常意味着 classifier 有 bug，早点炸比默默产生一个 null 分组好。
- `toMap` 直接委托 `HashMap.merge`，没做额外校验，而 `merge` 只判 value。

**一句话记法**：`toMap` 卡 value，`groupingBy` 卡 key。

---

## 7. 七条反常识（拉开差距的地方）

### ① 队列满了才建非核心线程

见 3.2。给无界队列时，`maximumPoolSize` 是**死参数**。

### ② `@Async` 不等于异步

四种失效场景，一个都不报错：

| 场景 | 原因 |
|---|---|
| **同类内部调用** `this.asyncMethod()` | 不走 Spring 代理，注解不生效 |
| 方法不是 `public` | 代理拦不到 |
| 没加 `@EnableAsync` | 切面根本没注册 |
| **找不到线程池 bean** | 静默退化成 `SimpleAsyncTaskExecutor`（每次新建线程） |

第一条最坑：**你调用的是 `this`，不是代理对象**。修法是把异步方法抽到另一个 Bean，
或者用 `AopContext.currentProxy()`（需要 `@EnableAspectJAutoProxy(exposeProxy = true)`）。

### ③ `synchronized` 锁的是对象，不是代码块

```java
public synchronized void a() { ... }    // 锁的是 this
public static synchronized void b() { ... }  // 锁的是 Class 对象
```

两个方法**锁的不是同一把锁**，所以能并发执行。这是「单例里加 `synchronized` 却没生效」的常见原因。

### ④ 不可变 ≠ 线程安全

见 5.4。`List.of` 安全是因为没人能改，不是因为有锁。

反过来说：**一个对象的所有字段都是 `final`，它才是真正不可变的**。
如果字段是 `final List`，但 list 本身可变，那这个对象**不是**不可变的。

### ⑤ 定时任务默认只有一个线程

`@Scheduled` 的 `poolSize` 默认 1（`application.yml:41` 已改成 4）。
六个任务排队执行，一个慢的全堵。

### ⑥ `CallerRunsPolicy` 会阻塞调用方

见 3.3。它是背压，不是「自动扩容」。用错了会让上游线程被占满。

### ⑦ 线程池不是越大越好

线程数超过核数之后，多出来的线程只是在**抢占 CPU 时间片 + 增加上下文切换**。
上下文切换的代价：保存/恢复寄存器、刷新 TLB、cache 失效，通常几微秒。
线程数上千时，CPU 可能有 30% 的时间在切换而不是干活。

---

## 8. 高频追问 20 问

### 必须秒答（10 问）

1. 线程池七个参数分别是什么？
2. 任务提交后，线程池的处理顺序是什么？（核心 → 队列 → 非核心 → 拒绝）
3. 四种拒绝策略分别是什么？默认哪个？
4. `volatile` 保证什么？不保证什么？
5. `i++` 为什么不是原子的？
6. `HashMap` 默认容量、负载因子、树化阈值？
7. `HashMap` 为什么容量必须是 2 的幂？
8. `ArrayList` 和 `LinkedList` 怎么选？
9. `synchronized` 和 `ReentrantLock` 的区别？
10. `ThreadLocal` 为什么会内存泄漏？怎么避免？

### 可以想一想（10 问）

11. 线程池的核心线程会被回收吗？（默认不会，`allowCoreThreadTimeOut(true)` 可以）
12. 线程池有哪几个状态？流转顺序？（RUNNING → SHUTDOWN → STOP → TIDYING → TERMINATED）
13. `submit()` 和 `execute()` 的区别？（前者返回 `Future`，会吞异常到 `Future` 里；后者直接抛）
14. 线程池里的任务抛异常了，线程会死吗？（不会，`runWorker` 里 catch 住，线程继续复用）
15. `ConcurrentHashMap` 为什么在 JDK 8 之后放弃分段锁？（改成 CAS + `synchronized` 锁单个桶，粒度更细）
16. `CopyOnWriteArrayList` 适合什么场景？（读多写极少，写时复制整个数组）
17. `CompletableFuture` 怎么处理异常？（`exceptionally` / `handle` / `whenComplete`）
18. `synchronized` 锁升级的过程？JDK 17 里偏向锁还在吗？（已废弃，JDK 18 移除）
19. 为什么阿里规范禁止用 `Executors` 创建线程池？（`newFixedThreadPool` 用无界队列 → OOM；`newCachedThreadPool` 的 `maxSize` 是 `Integer.MAX_VALUE` → 无限建线程）
20. 线程池满了，除了拒绝还能怎么办？（`CallerRunsPolicy` 背压 / 扩大队列 / 上游限流 / 降级异步为同步）

---

## 9. 主动暴露的两个不足

### 不足一：`@Async` 的失败只到日志，没有闭环

> 我的异步补偿删除失败后会抛异常，但因为方法返回 `void`，
> 调用方收不到，只进了 `AsyncUncaughtExceptionHandler` 的日志。
> 正确做法是失败写进 Redis 死信队列，让定时任务捞出来重试——
> 我在消息队列那边已经这么做了，异步任务这边还没统一。

**为什么这么说**：既承认了问题，又展示了你知道正确方案，还顺带把 Redis 篇的知识串起来了。

### 不足二：线程池参数是拍的，没压测

> `core=4 / max=8 / queue=200` 是按「4 核 + IO 密集」估的，没有压测数据支撑。
> 真实值应该看三个指标调：队列长度、拒绝次数、任务耗时 P99。
> 项目里异步任务量很小，所以先给了个保守值。

**为什么这么说**：面试官最怕「参数是抄来的还理直气壮」。承认「这是初值、需要压测」反而显得专业。

---

## 10. 5 分钟速记卡

```
【线程池】
7 参数：core / max / keepAlive / unit / queue / factory / handler
流转：核心 → 队列 → 非核心 → 拒绝        ★队列满了才建非核心
默认坑：queue=MAX → 永不扩容、永不拒绝 → OOM
bean 双名：applicationTaskExecutor + taskExecutor
异常：拒绝异常收得到，执行异常收不到（void）

【线程安全】
三要素：原子 / 可见 / 有序
volatile：可见 + 有序，不管原子
DCL 要 volatile：防 new 的三步重排
AtomicBoolean：compareAndSet 才能「检查并设置」
ThreadLocal：key 弱引用、value 强引用 → 必须 remove

【集合】
HashMap：16 / 0.75 / 8（退化 6）
& 取桶：首要理由是防负数（-5 % 8 = -5）
asList：内部类、set 改原数组、int[] 变 List<int[]>
List.of：全不可变 + 拒 null
subList：视图，原 list 一改就 CME
toMap：key 不重、key 不 null、value 不 null
CHM 拒 null：并发下 null 无法区分「不存在」和「值为 null」

【项目锚点】
FileCompensator.java:18        @Async void + throw → 异常黑洞
application.yml:41             scheduling.pool.size: 4（默认 1）
AiTaskConsumer.java:67         单线程消费是故意的，扩容靠消费者组
PsychologicalSupportService:63 AtomicBoolean（lambda + 跨线程可见）
CacheConfig.java:45            ThreadLocalRandom 做 TTL 抖动
AsyncConfig.java:34            bean 双名 + 有界队列 + CallerRunsPolicy
```

---

## 附：和 Redis 篇怎么串起来

面试时这两份文档不是并列关系，是**递进**关系。推荐这个讲法：

> 我做的这个项目是个心理健康系统，核心链路是 AI 咨询 + 情绪记录。
> 一开始所有状态都放 JVM 内存——会话记忆放 `ConcurrentHashMap`、任务队列用 `@Async`。
> 后来发现三个问题：重启就丢、多实例串不上、任务失败了没人知道。
> 所以我做了一轮改造：**能放 Redis 的状态全挪出去**（会话记忆、任务队列、黑名单、排行榜），
> **留在进程内的只有真正的并发控制**（线程池、原子标记、本地锁）。
>
> 这条边界我是这么划的：**需要跨请求、跨实例、跨重启的状态，都归 Redis；
> 只是「这一段代码同时只能有一个人跑」的，归本地锁。**
> 本地锁能做的，不要上分布式锁——多一次网络往返，还多一个故障点。

这段话说出来，面试官会认为你想过「边界在哪」，而不是「哪个技术更新」。

---

## 相关文档

- `Redis面试包装.md` —— 中间件篇，13 个场景分 5 类
- `Redis接入进度.md` —— 每一关的实现细节与踩坑原始记录
