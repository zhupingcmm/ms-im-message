package com.im.gateway.filter;

import com.im.common.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 统一鉴权：校验 Authorization: Bearer <jwt>，解析出 userId 通过内部头 X-User-Id 下传。
 * 始终剥离客户端自带的 X-User-Id，杜绝伪造越权（D1）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    /** 内部身份头：仅由网关注入，下游据此取 userId。 */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** 免鉴权路径前缀（登录、健康检查）。 */
    private static final List<String> WHITELIST = List.of("/auth/login", "/actuator");

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // CORS 预检直接放行
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // 白名单：放行但仍剥离伪造的身份头
        if (isWhitelisted(path)) {
            return chain.filter(stripIdentity(exchange));
        }

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "missing bearer token");
        }
        long userId;
        try {
            userId = jwtUtil.parseUserId(auth.substring(7));
        } catch (Exception e) {
            return unauthorized(exchange, "invalid token");
        }

        ServerHttpRequest mutated = request.mutate()
                .headers(h -> h.remove(USER_ID_HEADER))
                .header(USER_ID_HEADER, String.valueOf(userId))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(path::startsWith);
    }

    /** 剥离客户端可能伪造的内部身份头。 */
    private ServerWebExchange stripIdentity(ServerWebExchange exchange) {
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(h -> h.remove(USER_ID_HEADER))
                .build();
        return exchange.mutate().request(stripped).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        log.debug("reject request: {} {}", exchange.getRequest().getURI().getPath(), reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // 尽早执行
        return -100;
    }
}
