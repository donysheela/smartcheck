package com.student.smartcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartcheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartcheckApplication.class, args);
    }

}
