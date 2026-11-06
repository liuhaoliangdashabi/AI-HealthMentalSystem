package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.DiaryStatDTO;
import org.springframework.beans.factory.annotation.Value;
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
            log.error("调diary-service取统计失败，看板数字偏小，去检查8030是否在跑",e);
            return DiaryStatDTO.empty();
        }
    }
}
