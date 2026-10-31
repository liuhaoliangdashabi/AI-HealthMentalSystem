package org.example.diaryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

//必须显式扫 org.example.common，否则common的GlobalExceptionHandler不会注册，
//BusinessException无人接管→全部变成SpringBoot默认的500{"timestamp","status","error","path"}
//@EnableScheduling 是给 AiTaskConsumer 用的：消费主循环和 PEL 兜底都靠 @Scheduled
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"org.example.diaryservice",
        "org.example.common","org.example.aicommon"})
public class DiaryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiaryServiceApplication.class, args);
    }

}
