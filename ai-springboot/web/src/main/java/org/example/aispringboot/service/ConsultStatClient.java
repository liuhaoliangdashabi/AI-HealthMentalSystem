package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.ConsultStatDTO;
import org.example.aispringboot.DTO.response.DiaryStatDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ConsultStatClient {
    private final RestClient rest= RestClient.create();

    @Value("${internal.consult-service.url: http://127.0.0.1:8040}")
    private String baseUrl;

    public ConsultStatDTO fetch(int days){
        try{
            ConsultStatDTO dto=rest.get()
                    .uri(baseUrl+"/internal/consult/stat/overview?days="+days)
                    .retrieve()
                    .body(ConsultStatDTO.class);
            return dto==null?ConsultStatDTO.empty():dto;
        }catch (Exception e){
            log.error("调consult-service取统计失败，看板数字偏小，去检查8040是否在跑",e);
            return ConsultStatDTO.empty();
        }
    }
}
