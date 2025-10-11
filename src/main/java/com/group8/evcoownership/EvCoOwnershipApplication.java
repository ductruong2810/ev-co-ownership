package com.group8.evcoownership;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
//bo sung 10/11
public class EvCoOwnershipApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvCoOwnershipApplication.class, args);
    }
}
