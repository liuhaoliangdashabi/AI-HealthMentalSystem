package org.example.consultservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("mental_health_assistant.user")
public class UserBrief {
    private Long id;
    private String nickname;
}
