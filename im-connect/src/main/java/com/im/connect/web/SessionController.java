package com.im.connect.web;

import com.im.common.api.Result;
import com.im.connect.netty.ChannelManager;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线会话查询接口：展示本网关实例当前持有的所有已登录用户及其连接信息。
 */
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final ChannelManager channelManager;

    @GetMapping
    public Result<List<SessionView>> list() {
        List<SessionView> sessions = channelManager.snapshot().entrySet().stream()
                .map(e -> {
                    Channel c = e.getValue();
                    return new SessionView(
                            e.getKey(),
                            c.id().asShortText(),
                            String.valueOf(c.remoteAddress()),
                            c.isActive());
                })
                .toList();
        return Result.ok(sessions);
    }

    /**
     * 单个在线会话视图。
     */
    public record SessionView(long userId, String channelId, String remoteAddress, boolean active) {
    }
}
