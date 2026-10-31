package org.example.consultservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"org.example.consultservice",
        "org.example.common","org.example.aicommon"})
public class ConsultServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultServiceApplication.class, args);
    }

}
