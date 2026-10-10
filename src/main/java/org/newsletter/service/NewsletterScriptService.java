package org.newsletter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.newsletter.config.NewsletterAiProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NewsletterScriptService {
    private final NewsletterAiProperties properties;
    private final RestClient restClient = RestClient.create();

    public ScriptResult generate(ArrayNode contents) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new NewsletterAiException(
                    "Defina a variável de ambiente OPENROUTER_API_KEY antes de gerar o roteiro.");
        }
        if (contents == null || contents.isEmpty()) {
            throw new NewsletterAiException("Nenhuma notícia encontrada para gerar o roteiro.");
        }

        Map<String, Object> request = Map.of(
                "model", properties.model(),
                "temperature", 0.8,
                "max_tokens", 2500,
                "messages", new Object[]{
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", compactNews(contents))
                }
        );

        try {
            JsonNode response = restClient.post()
                    .uri(properties.apiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            String script = response == null
                    ? ""
                    : response.path("choices").path(0).path("message").path("content").asText("");
            if (script.isBlank()) {
                throw new NewsletterAiException("A API do OpenRouter retornou um roteiro vazio.");
            }

            return new ScriptResult(script, properties.model(), contents.size(),
                    response.path("usage").path("cost").asDouble(0.0));
        } catch (RestClientException exception) {
            throw new NewsletterAiException("Não foi possível gerar o roteiro no OpenRouter.", exception);
        }
    }

    private String compactNews(ArrayNode contents) {
        StringBuilder news = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            JsonNode content = contents.get(i);
            news.append("\n\nNOTÍCIA ").append(i + 1).append('\n')
                    .append("Título: ").append(content.path("title").asText()).append('\n')
                    .append("Conteúdo: ").append(content.path("body").asText()).append('\n');
            if (!content.path("source_url").isNull()) {
                news.append("Fonte: ").append(content.path("source_url").asText()).append('\n');
            }
        }
        return news.toString();
    }

    private String systemPrompt() {
        return """
                Você escreve falas para um apresentador brasileiro de notícias de tecnologia.
                Gere somente o texto que será falado em voz alta, em português brasileiro.
                O texto deve soar natural, espontâneo e descontraído, como um react de tecnologia:
                use comentários rápidos, humor leve, opiniões claramente identificadas e transições
                naturais entre as notícias.
                Não escreva título, cabeçalhos, tópicos, indicações de cena, instruções de edição,
                rubricas, efeitos sonoros, emojis ou qualquer texto entre colchetes e parênteses.
                Não escreva "abertura", "encerramento" ou explicações sobre o roteiro.
                Comece diretamente com uma fala chamativa e termine naturalmente após a última notícia.
                Não imite literalmente nenhuma pessoa real e não mencione que o texto foi gerado por IA.
                Não invente informações: use somente os dados fornecidos e diferencie fatos de opinião.
                """;
    }

    public record ScriptResult(String script, String model, int newsCount, double cost) {
    }

    public static class NewsletterAiException extends RuntimeException {
        public NewsletterAiException(String message) {
            super(message);
        }

        public NewsletterAiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
