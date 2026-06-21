package com.im.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.jwt.JwtUtil;
import com.im.user.dto.LoginResponse;
import com.im.user.dto.UserBrief;
import com.im.user.entity.User;
import com.im.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_SEARCH_LIMIT = 50;

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(String username, String rawPassword) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new IllegalArgumentException("用户被禁用");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        LoginResponse resp = new LoginResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setToken(token);
        return resp;
    }

    /**
     * 按昵称或用户名模糊搜索用户（供前端发起新会话）。
     * keyword 为纯数字时也按 id 精确匹配，便于直接按 userId 找人。
     * 只返回正常状态用户，最多 MAX_SEARCH_LIMIT 条。
     */
    public List<UserBrief> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        String kw = keyword.trim();
        int size = (limit <= 0 || limit > MAX_SEARCH_LIMIT) ? MAX_SEARCH_LIMIT : limit;
        Long maybeId = kw.matches("\\d+") ? parseLongOrNull(kw) : null;

        List<User> rows = userMapper.selectList(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getStatus, 1)
                        .and(w -> w.like(User::getNickname, kw)
                                .or().like(User::getUsername, kw)
                                .or(maybeId != null, q -> q.eq(User::getId, maybeId)))
                        .last("limit " + size));

        return rows.stream().map(UserService::toBrief).toList();
    }

    private static UserBrief toBrief(User u) {
        UserBrief b = new UserBrief();
        b.setId(u.getId());
        b.setUsername(u.getUsername());
        b.setNickname(u.getNickname());
        b.setAvatar(u.getAvatar());
        return b;
    }

    private static Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
