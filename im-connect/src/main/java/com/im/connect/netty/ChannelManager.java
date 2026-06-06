package com.im.connect.netty;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 本实例的本地连接表：userId -> Channel（P0 单端，每用户一条连接）。
 */
@Component
public class ChannelManager {

    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("userId");

    private final ConcurrentHashMap<Long, Channel> userChannels = new ConcurrentHashMap<>();

    public void bind(long userId, Channel channel) {
        userChannels.put(userId, channel);
        channel.attr(USER_ID).set(userId);
    }

    public void unbind(Channel channel) {
        Long userId = channel.attr(USER_ID).get();
        if (userId != null) {
            userChannels.remove(userId, channel);
        }
    }

    public Channel get(long userId) {
        return userChannels.get(userId);
    }

    public boolean isOnline(long userId) {
        Channel c = userChannels.get(userId);
        return c != null && c.isActive();
    }
}
