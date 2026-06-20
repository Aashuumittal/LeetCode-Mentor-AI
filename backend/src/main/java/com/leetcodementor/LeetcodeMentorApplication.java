package com.leetcodementor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LeetcodeMentorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetcodeMentorApplication.class, args);
    }
}
