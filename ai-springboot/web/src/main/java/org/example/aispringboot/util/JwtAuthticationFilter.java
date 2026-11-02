package org.example.aispringboot.util;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.common.ResultCode;
import org.example.aispringboot.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
public class JwtAuthticationFilter extends OncePerRequestFilter {
    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    //确保每个请求只执行一次，不要在过滤器中反复打转
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request){
        String requestUri=request.getRequestURI();
        return SecurityConfig.isPublicPATH(requestUri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        //获取请求的URL和方法
        String requestUri=request.getRequestURI();
        String method=request.getMethod();
        log.debug("获取到请求的URI和方法：request={},method={}",requestUri,method);

        //1.提取JWT token
        String token=jwtTokenUtil.extractTokenFromRequest(request);
        if(StringUtils.hasText(token)){
            //2.验证token并提取信息
            JwtTokenUtil.TokenVeriticationResult validationResult;
            try {
                validationResult = jwtTokenUtil.validateToken(token);
            } catch (JWTVerificationException e) {
                //过期是正常现象（大家都会遇到），签名/格式错才可疑 —— 分开打，别让正常的把可疑的淹了
                if(e instanceof TokenExpiredException){
                    log.debug("token已过期");
                }else{
                    log.warn("token校验失败：{}",e.getClass().getSimpleName());
                }
                clearSecurityContext();
                ResponseUtil.WriteError(response,ResultCode.TOKEN_EXPIRED);
                return;
            }
            if(validationResult!=null && validationResult.isValid()){
                UserLoginResponseDTO.UserDetailResponseDTO principal=
                        new UserLoginResponseDTO.UserDetailResponseDTO();
                principal.setId(validationResult.getUserId());
                principal.setUsername(validationResult.getUsername());
                principal.setUserType(validationResult.getRoleType());
                UsernamePasswordAuthenticationToken authcation=
                        new UsernamePasswordAuthenticationToken(principal,null,
                                Collections.singletonList(
                                        new SimpleGrantedAuthority("ROLE_"+validationResult.getRoleType())));
                SecurityContextHolder.getContext().setAuthentication(authcation);
                request.setAttribute("jwtToken",token);
            }else{
                log.warn("token解析出来有问题(userId无效)");
                clearSecurityContext();
                ResponseUtil.WriteError(response, ResultCode.TOKEN_INVALID);
                return;
            }
        }else{
            log.debug("提取token失败：请求未携带token");
            clearSecurityContext();
            ResponseUtil.WriteError(response, ResultCode.ACCESS_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request,response);

    }

    //清理Spring Security上下文
    private void clearSecurityContext(){
        SecurityContextHolder.clearContext();
    }

}
