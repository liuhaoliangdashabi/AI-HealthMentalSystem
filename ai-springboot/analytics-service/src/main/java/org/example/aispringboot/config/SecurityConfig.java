package org.example.aispringboot.config;

import cn.hutool.core.text.AntPathMatcher;
import jakarta.servlet.DispatcherType;
import org.example.aispringboot.util.JwtAuthticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final AntPathMatcher antPathMatcher=new AntPathMatcher();

    //白名单
    private static final String[] PUBLIC_PATHS={
            "/",
            "/api/user/login",
            "/api/user/add",
            "/error"
    };

    @Bean
    public BCryptPasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    public static Boolean isPublicPATH(String requestUrl){
        for(String publicPath:PUBLIC_PATHS){
            if(antPathMatcher.match(publicPath,requestUrl)){
                return true;
            }
        }
        return false;
    }

    @Bean
    public JwtAuthticationFilter jwtAuthticationFilter(){
        return new JwtAuthticationFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                //禁用CSRF保护（API服务一般不需要）
                .csrf(AbstractHttpConfigurer::disable)
                //配置会话管理无状态——后续用JWT
                .sessionManagement(session->{
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
                })
                //配置请求的授权规则
                .authorizeHttpRequests(auth->auth
                        //异步派发不该重复验证
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        //公开路径，无需登录即可访问
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        //其他都要认证
                        .anyRequest().authenticated()
                )
                //添加JWT认证过滤器
                .addFilterBefore(jwtAuthticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
