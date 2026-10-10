package org.newsletter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase.storage")
public record SupabaseStorageProperties(
        String url,
        String bucket,
        String serviceRoleKey) {
}
