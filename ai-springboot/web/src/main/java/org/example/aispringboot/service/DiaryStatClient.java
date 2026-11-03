package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.DiaryStatDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DiaryStatClient {
    private final RestClient rest= RestClient.create();

    @Value("${internal.diary-service.url: http://127.0.0.1:8030}")
    private String baseUrl;

    public DiaryStatDTO fetch(int days){
        try{
            DiaryStatDTO dto=rest.get()
                    .uri(baseUrl+"/internal/diary/stats/overview?days="+days)
                    .retrieve()
                    .body(DiaryStatDTO.class);
            return dto==null?DiaryStatDTO.empty():dto;
        }catch (Exception e){
            log.error("调diary-service取统计失败，看板数字偏小，去检查8030是否在跑",e);
            return DiaryStatDTO.empty();
        }
    }
}
