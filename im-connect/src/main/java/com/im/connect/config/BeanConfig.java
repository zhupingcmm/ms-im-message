package com.im.connect.config;

import com.im.common.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${im.jwt.secret}") String secret,
                           @Value("${im.jwt.expire-millis}") long expireMillis) {
        return new JwtUtil(secret, expireMillis);
    }
}
