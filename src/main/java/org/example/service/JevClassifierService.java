package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.example.config.OpenRouterProperties;
import org.example.controller.JevController;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JevClassifierService {
    private final OpenRouterProperties properties;
    private final RestClient restClient = RestClient.create();

    public JevController.TriagemResultado classify(String log) {
        return classifyBatch(List.of(log)).getFirst();
    }

    public List<JevController.TriagemResultado> classifyBatch(List<String> logs) {
        if (logs == null || logs.isEmpty() || logs.stream().anyMatch(log -> log == null || log.isBlank())) {
            throw new IllegalArgumentException("logs não pode ser vazio e não pode conter itens vazios");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("Defina a variável de ambiente OPENROUTER_API_KEY antes de chamar o JEV.");
        }

        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("devops", "Infraestrutura, Helm, Kubernetes, containers, deploys, pods, nós, clusters ou timeouts de rede.");
        criteria.put("qa", "AssertionError, testes automatizados, testes E2E, testes de integração, fixtures, mocks ou contratos de API.");
        criteria.put("security", "Snyk, GitLeaks, Trivy, chaves, tokens, secrets, CVEs ou vulnerabilidades.");
        criteria.put("arquitetura", "Exceções de código, Spring Boot, beans, dependências, injeção, configuração ou contexto da aplicação.");
        criteria.put("sre", "Kafka, brokers, consumer groups, lag, tópicos, filas, dead-letter queues, RabbitMQ, bancos de dados, connection pools, queries, deadlocks ou Redis.");

        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> questions = new LinkedHashMap<>();
        for (int i = 0; i < logs.size(); i++) {
            String questionId = "log_" + (i + 1);
            state.put(questionId, logs.get(i));
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("type", "choice");
            question.put("instructions", "Qual time deve ser responsável por este incidente de CI/CD? Analise somente o log recebido.");
            question.put("criteria", criteria);
            questions.put(questionId, question);
        }

        Map<String, Object> request = Map.of(
                "model", properties.model(),
                "state", state,
                "questions", questions
        );

        long startedAt = System.nanoTime();
        JsonNode response = restClient.post()
                .uri(properties.apiUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.apiKey())
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        long executionTimeMs = (System.nanoTime() - startedAt) / 1_000_000;

        if (response == null) {
            throw new IllegalStateException("Resposta vazia do modelo JEV.");
        }

        double requestCost = response.path("usage").path("cost").asDouble(0.0);
        List<JevController.TriagemResultado> results = new ArrayList<>();
        for (int i = 0; i < logs.size(); i++) {
            String questionId = "log_" + (i + 1);
            String team = response.path("answers").path(questionId).path("choice").asText("");
            String time = switch (team) {
                case "devops" -> "DevOps";
                case "qa" -> "QA";
                case "security" -> "Security";
                case "arquitetura" -> "Arquitetura";
                case "sre" -> "SRE";
                default -> throw new IllegalStateException("Time retornado pelo JEV não é válido: " + team);
            };
            String runbook = switch (team) {
                case "devops" -> "RBK-INF-001 - Troubleshooting AKS/K8s";
                case "qa" -> "RBK-QA-023 - Falha em Testes E2E";
                case "security" -> "RBK-SEC-099 - Revogação de Secrets";
                case "arquitetura" -> "RBK-ARQ-012 - Refatoração de Injeção de Dependências";
                case "sre" -> "RBK-SRE-045 - Troubleshooting Kafka, Filas e Bancos de Dados";
                default -> throw new IllegalStateException("Runbook não configurado para o time: " + team);
            };
            results.add(new JevController.TriagemResultado(
                    logs.get(i), time, runbook, 1, 1, executionTimeMs,
                    requestCost / logs.size(), response.toString()));
        }
        return results;
    }
}
