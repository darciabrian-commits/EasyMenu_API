package org.esfe.easymenu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EasymenuApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(EasymenuApiApplication.class, args);
    }
}