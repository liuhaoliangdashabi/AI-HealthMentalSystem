package org.example.aispringboot.DTO.response;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * user-service 的 /internal/user/stats 返回体。
 * dailyNew 的 key 是 "yyyy-MM-dd"。
 */
@Data
public class UserStatDTO {
    private long total;
    private Map<String, Long> dailyNew = new HashMap<>();

    public static UserStatDTO empty() {
        return new UserStatDTO();
    }
}
