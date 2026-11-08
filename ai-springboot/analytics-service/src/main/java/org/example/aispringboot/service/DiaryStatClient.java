package org.example.aispringboot.service;

import com.alibaba.csp.sentinel.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.DiaryStatDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Component
public class DiaryStatClient {
    private final RestClient rest;

    public DiaryStatClient(RestClient.Builder builder,
                           @Value("${internal.diary-service.url:http://diary-service}")String baseUrl) {
        this.rest=builder.baseUrl(baseUrl).build();
    }

    /**
     * 缓存名 stat，TTL 60 秒（见 CacheConfig）。分片缓存的 key 必须带 days，
     * 否则 7 天和 30 天的统计会串。
     * unless 是命门：降级返回的空壳（degraded=true）绝不能写进缓存，
     * 否则服务恢复后得等 TTL 过期才看得见真数字。
     */
    @Cacheable(cacheNames = "stat", key = "'diary:' + #days", unless = "#result.degraded")
    public DiaryStatDTO fetch(int days){
        try{
            DiaryStatDTO dto=rest.get()
                    .uri(uriBuilder ->
                            uriBuilder.path("/internal/diary/stats/overview")
                                    .queryParam("days",days).build())
                    .retrieve()
                    .body(DiaryStatDTO.class);
            return dto==null?DiaryStatDTO.empty():dto;
        }catch (Exception e){
            Tracer.trace(e);
            log.error("调diary-service取统计失败，看板数字偏小，去检查8030是否在跑",e);
            return DiaryStatDTO.empty();
        }
    }
}
