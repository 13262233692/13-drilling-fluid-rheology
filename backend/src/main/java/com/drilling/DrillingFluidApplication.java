package com.drilling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DrillingFluidApplication {
    public static void main(String[] args) {
        SpringApplication.run(DrillingFluidApplication.class, args);
    }
}
