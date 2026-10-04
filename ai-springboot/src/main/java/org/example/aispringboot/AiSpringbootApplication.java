package org.example.aispringboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class AiSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiSpringbootApplication.class, args);
    }

}
