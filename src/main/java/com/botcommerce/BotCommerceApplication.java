package com.botcommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class BotCommerceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BotCommerceApplication.class, args);
    }
}
