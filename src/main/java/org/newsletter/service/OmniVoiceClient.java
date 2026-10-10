package org.newsletter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsletter.config.OmniVoiceProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OmniVoiceClient {
    private final OmniVoiceProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public void submit(String executionCode, String audioFilename, String text) {
        log.info("Newsletter {}: preparando requisição JSON para {} ({} caracteres).",
                executionCode, properties.url(), text.length());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("execution_code", executionCode);
        payload.put("audio_filename", audioFilename);
        payload.put("text", text);
        payload.put("lang", "Portuguese");
        payload.put("ns", 32);
        payload.put("gs", 2.0);
        payload.put("dn", true);
        payload.put("speed", 1.0);
        payload.put("du", 0.0);
        payload.put("pp", true);
        payload.put("po", true);
        payload.put("gender", "Male / 男");
        payload.put("age", "Middle-aged / 中年");
        payload.put("pitch", "Auto");
        payload.put("style", "Auto");
        payload.put("accent", "Portuguese Accent / 葡萄牙口音");
        payload.put("dialect", "Auto");

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            log.info("Newsletter {}: request HTTP POST {} Content-Type=application/json body={}",
                    executionCode, properties.url() + "/synthesize", requestBody);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.url() + "/synthesize"))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                requestBuilder.header("X-OmniVoice-Key", properties.apiKey());
            }
            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofByteArray(
                            requestBody.getBytes(StandardCharsets.UTF_8)))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("Newsletter {}: OmniVoice respondeu HTTP {}: {}",
                        executionCode, response.statusCode(), response.body());
                throw new IllegalStateException(
                        response.statusCode() + " " + response.body());
            }
            log.info("Newsletter {}: request HTTP POST concluído com sucesso.", executionCode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Não foi possível serializar a requisição do OmniVoice.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível enviar a requisição do OmniVoice.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A chamada ao OmniVoice foi interrompida.", exception);
        }
        log.info("Newsletter {}: resposta HTTP recebida do OmniVoice.", executionCode);
    }
}
