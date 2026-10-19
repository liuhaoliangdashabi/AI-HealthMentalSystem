package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.util.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 在线用户统计。ZSet 存 userId -> 最后活跃时间戳，统计前先清掉超时的成员。
 */
@Slf4j
@Service
public class OnlineUserService {

    private final StringRedisTemplate redis;

    public OnlineUserService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 打一次心跳，在 JwtAuthticationFilter 认证通过后调用。异常直接吞掉，不影响业务请求。 */
    public void touch(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.opsForZSet().add(
                    RedisKeys.ONLINE_USERS,
                    String.valueOf(userId),
                    System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("在线心跳写入失败，不影响本次请求，userId={}", userId, e);
        }
    }

    /** 当前在线人数。Redis 读失败时返回 -1，与 0（真的没人）区分开。 */
    public long count() {
        try {
            long deadline = System.currentTimeMillis() - RedisKeys.ONLINE_TIMEOUT.toMillis();
            //先清超时的再数，顺序不能反
            redis.opsForZSet().removeRangeByScore(RedisKeys.ONLINE_USERS, 0, deadline);
            Long n = redis.opsForZSet().zCard(RedisKeys.ONLINE_USERS);
            return n == null ? 0L : n;
        } catch (Exception e) {
            log.warn("统计在线人数失败，返回 -1 表示未知", e);
            return -1L;
        }
    }
}
