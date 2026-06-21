package com.im.gateway.config;

import com.im.common.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${im.jwt.secret}") String secret,
                           @Value("${im.jwt.expire-millis:604800000}") long expireMillis) {
        return new JwtUtil(secret, expireMillis);
    }
}
