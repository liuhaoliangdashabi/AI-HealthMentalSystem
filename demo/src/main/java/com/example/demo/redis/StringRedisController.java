package com.example.demo.redis;

import io.micrometer.common.util.StringUtils;
import io.netty.util.internal.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * 第 1 课：把你在 redis-cli 里敲过的命令，逐条翻译成 Java。
 *
 * <p>做法：把下面的 TODO 1~6 填上，启动项目，然后一个个调 URL。
 * 每道题上面都写了等价的 CLI 命令，照着翻就行 —— 你已经会命令了，
 * 这课要学的只是「命令对应哪个 Java 方法」。
 *
 * <p>调完一个，就打开 redis-cli 敲对应的命令，看看是不是同一份数据。
 * 这一步很重要：能亲眼确认 Java 写的和你手敲的是同一个东西。
 */
@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class StringRedisController {

    // 已经帮你注入了，不用自己 new。
    //
    // 为什么用 StringRedisTemplate 而不是 RedisTemplate：
    // 它的 key 和 value 都用纯字符串存，所以你在 redis-cli 里 GET 出来
    // 能直接看懂。RedisTemplate 默认用 JDK 序列化，存进去的东西在
    // redis-cli 里会变成一堆乱码 —— 这是第 3 课的内容，先记住结论。
    private final StringRedisTemplate redis;

    /**
     * 连通性自检，这个我写好了。
     * <p>CLI: {@code PING}
     * <p>URL: {@code http://localhost:8080/redis/ping}
     * <p>访问前记得确认你的 Redis 8.10.2 还开着。
     */
    @GetMapping("/ping")
    public String ping() {
        // 这里必须写 (RedisCallback<String>) 这个强制转换。
        // 因为 RedisTemplate.execute() 有两个重载：execute(RedisCallback) 和
        // execute(SessionCallback)，光写 lambda 编译器推断不出你要哪个，会报
        // "execute 的调用不明确"。这是用 execute() 时的常见坑，记住就好。
        return redis.execute((RedisCallback<String>) connection -> connection.ping());
    }

    // ==================== 以下 6 题归你填 ====================

    /**
     * 【TODO 1】写入一个字符串。
     * <p>CLI: {@code SET name tom}
     * <p>URL: {@code POST /redis/set?key=name&value=tom}
     * <p>提示: {@code redis.opsForValue().set(key, value)} —— opsForValue() 拿到的
     * 就是「操作字符串」的那组命令。
     * <p>要求: 写完之后再读一次并返回，证明真的写进去了。
     */
    @PostMapping("/set")
    public String set(@RequestParam String key, @RequestParam String value) {
        // TODO 1 在这里写
        redis.opsForValue().set(key,value);
        return redis.opsForValue().get(key);
    }

    /**
     * 【TODO 2】写入并设置过期时间。
     * <p>CLI: {@code SET code 1234 EX 60}
     * <p>URL: {@code POST /redis/setex?key=code&value=1234&seconds=60}
     * <p>提示: set 有重载版本，key、value 之后第三个参数是 {@code java.time.Duration}，
     * 用 {@code Duration.ofSeconds(seconds)} 构造。
     * <p>要求: 返回 "剩余秒数 = " + {@code redis.getExpire(key)}，
     * 然后你会发现返回值是 60 而不是 0 —— 想想为什么。
     */
    @PostMapping("/setex")
    public String setex(@RequestParam String key,
                        @RequestParam String value,
                        @RequestParam(defaultValue = "60") long seconds) {
        // TODO 2 在这里写
        //刚设，返回值不久应该是60吗，难道该是0
        Duration a=Duration.ofSeconds(seconds);
        redis.opsForValue().set(key,value,a);
        return "剩余秒数 = " + redis.getExpire(key);
    }

    /**
     * 【TODO 3】读取字符串。
     * <p>CLI: {@code GET name}
     * <p>URL: {@code GET /redis/get?key=name}
     * <p>提示: key 不存在时返回 null，不是空字符串 —— 这一点和 {@code EXISTS} 配合时要注意。
     */
    @GetMapping("/get")
    public String get(@RequestParam String key) {
        // TODO 3 在这里写
        String res=redis.opsForValue().get(key);
        if(StringUtils.isBlank(res))return null;
        return res;
    }

    /**
     * 【TODO 4】删除 key。
     * <p>CLI: {@code DEL name}
     * <p>URL: {@code DELETE /redis/del?key=name}
     * <p>提示: {@code redis.delete(key)} 返回的是 Long（删掉了几个），
     * 不是 boolean。删一个不存在的 key 返回 0，而且不报错。
     * <p>要求: 返回 "删除了 N 个 key"。
     */
    @DeleteMapping("/del")
    public String del(@RequestParam String key) {
        // TODO 4 在这里写
        //这好像是个坑点，因为有重载，我不知道有没有别的字段转Collection的好方法，这么写了
        Long count=redis.delete(Collections.singletonList(key));
        return "删除了 "+count+" 个key";
    }

    /**
     * 【TODO 5】自增计数。
     * <p>CLI: {@code INCRBY count 5}
     * <p>URL: {@code POST /redis/incr?key=count&step=5}
     * <p>提示: {@code redis.opsForValue().increment(key, step)}，
     * 它的返回值直接就是自增之后的新值，不用再 GET 一次。
     * <p>想一想: 为什么这个操作天生适合做浏览量、点赞数？
     * （关键词：原子性。GET 出来 +1 再 SET 回去，两个人同时点就会丢数据。）
     */
    @PostMapping("/incr")
    public Long incr(@RequestParam String key, @RequestParam(defaultValue = "1") long step) {
        // TODO 5 在这里写
        //为什么？这个是个什么乐观锁吗？
        return redis.opsForValue().increment(key,step);
    }

    /**
     * 【TODO 6】尝试加锁，这是分布式锁的地基。
     * <p>CLI: {@code SET lock 1 NX EX 30}  —— 注意 NX 和 EX 是同时给的，这一条命令必须原子
     * <p>URL: {@code POST /redis/lock?key=lock&seconds=30}
     * <p>提示: {@code redis.opsForValue().setIfAbsent(key, value, Duration)}
     * 就是 SETNX + EX 的合体。key 不存在时写入成功返回 true（抢到锁），
     * 已存在则返回 false，并且**不覆盖**原来的值。
     * <p>要求: 返回 true 时返回 "抢到锁了"，否则 "没抢到，别人占着"。
     * <p>小心: setIfAbsent 返回的是包装类型 {@code Boolean}，可能是 null，
     * 直接拿去 if 判断会抛空指针，要用 {@code Boolean.TRUE.equals(result)}。
     * <p>验证: 先在 redis-cli 里敲一次同样的命令占住 lock，
     * 再来调这个接口，看是不是返回"没抢到"。
     */
    @PostMapping("/lock")
    public String tryLock(@RequestParam String key,
                          @RequestParam(defaultValue = "30") long seconds) {

        // 第一步：生成一个唯一的 token，作为"锁的持有者标识"。
        // 为什么不写死 "1"：释放锁时要靠它确认"这锁是我的"，写死了就没法区分。
        // 场景：A 抢到锁但卡了 31 秒 → 锁过期 → B 抢到 → A 醒来执行删除，
        //       如果 value 都是 "1"，A 就把 B 的锁删了。用 UUID 才能防住。
        String token = UUID.randomUUID().toString();

        // 第二步：SET key token NX EX seconds —— 一条命令，服务端原子完成。
        // setIfAbsent 就是 SETNX 的 Java 形态：
        //   key 不存在 → 写入成功，返回 true  → 我抢到了
        //   key 已存在 → 什么都不做，返回 false → 别人占着（注意：不覆盖原有的值！）
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(seconds));

        // 第三步：判断结果。
        // 为什么用 Boolean.TRUE.equals(ok) 而不是 ok == true 或 if (ok)？
        // 因为 ok 是包装类型 Boolean，理论上可能是 null（比如连接异常时），
        // 一旦是 null，if (ok) 会拆箱 → NullPointerException。
        // Boolean.TRUE.equals() 是 null-safe 的：null 时安全地返回 false。
        if (Boolean.TRUE.equals(ok)) {
            return "抢到锁了，token = " + token;
        }
        return "没抢到，别人占着";
    }
}
