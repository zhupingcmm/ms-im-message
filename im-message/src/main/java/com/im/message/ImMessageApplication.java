package com.im.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.im.message.mapper")
public class ImMessageApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImMessageApplication.class, args);
    }
}
