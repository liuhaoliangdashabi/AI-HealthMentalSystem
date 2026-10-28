package org.example.gateway;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.List;
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String ISSUER="mental-health-assistant";
    private static final List<String> WHITE_LIST=
            List.of("/api/user/login","/api/user/add","/error");

    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.header}")
    private String headerName;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {
        String path=exchange.getRequest().getURI().getPath();
        if(WHITE_LIST.stream().anyMatch(path::startsWith)){
            return chain.filter(exchange);
        }

        String token=exchange.getRequest().getHeaders().getFirst(headerName);
        try {
            DecodedJWT jwt=JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer(ISSUER).build().verify(token);
            ServerHttpRequest newReq=exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(jwt.getClaim("userId").asLong()))
                    .header("X-User-Role", String.valueOf(jwt.getClaim("roleType").asInt()))
                    .header("X-User-Name",jwt.getClaim("username").asString())
                    .build();
            return chain.filter(exchange.mutate().request(newReq).build());
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }
    @Override
    public int getOrder() {
        return -100;
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
