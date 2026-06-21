package com.im.user.controller;

import com.im.common.api.Result;
import com.im.user.dto.UserBrief;
import com.im.user.entity.User;
import com.im.user.mapper.UserMapper;
import com.im.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 用户公开信息查询。前端用会话列表里的 peerId 批量解析昵称/头像，或按关键字搜索发起新会话。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final UserService userService;

    /** 批量查询用户公开信息：/users?ids=1001,1002 */
    @GetMapping
    public Result<List<UserBrief>> batch(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserBrief> list = userMapper.selectBatchIds(ids).stream()
                .map(UserController::toBrief)
                .toList();
        return Result.ok(list);
    }

    /** 按昵称/用户名/userId 搜索用户：/users/search?keyword=al&limit=20 */
    @GetMapping("/search")
    public Result<List<UserBrief>> search(@RequestParam("keyword") String keyword,
                                          @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.ok(userService.search(keyword, limit));
    }

    private static UserBrief toBrief(User u) {
        UserBrief b = new UserBrief();
        b.setId(u.getId());
        b.setUsername(u.getUsername());
        b.setNickname(u.getNickname());
        b.setAvatar(u.getAvatar());
        return b;
    }
}
