package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(String apiUrl, String apiKey, String model) {}
