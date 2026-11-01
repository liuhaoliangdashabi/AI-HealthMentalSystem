package org.example.gateway;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.example.common.util.IdentityUtil.HEADER_USER_ID;
import static org.example.common.util.IdentityUtil.HEADER_USER_NAME;
import static org.example.common.util.IdentityUtil.HEADER_USER_ROLE;

@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String ISSUER="mental-health-assistant";
    private static final String BLACK_KEY="blacklist:token:";
    private static final List<String> WHITE_LIST=
            List.of("/api/user/login","/api/user/add","/error");

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.header}")
    private String headerName;

    private final ReactiveStringRedisTemplate redis;

    public AuthGlobalFilter(ReactiveStringRedisTemplate redis) {
        this.redis=redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {
        String path=exchange.getRequest().getURI().getPath();
        if(WHITE_LIST.stream().anyMatch(path::startsWith)){
            return chain.filter(exchange);
        }

        String token=exchange.getRequest().getHeaders().getFirst(headerName);
        DecodedJWT jwt;
        try {
            jwt=JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer(ISSUER).build().verify(token);
        } catch (Exception e) {
            //验签失败不用查 Redis，直接挡掉
            return unauthorized(exchange);
        }

        ServerHttpRequest newReq=exchange.getRequest().mutate()
                //先把客户端自带的身份头全部作废，再注入 token 解出来的值。
                //不这么做的话，调用方自己塞一个 X-User-Role: 2 就能冒充管理员
                .headers(h->{
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_ROLE);
                    h.remove(HEADER_USER_NAME);
                })
                .header(HEADER_USER_ID, String.valueOf(jwt.getClaim("userId").asLong()))
                .header(HEADER_USER_ROLE, String.valueOf(jwt.getClaim("roleType").asInt()))
                .header(HEADER_USER_NAME,jwt.getClaim("username").asString())
                .build();
        ServerWebExchange newExchange=exchange.mutate().request(newReq).build();

        //登出后 token 只是被塞进 Redis 黑名单，签名照样合法，
        //所以光验签挡不住，必须查一次黑名单——这是 web 下线后最容易漏的一环
        return redis.hasKey(BLACK_KEY+md5Hex(token))
                .defaultIfEmpty(false)
                .flatMap(blocked -> Boolean.TRUE.equals(blocked)
                        ? unauthorized(exchange)
                        : chain.filter(newExchange))
                //Redis 挂了按放行处理，和 web 原来的策略一致：
                //宁可短暂失去登出能力，也不能让整站登不进来
                .onErrorResume(e->{
                    log.error("网关查 token 黑名单失败，Redis 可能有问题，本次按放行处理",e);
                    return chain.filter(newExchange);
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 黑名单 key 里用 token 的 md5，和 web 登出时写的 key 必须一模一样，
     * 差一个字母就是「登出后还能继续用」。
     */
    private static String md5Hex(String token){
        try {
            MessageDigest md=MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 不可用",e);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange){
        ServerHttpResponse response=exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":\"401\",\"data\":null,\"msg\":\"未登录或登录已过期\"}";
        DataBuffer buff=response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buff));
    }
}
