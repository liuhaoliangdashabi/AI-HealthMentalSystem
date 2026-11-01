package org.example.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户域独立服务。
 *
 * scanBasePackages 必须带 org.example.common —— IdentityInterceptor / IdentityWebConfig /
 * GlobalExceptionHandler 都在那儿。漏了的话 @RequireIdentity 不生效且异常不会被统一处理，
 * 这个坑 consult 已经踩过一次。
 */
@SpringBootApplication(scanBasePackages = {"org.example.userservice", "org.example.common"})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
