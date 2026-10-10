package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tabnews")
public record TabNewsProperties(String apiUrl) {
}
