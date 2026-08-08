package com.shiftscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShiftSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiftSchedulerApplication.class, args);
    }

}
