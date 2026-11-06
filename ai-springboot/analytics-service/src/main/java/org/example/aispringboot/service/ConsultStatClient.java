package org.example.aispringboot.service;

import com.alibaba.csp.sentinel.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.ConsultStatDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ConsultStatClient {
    private final RestClient rest;

    public ConsultStatClient(RestClient.Builder builder,
                             @Value("${internal.consult-service.url:http://consult-service}")String baseUrl) {
        this.rest=builder.baseUrl(baseUrl).build();

    }

    public ConsultStatDTO fetch(int days){
        try{
            ConsultStatDTO dto=rest.get()
                    .uri(uriBuilder ->
                        uriBuilder.path("/internal/consult/stats/overview")
                                .queryParam("days",days).build())
                    .retrieve()
                    .body(ConsultStatDTO.class);
            return dto==null?ConsultStatDTO.empty():dto;
        }catch (Exception e){
            Tracer.trace(e);
            log.error("调consult-service取统计失败，看板数字偏小，去检查8040是否在跑",e);
            return ConsultStatDTO.empty();
        }
    }
//    private static RestClient buildRest(){
//        HttpClient http=HttpClient.newBuilder()
//                .connectTimeout(Duration.ofMillis(500)).build();
//        JdkClientHttpRequestFactory f=new JdkClientHttpRequestFactory(http);
//        f.setReadTimeout(Duration.ofMillis(1500));
//        return RestClient.builder().requestFactory(f).build();
//    }
}
