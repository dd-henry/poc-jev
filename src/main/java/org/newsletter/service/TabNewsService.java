package org.newsletter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;
import org.example.config.TabNewsProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TabNewsService {
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final TabNewsProperties properties;
    private final RestClient restClient = RestClient.create();

    public ArrayNode getUserContents(
            String username, LocalDate date, int page, int perPage, String strategy) {
        String uri = UriComponentsBuilder.fromUriString(properties.apiUrl())
                .pathSegment("contents", username.toLowerCase(Locale.ROOT))
                .queryParam("page", page)
                .queryParam("per_page", perPage)
                .queryParam("strategy", strategy)
                .build()
                .toUriString();

        try {
            JsonNode response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray()) {
                throw new TabNewsApiException("A API do TabNews retornou uma resposta vazia.");
            }

            ArrayNode contentsFromDate = JsonNodeFactory.instance.arrayNode();
            response.forEach(content -> {
                JsonNode createdAt = content.get("created_at");
                if (createdAt != null && isOnDate(createdAt.asText(), date)) {
                    contentsFromDate.add(getContent(username, content));
                }
            });
            return contentsFromDate;
        } catch (RestClientException exception) {
            throw new TabNewsApiException("Não foi possível consultar a API do TabNews.", exception);
        }
    }

    private JsonNode getContent(String username, JsonNode summary) {
        String uri = UriComponentsBuilder.fromUriString(properties.apiUrl())
                .pathSegment("contents", username.toLowerCase(Locale.ROOT), summary.get("slug").asText())
                .build()
                .toUriString();

        try {
            JsonNode content = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(JsonNode.class);

            if (content == null || !content.isObject()) {
                throw new TabNewsApiException("A API do TabNews retornou o conteúdo vazio.");
            }
            return content;
        } catch (RestClientException exception) {
            throw new TabNewsApiException(
                    "Não foi possível consultar o conteúdo '" + summary.get("slug").asText() + "'.",
                    exception);
        }
    }

    private boolean isOnDate(String createdAt, LocalDate date) {
        try {
            return Instant.parse(createdAt).atZone(BRAZIL_ZONE).toLocalDate().equals(date);
        } catch (RuntimeException exception) {
            return createdAt.startsWith(date.toString());
        }
    }

    public static class TabNewsApiException extends RuntimeException {
        public TabNewsApiException(String message) {
            super(message);
        }

        public TabNewsApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
