package com.im.user.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.user.entity.User;
import com.im.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * P0 演示账号种子数据（幂等）。
 * 注：表结构(DDL)由 Flyway 管理；此处仅为本地联调插入 demo 账号，
 * 因为密码必须由 PasswordEncoder 生成哈希，不便写死在 SQL 里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoUserSeeder implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seed(1001L, "alice", "Alice");
        seed(1002L, "bob", "Bob");
    }

    private void seed(long id, String username, String nickname) {
        Long exists = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            return;
        }
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setNickname(nickname);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);
        log.info("seeded demo user: {} (id={}), password=123456", username, id);
    }
}
