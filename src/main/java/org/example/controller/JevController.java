package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.service.JevClassifierService;
import org.example.service.TriagemHistoryService;
import org.example.service.ExchangeRateService;
import org.example.service.AsyncTriageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Triagem JEV", description = "Classificação de incidentes de CI/CD usando o modelo JEV")
public class JevController {

    private final JevClassifierService service;
    private final TriagemHistoryService historyService;
    private final ExchangeRateService exchangeRateService;
    private final AsyncTriageService asyncTriageService;

    @GetMapping("/health")
    @Operation(summary = "Verifica a saúde da API")
    public String health() {
        return "UP";
    }

    @PostMapping(value = "/triage", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Classifica um log de incidente")
    public TriagemResultado triage(@RequestBody TriagemRequest request) {
        if (request == null || request.log() == null || request.log().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo 'log' é obrigatório.");
        }
        TriagemResultado result = service.classify(request.log());
        historyService.add("SINGLE", List.of(result));
        return result;
    }

    @PostMapping(value = "/triage-batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Classifica vários logs de incidentes")
    public List<TriagemResultado> triageBatch(@RequestBody TriagemBatchRequest request) {
        if (request == null || request.logs() == null || request.logs().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo 'logs' deve conter pelo menos um item.");
        }
        List<TriagemResultado> results = request.logs().stream().map(service::classify).toList();
        historyService.add("BATCH", results);
        return results;
    }

    @PostMapping(value = "/triage/ingest", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recebe logs para processamento assíncrono")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AsyncJobAccepted ingest(@RequestBody TriagemBatchRequest request) {
        if (request == null || request.logs() == null || request.logs().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo 'logs' deve conter pelo menos um item.");
        }
        String executionId = asyncTriageService.submit(request.logs());
        return new AsyncJobAccepted(executionId, "RECEIVED", "/api/triage/ingest/" + executionId);
    }

    @GetMapping("/triage/ingest/{executionId}")
    @Operation(summary = "Consulta o status do processamento assíncrono")
    public AsyncJobStatus ingestStatus(@PathVariable String executionId) {
        try {
            return asyncTriageService.status(executionId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @GetMapping("/history")
    @Operation(summary = "Consulta o histórico de execuções")
    public List<HistoricoExecucao> history() {
        return historyService.findAll();
    }

    @DeleteMapping("/history")
    @Operation(summary = "Limpa o histórico de execuções")
    public void clearHistory() {
        historyService.clear();
    }

    @GetMapping("/exchange-rate")
    @Operation(summary = "Consulta a cotação atual do dólar em reais")
    public ExchangeRateService.ExchangeRate exchangeRate() {
        return exchangeRateService.getUsdToBrl();
    }

    public record TriagemRequest(String log) {}
    public record TriagemBatchRequest(List<String> logs) {}
    public record AsyncJobAccepted(String executionId, String status, String statusUrl) {}
    public record AsyncJobStatus(
            String executionId,
            String status,
            int totalLogs,
            int completedLogs,
            java.time.Instant submittedAt,
            String error) {}
    public record TriagemResultado(
            String log,
            String time,
            String runbook,
            int chunkNumber,
            int requestNumber,
            long executionTimeMs,
            double costUsd,
            String rawResponse) {}
    public record HistoricoExecucao(
            String id,
            java.time.Instant executedAt,
            String type,
            int logCount,
            long executionTimeMs,
            double costUsd,
            List<TriagemResultado> results) {}
}
