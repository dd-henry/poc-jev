package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = {"org.example", "org.newsletter"})
@ComponentScan(basePackages = {"org.example", "org.newsletter"})
@EnableScheduling
public class JevApplication {
    public static void main(String[] args) {
        SpringApplication.run(JevApplication.class, args);
    }
}
