package org.example.aispringboot.util;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.aispringboot.common.ResultCode;
import org.example.aispringboot.config.SecurityConfig;
import org.example.aispringboot.enumClass.UserStatus;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.service.OnlineUserService;
import org.example.aispringboot.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
public class JwtAuthticationFilter extends OncePerRequestFilter {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private OnlineUserService onlineUserService;

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
                //3.根据解析出的Id查询验证用户状态
                UserLoginResponseDTO.UserDetailResponseDTO user;
                try{
                    user=userService.getUserById(validationResult.getUserId());
                }catch(BusinessException e){
                    log.warn("token解析出的用户查不到，userId={}",validationResult.getUserId());
                    clearSecurityContext();
                    ResponseUtil.WriteError(response,ResultCode.TOKEN_INVALID);
                    return;
                }
                log.debug("认证通过，userId={}",validationResult.getUserId());
                if(user!=null && UserStatus.NORMAL.getCode().equals(user.getStatus())){
                    String isBlack=null;
                    try {
                        isBlack = redis.opsForValue().get("blacklist:token:"+ DigestUtil.md5Hex(token));
                        if(isBlack!=null){
                            log.info("token被加入黑名单，所属Id={}",validationResult.getUserId());
                            clearSecurityContext();
                            ResponseUtil.WriteError(response,ResultCode.TOKEN_BLOCKED);
                            return;
                        }
                    } catch (Exception e) {
                        log.error("查黑名单失败，Redis出问题了！尽快维修！！",e);
//                        clearSecurityContext();
//                        ResponseUtil.WriteError(response,ResultCode.SYSTEM_ERROR);
//                        return;
                    }
                    //4.创建SpringSecurity认证对象
                    List<SimpleGrantedAuthority> authorities=Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_"+validationResult.getRoleType())
                    );
                    //5.创建UsernamePasswordAuthenticationToken
                    UsernamePasswordAuthenticationToken authcation=new UsernamePasswordAuthenticationToken(
                            user,
                            null,//使用JWT，密码可不设置
                            authorities
                    );
                    //6.设置认证信息到SpringSecurity上下文——不然被拦截
                    SecurityContextHolder.getContext().setAuthentication(authcation);
                    //7.token存储到请求的属性中——确保截取的token一定没问题了，再还回去
                    request.setAttribute("jwtToken",token);
                    //8.在线心跳。放在认证之后 —— 没通过认证的请求不算"在线"，
                    //  不然攻击者拿一堆废 token 刷接口，能把在线人数刷上天。
                    //  touch 内部吞了所有异常，Redis 挂了也不会影响这次请求
                    onlineUserService.touch(validationResult.getUserId());
                }else{
                    log.warn("user数据或user状态有误：userId={}",user.getId());
                    clearSecurityContext();
                    ResponseUtil.WriteError(response,ResultCode.TOKEN_ACCESS_FORBIDDEN);
                    return;
                }
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
