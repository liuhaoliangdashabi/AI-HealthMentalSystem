package org.example.userservice.util;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.userservice.config.JwtConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;
import java.util.UUID;

/**
 * 和 web 那份的唯一区别：去掉了 SecurityContextHolder 相关方法。
 * 本服务不引 starter-security，身份一律从网关注入的 X-User-* 头取（见 IdentityUtil）。
 */
@Slf4j
@Component
public class JwtTokenUtil {
    private static final String ISSUER = "mental-health-assistant";

    private final JwtConfig jwtConfig;

    public JwtTokenUtil(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    //生成token
    //JWT的iat(s)，withIssuedAt(new Date())，精度不一致
    //如果没做防重提交，token一模一样，登出会把两个token一起拉黑：改唯一的UUID
    public String generateToken(Long userId, String username, Integer roleType) {
        try {
            log.debug("现在正在生成token");
            Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
            Date expiration = new Date(System.currentTimeMillis() + jwtConfig.getExpiration());

            return JWT.create()
                    .withClaim("userId", userId)
                    .withClaim("username", username)
                    .withClaim("roleType", roleType)
                    .withIssuedAt(new Date())
                    .withJWTId(UUID.randomUUID().toString())
                    .withIssuer(ISSUER)
                    .withExpiresAt(expiration)
                    .sign(algorithm);
        } catch (Exception e) {
            log.error("生成token失败，错误原因：{}", ExceptionUtil.getRootCauseMessage(e));
            throw new RuntimeException("生成token失败", e);
        }
    }

    //提取token
    public String extractTokenFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String tokenHeader = request.getHeader(jwtConfig.getHeader());
        if (StringUtils.hasText(tokenHeader)) {
            return tokenHeader;
        }
        return null;
    }

    //获取当前的token
    public String getCurrentToken() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String token = (String) request.getAttribute("jwtToken");
            if (token != null) return token;

            //备用方案——请求头直接获取
            return extractTokenFromRequest(request);
        }
        return null;
    }

    //验证token,并提取荷载部分
    @Getter
    public static class TokenVeriticationResult {
        private final Long userId;
        private final String username;
        private final Integer roleType;
        private final boolean valid;

        public TokenVeriticationResult(Long userId, String username, Integer roleType, boolean valid) {
            this.userId = userId;
            this.username = username;
            this.roleType = roleType;
            this.valid = valid;
        }
    }

    public TokenVeriticationResult validateToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        String username = jwt.getClaim("username").asString();
        //角色类型需要兼容——可能传字符，也可能传数字
        Integer roleType = null;
        try {
            roleType = jwt.getClaim("roleType").asInt();
        } catch (Exception e) {
            String roleTypeStr = jwt.getClaim("roleType").asString();
            if (StringUtils.hasText(roleTypeStr)) {
                roleType = Integer.valueOf(roleTypeStr);
            }
        }

        if (userId != null && StringUtils.hasText(username) && roleType != null) {
            return new TokenVeriticationResult(userId, username, roleType, true);
        }
        return null;
    }

    //验证token是否有效
    public DecodedJWT verifyToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new JWTVerificationException("Token不能为空");
        }
        Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
        JWTVerifier verifyToken = JWT.require(algorithm).withIssuer(ISSUER).build();
        return verifyToken.verify(token);
    }
}
