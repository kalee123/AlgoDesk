package com.app.algodesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlgoDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlgoDeskApplication.class, args);
    }

}
