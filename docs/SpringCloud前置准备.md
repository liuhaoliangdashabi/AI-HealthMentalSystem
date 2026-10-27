# Spring Cloud 前置准备

> 查证时间：2026-10-19。版本号会变，动手前建议再核一次官方页面：
> - Spring Cloud 版本对应：https://cloud.spring.io/
> - Spring Cloud Alibaba 版本对应：https://sca.aliyun.com/docs/2025.x/overview/version-explain/

---

## 0. 三个问题，先给直接答案

| 问题 | 答案 |
|---|---|
| Spring Cloud 能直接用 Spring Cloud Alibaba 吗 | **能**，而且国内基本都这么用。但它是「补充」不是「替代」，见第 1 节 |
| 需要下载什么 | **起步只需要 Nacos 一个**。其余按学习进度加，别一次下全套 |
| 该先干什么 | **先做模块化，别急着拆服务**。见第 3 节 |

---

## 1. 先把关系理清：SCA 不是替代品

很多人以为「用了 Spring Cloud Alibaba 就不用 Spring Cloud 了」。不是。

**Spring Cloud 是标准，Spring Cloud Alibaba 是符合这个标准的一套实现（外加几个阿里特有组件）。**

| 能力 | 官方 Spring Cloud | Spring Cloud Alibaba |
|---|---|---|
| 服务注册发现 | Eureka / Consul / Zookeeper | **Nacos** |
| 配置中心 | Spring Cloud Config | **Nacos Config** |
| 远程调用 | **OpenFeign** | —— |
| 负载均衡 | **Spring Cloud LoadBalancer** | —— |
| 网关 | **Spring Cloud Gateway** | —— |
| 熔断限流 | CircuitBreaker 抽象 + Resilience4j | **Sentinel** |
| 分布式事务 | —— | **Seata** |
| 消息队列 | Spring Cloud Stream（抽象） | **RocketMQ** |

**加粗的是你会实际用到的。** 所以你的项目里会同时出现两套依赖：

```xml
<!-- 官方的：管远程调用、网关、负载均衡 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- 阿里的：管注册中心、配置、流控、事务、消息 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-dependencies</artifactId>
    <version>2025.0.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**为什么国内选 SCA**：

- Netflix 那套（Eureka 2.x、Hystrix、Ribbon）大部分已停止开发或进入维护模式
- Nacos 一个组件同时解决「注册中心 + 配置中心」，比 Eureka + Config 两套省事
- Sentinel 有中文控制台，流控规则可视化配置，比 Resilience4j 直观
- 文档和社区中文资料多，出问题好搜

**注意**：SCA 的版本号是四段（`2025.0.0.0`），比 Spring Cloud 多一位，别写成三段。

### 1.1 「Spring Cloud 是规范」这句话只对了一半

说它是「规范」容易误导，因为规范通常指「只有接口、没有实现」（比如 JDBC 的
`java.sql.Connection`，Sun 只定义方法签名，驱动厂商来写实现）。

Spring Cloud 实际是三层混在一起的东西：

| 层 | 是什么 | 例子 |
|---|---|---|
| 抽象层 | 接口 + 数据模型，不含任何实现 | `DiscoveryClient`、`LoadBalancerClient`、`ServiceInstance`、`@LoadBalanced` |
| 官方实现 | Spring 团队自己写的，不依赖任何厂商 | Gateway、OpenFeign、Spring Cloud LoadBalancer |
| 第三方适配 | 把各家中间件接到抽象层上 | `NacosDiscoveryClient`、`EurekaDiscoveryClient`、`ConsulDiscoveryClient` |

所以它既不是纯接口，也不是纯实现。最贴切的类比是 Java 集合：

```
DiscoveryClient（接口）  ←→  List（接口）
NacosDiscoveryClient     ←→  ArrayList（实现）
```

`spring-cloud-commons` 里那个 `DiscoveryClient` 接口长这样（简化）：

```java
public interface DiscoveryClient extends Ordered {
    String description();
    List<ServiceInstance> getInstances(String serviceId);   // 这个服务有哪些实例
    List<String> getServices();                             // 现在有哪些服务
}
```

Nacos 的实现类 `NacosDiscoveryClient` 里，`getInstances()` 就是去调 Nacos 的
`/nacos/v1/ns/instance/list` 接口。**换了注册中心，换的只是这一个实现类，
`@LoadBalanced RestTemplate`、Feign 这些上层代码一行都不用改。** 这才是抽象层的价值。

### 1.2 为什么「只导坐标」不行

因为 `spring-cloud-dependencies` 和 `spring-cloud-alibaba-dependencies` 的
`<packaging>` 都是 **`pom`**，打开看只有一个 `<dependencyManagement>` 块，
**一行 Java 代码都没有**。

导完 BOM 后执行：

```bash
mvn dependency:tree | grep -c spring-cloud
# 0
```

一个 jar 都没多。BOM 唯一的作用是「**登记版本号**」，让你后面写 starter 时不用写 `<version>`。

真正把代码拉进来的是 starter。三个层次要分清：

```xml
<!-- 第一层：BOM，只登记版本，零代码 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-dependencies</artifactId>
    <version>2025.0.0.0</version>
    <type>pom</type>          <!-- 关键：type 是 pom -->
    <scope>import</scope>     <!-- 关键：scope 是 import -->
</dependency>

<!-- 第二层：starter，真正拉代码，不用写 version（BOM 管了） -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>

<!-- 第三层：starter 自己带进来的传递依赖，你不用管 -->
<!-- nacos-client / spring-cloud-commons / spring-cloud-context ... -->
```

这个模式你项目里已经有了：`spring-boot-starter-parent` 是父 POM（也管版本），
`spring-boot-starter-data-redis` 才是真正拉 jar 的那个。同一个道理。

**一句话记住**：BOM 管版本，starter 管功能。只导 BOM = 只买了目录，没买书。

---

## 2. 版本对应：这是第一个坑，也是最大的坑

**Spring Boot 每升一个大版本，Spring Cloud 就得换一条 release train。** 版本错位的典型症状：
能编译通过、能启动，但运行时报 `NoSuchMethodError` / `ClassNotFoundException`，
或者配置项静默失效（比如 `bootstrap.yml` 不生效）。

### 2.1 Spring Cloud 官方对应表

| Spring Cloud | 代号 | 对应 Spring Boot |
|---|---|---|
| 2025.1.x | Oakwood | 4.0.x、4.1.x（从 2025.1.2 起） |
| **2025.0.x** | **Northfields** | **3.5.x** |
| 2024.0.x | Moorgate | 3.4.x |
| 2023.0.x | Leyton | 3.2.x、3.3.x（从 2023.0.2 起） |

### 2.2 Spring Cloud Alibaba 对应表

| SCA | Spring Cloud | Spring Boot |
|---|---|---|
| 2025.1.0.0 | 2025.1.0 | 4.0.0 |
| **2025.0.0.0** | **2025.0.0** | **3.5.0** |

### 2.3 你的项目该用哪套

你现在的 `pom.xml`：**Spring Boot 3.5.15 + Java 17**。

所以锁定：

```
Spring Boot         3.5.15      ← 不动
Spring Cloud        2025.0.x    ← Northfields
Spring Cloud Alibaba 2025.0.0.0
```

配套的中间件版本（SCA 2025.0.0.0 官方配套）：

| 组件 | 版本 | 本地要不要装 |
|---|---|---|
| Nacos | 3.0.3 | 要 |
| Sentinel | 1.8.9 | 要（starter + Dashboard 两个东西，见 3.1） |
| RocketMQ | 5.3.1 | 要 |
| Seata | 2.5.0 | 要，但最后再装 |
| SchedulerX | 1.13.1 | **不要**，阿里云商业产品，本地跑不了 |

SCA 官方配套表里还有第五个组件 `SchedulerX 1.13.1`（分布式任务调度）。
它是阿里云上的付费服务，客户端连的是云上的接入点，**本地没有开源版本可以起**。
定时任务你项目里用 `@Scheduled` 就够了，暂时不用管它。

**⚠️ 不要自己「升一个版本试试」。** Spring Cloud 的组件之间有严格的兼容矩阵，
单独升一个（比如把 Nacos client 升到最新）很容易踩到序列化不兼容、配置项改名这类坑。
**永远用 BOM 锁版本，不要手动写 `<version>`。**

---

## 3. 需要下载什么

### 3.1 分三档，按学习进度来

**第一档：起步只要一个（先只装这个）**

| 组件 | 版本 | 干什么 | 怎么装 |
|---|---|---|---|
| **Nacos** | 3.0.3 | 注册中心 + 配置中心 | 下 zip 解压，`startup.cmd -m standalone` |

控制台：http://localhost:8848/nacos

**这一档就够你把「服务注册 + 服务发现 + 配置中心」全跑通了。** 别急着下别的。

**第二档：学流控和消息时再装**

| 组件 | 版本 | 干什么 | 怎么装 |
|---|---|---|---|
| Sentinel Dashboard | 1.8.9 | 流控 / 熔断规则的可视化控制台 | 一个 jar，`java -jar sentinel-dashboard.jar` |
| RocketMQ | 5.3.1 | 消息队列 | 下 zip，先起 NameServer 再起 Broker |

Sentinel Dashboard 默认端口 8080 —— **和你项目的 8080 冲突**，启动时加 `--server.port=8858`。

**第三档：分布式事务（最后再碰，别一开始就上）**

| 组件 | 版本 | 干什么 |
|---|---|---|
| Seata | 2.5.0 | 分布式事务（AT / TCC / SAGA 模式） |

Seata 要额外建表、改配置、跑 TC 服务，是这几样里最重的。**等你有跨服务的写操作了再说。**

### 3.2 一个更省事的方案：Docker

如果装 Docker Desktop（Windows 需要 WSL2），一条命令起全套：

```yaml
# docker-compose.yml
services:
  nacos:
    image: nacos/nacos-server:v3.0.3
    environment:
      MODE: standalone
    ports:
      - "8848:8848"
      - "9848:9848"

  sentinel:
    image: bladex/sentinel-dashboard:1.8.9
    ports:
      - "8858:8858"

  rocketmq-namesrv:
    image: apache/rocketmq:5.3.1
    command: sh mqnamesrv
    ports:
      - "9876:9876"
```

**好处**：不用管 JDK 版本、不用配环境变量、删起来干净（`docker compose down -v`）。

**代价**：Docker Desktop 本身占内存（2~4G），WSL2 首次配置有点折腾。

**建议**：如果你已经装了 Docker，用它。没装的话，Nacos 下 zip 解压更直接，5 分钟能跑起来。

### 3.3 不用下载的东西

| 东西 | 为什么不用 |
|---|---|
| Eureka | Nacos 已覆盖，且 Netflix 那套已停止开发 |
| Spring Cloud Config Server | Nacos Config 已覆盖 |
| Hystrix | 已停止开发，被 Resilience4j / Sentinel 取代 |
| Ribbon | 已停止维护，被 Spring Cloud LoadBalancer 取代（你导的 BOM 里根本没有它） |
| Zookeeper | Nacos 不需要它；RocketMQ 5.x 也不需要（4.x 之前才要） |
| RabbitMQ / Kafka | 除非你有明确场景，否则先学 RocketMQ（SCA 配套） |
| SchedulerX | 阿里云商业产品，本地没有开源版，定时任务用 `@Scheduled` 就够 |

---

## 4. 比下载更重要的事：先做模块化

**这是整份文档最该看的一节。**

很多人学微服务的方式是：装一堆中间件 → 把单体拆成 8 个服务 → 发现到处是分布式事务、
跨服务查询、数据一致性，然后崩溃。

**微服务不是「技术选型」，是「组织问题」。** 拆之前先问：我这个项目**哪里疼**？

你现在这个项目（心理健康系统）的痛点其实很清楚：

| 痛点 | 现在怎么解决的 | 拆成微服务后变成什么问题 |
|---|---|---|
| AI 分析慢（5~10 秒） | Redis Stream 异步队列 | 跨服务的消息投递、幂等、死信 |
| 会话记忆在 JVM 堆里 | `MessageWindowChatMemory` | **多实例后彻底失效**，必须外置 |
| 浏览量并发写 | Redis INCR + 定时回写 | 回写任务只能有一个实例跑 → 分布式锁 |
| 管理端看板全表扫描 | `@Cacheable` | 缓存一致性、跨服务数据聚合 |
| 文件上传 | 本地 OSS | 独立文件服务 |

**这些痛点才是拆分的理由。** 没有痛点的模块，拆了只是增加复杂度。

### 4.1 拆之前要做的四件事

**① 按领域分包，先把边界画出来**

现在你的包是按技术分的（`controller` / `service` / `mapper`）。改成按业务分：

```
com.example.health
├── user/          (用户、认证、权限)
├── content/       (知识库、文章、分类)
├── emotion/       (情绪日记、分析)
├── chat/          (AI 咨询、会话)
├── file/          (上传、OSS)
└── common/        (公共 DTO、工具、异常)
```

每个包内部自带 `controller/service/mapper`。**这一步不需要任何中间件，纯重构。**

**② 禁止跨模块直接查表**

现在 `DiaryService` 可能直接查 `user` 表拿昵称。拆服务后这张表在另一个库里，查不到了。

改成：**只通过对方的 Service 接口拿数据**。

```java
// ❌ 拆服务后会失效
User user = userMapper.selectById(userId);

// ✅ 拆服务后改成 Feign 调用，接口不变
UserDTO user = userService.getUserById(userId);
```

**这是最重要的一步。** 做完这一步，拆服务就只是「把 Service 实现换成 HTTP 调用」，
而不是「重写业务逻辑」。

**③ 配置外置**

现在 `application.yml` 里写死的东西（数据库地址、API key、OSS 配置），
拆服务后每个服务一份配置，改一次要改 N 个文件。这就是 Nacos Config 存在的理由。

**④ DTO 不暴露 entity**

跨服务传输的必须是 DTO，不能是 entity。原因：
- entity 带 `password`、`deleted` 这类不该传的字段
- 数据库改了字段，所有调用方都受影响

你现在已经有 `DTO/response/` 目录了，这一步基础是好的。

### 4.2 一个判断标准

**如果两个模块之间，每次改需求都要同时改两边，它们就不该拆。**

比如「情绪日记」和「情绪分析」—— 分析结果就是日记的一个字段，拆开只会增加一次网络调用。
但「用户」和「内容」—— 内容作者信息变了，不需要改用户模块，这就适合拆。

---

## 5. 学习路线（按依赖顺序，别跳）

| 阶段 | 学什么 | 对应组件 | 前置 |
|---|---|---|---|
| 0 | 模块化重构 | 无 | 现在就能做 |
| 1 | 服务注册与发现 | Nacos | 阶段 0 |
| 2 | 远程调用 | OpenFeign | 阶段 1 |
| 3 | 负载均衡 | LoadBalancer | 阶段 2（起两个实例才有意义） |
| 4 | 统一网关 | Gateway | 阶段 2 |
| 5 | 配置中心 | Nacos Config | 阶段 1 |
| 6 | 流控熔断 | Sentinel | 阶段 2 |
| 7 | 消息队列 | RocketMQ | 阶段 2 |
| 8 | 分布式事务 | Seata | 阶段 7（最难，最后） |

**为什么是这个顺序**：

- 没有注册中心，Feign 不知道调谁 → 1 必须在 2 前
- 只有一个实例时，负载均衡看不出效果 → 3 要在 2 之后，且要起两个实例
- 网关是「统一入口」，得先有服务才能路由 → 4 在 2 后
- 配置中心可以和注册中心一起学（同一个 Nacos）
- 流控和消息都建立在「能调用」的基础上 → 6、7 在 2 后
- 分布式事务只在「跨服务写」时才有意义 → 最后

**每一阶段的最小验证标准**：

| 阶段 | 怎么算学会了 |
|---|---|
| 1 | 两个服务启动后，Nacos 控制台能看到它们，且能互相调用到 |
| 2 | 用 `@FeignClient` 调通一个接口，返回值正确 |
| 3 | 起两个实例，请求轮流打到两边（看日志） |
| 4 | 通过网关端口访问后端服务，路由规则生效 |
| 5 | 在 Nacos 控制台改配置，不重启服务，`@RefreshScope` 的字段变了 |
| 6 | 配一条 QPS=2 的规则，快速请求返回 429 / 限流提示 |
| 7 | 生产者发消息，消费者收到；消费失败能重试 |
| 8 | 两个服务各写一个库，一个失败时另一个回滚 |

---

## 6. 和你现有项目的衔接点（最有价值的部分）

你这一路做的 Redis 场景，**几乎每一个在微服务里都有对应的「升级版」**。对照着学，理解会快很多。

| 你现在有的（Redis） | 微服务里的对应物 | 差别在哪 |
|---|---|---|
| ZSet 滑动窗口限流（`UserService`） | **Sentinel 流控规则** | 从「手写算法」变成「配置化 + 控制台可视化」 |
| Redis Stream 任务队列（`AiTaskConsumer`） | **RocketMQ** | 从「自己维护 PEL / 死信 / claim」变成「MQ 自带重试和死信队列」 |
| `SET NX PX` 分布式锁（`FileCleanupTask`） | **Seata** 或 Redisson | 锁解决「同时跑」，事务解决「同时成功或失败」，不是一回事 |
| `@Cacheable` 注解缓存 | 缓存一致性变难 | 多实例后本地缓存不可靠，必须走 Redis |
| 在线人数 ZSet | 网关层统计更自然 | 网关是所有请求的入口 |
| `MessageWindowChatMemory`（JVM 堆） | **必须外置** | 多实例后会话串不上，这是真痛点 |

**特别提醒**：`ChatMemory` 那个问题，你现在单实例感觉不到，一旦起两个实例就立刻暴露 ——
用户发消息被负载均衡到 A 实例，下一条到了 B 实例，B 没有上下文，AI 会「失忆」。

**这不是「以后再说」的问题，是拆服务前必须先解决的。** 把 ChatMemory 落到 Redis，
既是 bug 修复，也是微服务化的前置条件。

---

## 7. 今天就能做的三件事

**① 装 Nacos，跑起来（约 10 分钟）**

```bash
# 下载 nacos-server-3.0.3.zip，解压后
cd nacos/bin
startup.cmd -m standalone     # Windows
# 或 sh startup.sh -m standalone
```

浏览器打开 http://localhost:8848/nacos，能登录就算成功。

**② 建一个空 demo 项目，注册上去（约 30 分钟）**

不用改你现有的项目。新建一个最小 Spring Boot 项目，加两个依赖：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
```

配置：

```yaml
spring:
  application:
    name: demo-service
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
```

启动后去 Nacos 控制台的「服务列表」看，能看到 `demo-service` 就成了。

**③ 把现有项目按领域重新分包（不用改逻辑，纯移动文件）**

这一步不需要任何中间件，今天就能做，而且收益最大。

---

## 8. 常见坑（提前知道能省一天）

| 坑 | 症状 | 解法 |
|---|---|---|
| **版本不匹配** | 启动报 `NoSuchMethodError`、`ClassNotFoundException` | 严格按第 2 节的表，用 BOM 锁版本 |
| **`bootstrap.yml` 不生效** | 配置中心拉不到配置 | Spring Cloud 2020+ 默认关了 bootstrap，要加 `spring-cloud-starter-bootstrap` 依赖 |
| **Sentinel 端口冲突** | 起不来，提示 8080 被占用 | `java -jar sentinel-dashboard.jar --server.port=8858` |
| **Nacos 3.x 鉴权** | 控制台/客户端 403 | Nacos 3.x 默认开了鉴权，学习阶段可以在配置里关掉，或按官方 quickstart 配 token |
| **服务注册上了但调不通** | Feign 报连接超时 | 检查 `server.port` 是否被注册成了容器内端口；本地开发直接用 IP |
| **循环依赖** | 启动报 `BeanCurrentlyInCreationException` | 微服务里出现循环调用，说明服务边界划错了，该合并 |
| **一个实例改了代码，另一个没改** | 行为不一致 | 本地调试时只起一个实例，或者用不同端口但同一份代码 |

---

## 附：一张图记住整体结构

```
                      ┌─────────────┐
   浏览器 ──────────▶ │   Gateway   │  ← 统一入口、鉴权、路由
                      └──────┬──────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │  user    │   │ content  │   │  chat    │
        └────┬─────┘   └────┬─────┘   └────┬─────┘
             │              │              │
             └──────────────┼──────────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │  Nacos   │  │  Redis   │  │ RocketMQ │
        │ 注册+配置 │  │  缓存    │  │  消息    │
        └──────────┘  └──────────┘  └──────────┘

        Sentinel 挂在每个服务上（流控/熔断）
        Seata 横跨多个服务（分布式事务）
```

**记住：Gateway 是入口，Nacos 是通讯录，Sentinel 是保险丝，Seata 是账本。**
