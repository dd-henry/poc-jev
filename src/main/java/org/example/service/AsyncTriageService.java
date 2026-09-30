package org.example.service;

import org.example.controller.JevController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

@Service
public class AsyncTriageService {
    private final JevClassifierService classifierService;
    private final TriagemHistoryService historyService;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore concurrency;
    private final int maxConcurrency;
    private final int logsPerRequest;

    public AsyncTriageService(
            JevClassifierService classifierService,
            TriagemHistoryService historyService,
            @Value("${processing.max-concurrency:10}") int maxConcurrency,
            @Value("${processing.logs-per-request:2}") int logsPerRequest) {
        this.classifierService = classifierService;
        this.historyService = historyService;
        this.concurrency = new Semaphore(maxConcurrency);
        this.maxConcurrency = maxConcurrency;
        this.logsPerRequest = logsPerRequest;
    }

    public String submit(List<String> logs) {
        String id = UUID.randomUUID().toString();
        jobs.put(id, new Job(id, "RECEIVED", logs.size(), 0, Instant.now(), null));
        executor.submit(() -> process(id, logs));
        return id;
    }

    public JevController.AsyncJobStatus status(String id) {
        Job job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("Execução não encontrada: " + id);
        }
        return new JevController.AsyncJobStatus(
                job.id(), job.status(), job.totalLogs(), job.completedLogs(),
                job.submittedAt(), job.error());
    }

    private void process(String id, List<String> logs) {
        update(id, "PROCESSING", 0, null);
        List<JevController.TriagemResultado> results = new ArrayList<>();
        try {
            List<List<String>> requests = partition(logs, logsPerRequest);
            int parallelism = maxConcurrency;
            for (int start = 0; start < requests.size(); start += parallelism) {
                int chunkNumber = (start / parallelism) + 1;
                List<List<String>> chunk = requests.subList(start, Math.min(start + parallelism, requests.size()));
                int requestBase = start;
                List<RequestResult> chunkResults = new ArrayList<>();
                try (var chunkExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                    var futures = IntStream.range(0, chunk.size())
                            .mapToObj(index -> chunkExecutor.submit(() ->
                                        classifyWithLimit(requestBase + index + 1, chunk.get(index))))
                            .toList();
                    for (var future : futures) {
                        chunkResults.add(future.get());
                    }
                }
                chunkResults.forEach(requestResult -> results.addAll(
                        requestResult.results().stream()
                                .map(result -> new JevController.TriagemResultado(
                                        result.log(), result.time(), result.runbook(), chunkNumber,
                                        requestResult.requestNumber(), result.executionTimeMs(),
                                        result.costUsd(), result.rawResponse()))
                                .toList()));
                update(id, "PROCESSING", results.size(), null);
            }
            historyService.add(id, "ASYNC_BATCH", results);
            update(id, "COMPLETED", results.size(), null);
        } catch (Exception exception) {
            update(id, "FAILED", results.size(), exception.getMessage());
        }
    }

    private RequestResult classifyWithLimit(int requestNumber, List<String> logs) throws InterruptedException {
        concurrency.acquire();
        try {
            return new RequestResult(requestNumber, classifierService.classifyBatch(logs));
        } finally {
            concurrency.release();
        }
    }

    private List<List<String>> partition(List<String> logs, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("processing.logs-per-request deve ser maior que zero");
        }

        List<List<String>> partitions = new ArrayList<>();
        for (int start = 0; start < logs.size(); start += size) {
            partitions.add(List.copyOf(logs.subList(start, Math.min(start + size, logs.size()))));
        }
        return partitions;
    }

    private void update(String id, String status, int completedLogs, String error) {
        jobs.computeIfPresent(id, (key, current) ->
                new Job(current.id(), status, current.totalLogs(), completedLogs,
                        current.submittedAt(), error));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private record Job(
            String id, String status, int totalLogs, int completedLogs,
            Instant submittedAt, String error) {}

    private record RequestResult(
            int requestNumber, List<JevController.TriagemResultado> results) {}
}
