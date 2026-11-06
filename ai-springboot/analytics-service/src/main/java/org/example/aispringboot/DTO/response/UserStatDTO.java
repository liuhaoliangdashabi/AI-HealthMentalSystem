package org.example.aispringboot.DTO.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * user-service 的 /internal/user/stats 返回体。
 * dailyNew 的 key 是 "yyyy-MM-dd"。
 */
@Data
@NoArgsConstructor
public class UserStatDTO {
    private long total;
    private Map<String, Long> dailyNew = new HashMap<>();

    private boolean degraded=false;

    public UserStatDTO(boolean degraded) {
        this.degraded=degraded;
    }

    public static UserStatDTO empty() {
        return new UserStatDTO(true);
    }
}
