package org.example.aispringboot.config.redisconfig;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 第 1 关 · 场景B：注解流缓存（@Cacheable / @CacheEvict）
 *
 * 这里只做「配置」，注解写在 KnowledgeArticleService 上。
 */
@Slf4j
@Configuration
@EnableCaching   // ★ 没有它，所有 @Cacheable/@CacheEvict 全是【装饰品】，一个都不生效
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {

        // ★ 又是它：裸 ObjectMapper 不挂 JavaTimeModule，带 LocalDateTime 的 DTO 【写的时候就抛】
        //   跟 RedisConfig 里那个坑一模一样 —— 只是这次在「缓存抽象」这条线上
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer();
        json.configure(mapper -> {
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        });

        RedisCacheConfiguration cfg = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30+ThreadLocalRandom.current().nextInt(10)))
                // key 用纯字符串：redis-cli 里看到的是 article::42 这种，肉眼可读
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // value 用 JSON：ArticleResponseDTO 是【对象】，必须能还原类型
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(json));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(cfg)
                //★ 按 cacheName 单独配 TTL。全局默认是 30~40 分钟，那是给「文章详情」这种
                //  改一次、看很久的数据用的。但不同数据的"可容忍陈旧度"根本不一样：
                //
                //  analytics：管理端看板。运营要看到"今天新增了多少"，60 秒的延迟无感，
                //             30 分钟的延迟会让人以为系统坏了。宁可多回源几次。
                //  user     ：用户信息。改昵称/换头像必须立刻生效 —— 但它靠 @CacheEvict
                //             保证，不靠 TTL，所以沿用全局 30 分钟没问题。
                //
                //  教训：一个全局 TTL 套所有缓存，最后一定是"有的太短白回源、有的太长像 bug"。
                .withCacheConfiguration("analytics",
                        cfg.entryTtl(Duration.ofSeconds(60)))
                .build();
    }

    @Nullable
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler(){
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("注解流缓存读失败，按 miss 处理并回源查库。cache={}, key={}", cache.getName(), key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, @Nullable Object value) {
                log.warn("注解流缓存写失败，本次不写缓存，下次请求仍会回源。cache={}, {}={}", cache.getName(), key,value, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.error("注解流缓存删除失败，数据已改但缓存未失效，TTL 内可能读到旧值。cache={}, key={}", cache.getName(), key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.error("注解流缓存清空失败，整片缓存未失效，TTL 内可能读到旧值。cache={}", cache.getName(), e);
            }
        };
    }



}
