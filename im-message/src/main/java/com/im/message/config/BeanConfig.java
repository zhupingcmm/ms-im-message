package com.im.message.config;

import com.im.common.id.Snowflake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public Snowflake snowflake(@Value("${im.snowflake.worker-id:1}") long workerId) {
        return new Snowflake(workerId);
    }
}
