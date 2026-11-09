package org.example.userservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name="diary-service",path="/internal/diary")
public interface DiaryClient {
    @DeleteMapping("/by-user/{userId}/{targetId}")
    int deleteById(@PathVariable("userId")Long userId,
                   @PathVariable("targetId")Long targetId);
}
