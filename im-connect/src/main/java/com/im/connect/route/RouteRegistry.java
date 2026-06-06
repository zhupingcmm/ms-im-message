package com.im.connect.route;

import com.im.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 连接路由表：route:{userId} -> Set(gatewayId)。
 * 供下游按用户定位所在网关实例（P0 单实例，主要演示机制）。
 */
@Component
@RequiredArgsConstructor
public class RouteRegistry {

    private final StringRedisTemplate redis;

    @Value("${im.gateway.id}")
    private String gatewayId;

    public void online(long userId) {
        redis.opsForSet().add(RedisKeys.route(userId), gatewayId);
    }

    public void offline(long userId) {
        redis.opsForSet().remove(RedisKeys.route(userId), gatewayId);
    }
}
