package org.example.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密器。
 * web 里这个 Bean 是 SecurityConfig 顺带提供的，这里没引 starter-security，
 * 必须自己声明一个，否则 UserService 构造注入直接失败。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
