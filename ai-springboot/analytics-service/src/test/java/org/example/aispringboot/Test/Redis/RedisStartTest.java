package org.example.aispringboot.Test.Redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@SpringBootTest
public class RedisStartTest {
    @Autowired
    private StringRedisTemplate stringRedis;
    @Autowired
    private RedisTemplate<String,Object> redis;

    public static class DemoUser{
        public String name;
        public Integer age;
        public LocalDateTime createdAt;
        public DemoUser(){}
        public DemoUser(String name,Integer age){this.name=name;this.age=age;this.createdAt=LocalDateTime.now();}
    }
    @Test
    void stringTemplate_cunquzhi(){
        stringRedis.opsForValue().set("demo:string","张三");
        assertEquals("张三",stringRedis.opsForValue().get("demo:string"));
    }
    @Test
    void redisTemplate_objectToJson_daihuanyuan(){
        redis.opsForValue().set("demo:user",new DemoUser("张三",18));
        Object got=redis.opsForValue().get("demo:user");
        assertNotNull(got);
        assertEquals(DemoUser.class,got.getClass());
        assertEquals("张三",((DemoUser)got).name);
    }
}
