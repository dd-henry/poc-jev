package org.newsletter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omnivoice")
public record OmniVoiceProperties(String url, String audioDirectory, String apiKey) {
}
