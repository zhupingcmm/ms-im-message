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

    /**
     * 全局在线判断：route:{userId} 集合非空即在任一网关实例在线。
     * 不依赖本实例的本地连接表，多网关部署下也准确。
     */
    public boolean isOnline(long userId) {
        Long size = redis.opsForSet().size(RedisKeys.route(userId));
        return size != null && size > 0;
    }
}
