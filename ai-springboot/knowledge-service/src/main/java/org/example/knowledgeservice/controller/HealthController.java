package org.example.knowledgeservice.controller;

import org.example.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @Value("${spring.application.name}")
    private String appName;

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success(appName + "ok");
    }
}
