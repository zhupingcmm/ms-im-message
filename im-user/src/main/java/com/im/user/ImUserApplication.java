package com.im.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.im.user.mapper")
public class ImUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImUserApplication.class, args);
    }
}
