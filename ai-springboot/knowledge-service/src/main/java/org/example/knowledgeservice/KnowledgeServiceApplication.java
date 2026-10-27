package org.example.knowledgeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"org.example.knowledgeservice","org.example.common"})
@EnableDiscoveryClient
public class KnowledgeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeServiceApplication.class, args);
    }

}
