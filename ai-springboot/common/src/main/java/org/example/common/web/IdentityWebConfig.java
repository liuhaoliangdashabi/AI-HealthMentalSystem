package org.example.common.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 三个服务都扫了 org.example.common 包，所以这份配置自动生效，不用各服务再写一遍。
 * web 里用 @AuthenticationPrincipal 取身份（SecurityContext 里有），别给它加 @RequireIdentity，
 * 那边没有 X-User-Id 请求头，加了会被拦成 401。
 */
@Configuration
public class IdentityWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdentityInterceptor()).addPathPatterns("/**");
    }
}
