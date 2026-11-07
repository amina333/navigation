package com.air.navigation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // envoi des e-mails en arrière-plan
public class NavigationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NavigationApplication.class, args);
    }
}
