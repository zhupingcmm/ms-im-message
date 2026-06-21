package com.im.connect.netty;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本实例的本地连接表：userId -> (deviceId -> Channel)。
 * P1 多端：每用户可有多条连接，按 deviceId 区分；同设备重连覆盖旧连接。
 */
@Component
public class ChannelManager {

    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("userId");
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("deviceId");

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Channel>> userChannels = new ConcurrentHashMap<>();

    public void bind(long userId, String deviceId, Channel channel) {
        userChannels.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(deviceId, channel);
        channel.attr(USER_ID).set(userId);
        channel.attr(DEVICE_ID).set(deviceId);
    }

    public void unbind(Channel channel) {
        // 清空 attr，避免登出后连接断开时被 channelInactive 误判为仍登录
        Long userId = channel.attr(USER_ID).getAndSet(null);
        String deviceId = channel.attr(DEVICE_ID).getAndSet(null);
        if (userId == null) {
            return;
        }
        ConcurrentHashMap<String, Channel> devices = userChannels.get(userId);
        if (devices == null) {
            return;
        }
        if (deviceId != null) {
            devices.remove(deviceId, channel);
        } else {
            devices.values().remove(channel);
        }
        if (devices.isEmpty()) {
            userChannels.remove(userId, devices);
        }
    }

    /** 该用户在本实例的全部连接（多端）。 */
    public Collection<Channel> get(long userId) {
        ConcurrentHashMap<String, Channel> devices = userChannels.get(userId);
        return devices == null ? List.of() : List.copyOf(devices.values());
    }

    /** 本实例是否还持有该用户的任一活跃连接。 */
    public boolean isOnline(long userId) {
        ConcurrentHashMap<String, Channel> devices = userChannels.get(userId);
        return devices != null && devices.values().stream().anyMatch(Channel::isActive);
    }

    /**
     * 本实例当前在线连接的只读快照，供查询接口使用。
     */
    public List<Online> snapshot() {
        List<Online> list = new ArrayList<>();
        userChannels.forEach((uid, devices) ->
                devices.forEach((dev, ch) -> list.add(new Online(uid, dev, ch))));
        return list;
    }

    /** 单条在线连接快照。 */
    public record Online(long userId, String deviceId, Channel channel) {
    }
}
