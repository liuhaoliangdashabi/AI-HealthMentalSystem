package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.UserStatDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 调 user-service 的内部接口拿用户统计。
 * <p>
 * web 没注册 Nacos（spring.application.name 还是 ai-springboot），用不了 lb://，
 * 所以地址直接配在 yml 里。以后 web 彻底退休这块代码一起删。
 * <p>
 * 失败一律返回空对象：看板缺个用户数不应该让整个页面 500。
 */
@Slf4j
@Component
public class UserStatClient {

    private final RestClient rest = RestClient.create();

    @Value("${internal.user-service.url:http://127.0.0.1:8050}")
    private String baseUrl;

    public UserStatDTO fetch(int days) {
        try {
            UserStatDTO dto = rest.get()
                    .uri(baseUrl + "/internal/user/stats?days=" + days)
                    .retrieve()
                    .body(UserStatDTO.class);
            return dto == null ? UserStatDTO.empty() : dto;
        } catch (Exception e) {
            log.error("调 user-service 取用户统计失败，看板数字会偏小，检查 8050 是否在跑", e);
            return UserStatDTO.empty();
        }
    }
}
