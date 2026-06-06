package com.im.connect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ImConnectApplication {
    public static void main(String[] args) {
        SpringApplication.run(ImConnectApplication.class, args);
    }
}
