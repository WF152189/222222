package com.example.svf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SvfPracticeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SvfPracticeApplication.class, args);
    }
}
