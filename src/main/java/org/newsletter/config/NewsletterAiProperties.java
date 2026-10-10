package org.newsletter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "newsletter.ai")
public record NewsletterAiProperties(String apiUrl, String apiKey, String model) {
}
