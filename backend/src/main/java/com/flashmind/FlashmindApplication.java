package com.flashmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlashmindApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlashmindApplication.class, args);
    }
}
