package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = {"org.example", "org.newsletter"})
@ComponentScan(basePackages = {"org.example", "org.newsletter"})
public class JevApplication {
    public static void main(String[] args) {
        SpringApplication.run(JevApplication.class, args);
    }
}
