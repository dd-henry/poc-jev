package org.newsletter.service;

import lombok.RequiredArgsConstructor;
import org.newsletter.config.SupabaseStorageProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class SupabaseStorageClient {
    private final SupabaseStorageProperties properties;
    private final RestClient restClient = RestClient.create();

    public byte[] download(String objectPath) {
        validateConfiguration();
        return restClient.get()
                .uri(objectUrl(objectPath))
                .header("apikey", properties.serviceRoleKey())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceRoleKey())
                .retrieve()
                .body(byte[].class);
    }

    private String objectUrl(String objectPath) {
        return properties.url() + "/storage/v1/object/"
                + properties.bucket() + "/" + objectPath;
    }

    private void validateConfiguration() {
        if (properties.url() == null || properties.url().isBlank()
                || properties.bucket() == null || properties.bucket().isBlank()
                || properties.serviceRoleKey() == null || properties.serviceRoleKey().isBlank()) {
            throw new IllegalStateException(
                    "Configure SUPABASE_URL, SUPABASE_STORAGE_BUCKET e SUPABASE_SERVICE_ROLE_KEY.");
        }
    }
}
