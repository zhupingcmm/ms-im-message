package com.im.connect.web;

import com.im.common.api.Result;
import com.im.connect.route.RouteRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 在线状态查询：基于 Redis 路由表（route:{userId}）做全局在线判断，
 * 不依赖单个网关实例的本地连接表，多实例部署下也准确。
 */
@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final RouteRegistry routeRegistry;

    /** 批量查询在线状态：/presence?ids=1001,1002 */
    @GetMapping
    public Result<List<PresenceView>> query(@RequestParam("ids") List<Long> ids) {
        List<PresenceView> views = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null) continue;
                views.add(new PresenceView(id, routeRegistry.isOnline(id)));
            }
        }
        return Result.ok(views);
    }

    /** 单用户在线状态视图。 */
    public record PresenceView(long userId, boolean online) {
    }
}
