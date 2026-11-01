package org.example.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * common 传递进来了 spring-boot-starter-security，Boot 会默认生成一条
 * "所有请求都要 Basic 认证" 的安全链 —— 表现是连 /api/user/login 都返回 401，
 * 响应头带 WWW-Authenticate: Basic realm="Realm"。
 * <p>
 * 本服务的所有鉴权都由网关完成（验签 + 黑名单 + 注 X-User-Id），内部只做 @RequireIdentity 判空，
 * 所以这里整条链放行，只保留一个空壳覆盖掉默认链。
 * <p>
 * 注意：不能靠 spring.autoconfigure.exclude 排 UserDetailsServiceAutoConfiguration，
 * 那只是不生成默认用户密码，默认安全链照样在。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(reg -> reg.anyRequest().permitAll());
        return http.build();
    }
}
