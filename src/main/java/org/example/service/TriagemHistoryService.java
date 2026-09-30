package org.example.service;

import org.example.controller.JevController;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TriagemHistoryService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public void add(String type, List<JevController.TriagemResultado> results) {
        add(UUID.randomUUID().toString(), type, results);
    }

    @Transactional
    public void add(String id, String type, List<JevController.TriagemResultado> results) {
        Instant executedAt = Instant.now();
        long executionTimeMs = results.stream()
                .mapToLong(JevController.TriagemResultado::executionTimeMs)
                .sum();
        double costUsd = results.stream()
                .mapToDouble(JevController.TriagemResultado::costUsd)
                .sum();

        jdbcTemplate.update("""
                insert into triagem_execution
                    (id, executed_at, execution_type, log_count, execution_time_ms, cost_usd)
                values (:id, :executedAt, :type, :logCount, :executionTimeMs, :costUsd)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("executedAt", Timestamp.from(executedAt))
                        .addValue("type", type)
                        .addValue("logCount", results.size())
                        .addValue("executionTimeMs", executionTimeMs)
                        .addValue("costUsd", costUsd));

        for (int i = 0; i < results.size(); i++) {
            JevController.TriagemResultado result = results.get(i);
            jdbcTemplate.update("""
                    insert into triagem_result
                        (execution_id, result_order, log, team, runbook, chunk_number, request_number,
                         execution_time_ms, cost_usd, raw_response)
                    values (:executionId, :resultOrder, :log, :team, :runbook,
                            :chunkNumber, :requestNumber, :executionTimeMs, :costUsd, :rawResponse)
                    """,
                    new MapSqlParameterSource()
                            .addValue("executionId", id)
                            .addValue("resultOrder", i)
                            .addValue("log", result.log())
                            .addValue("team", result.time())
                            .addValue("runbook", result.runbook())
                            .addValue("chunkNumber", result.chunkNumber())
                            .addValue("requestNumber", result.requestNumber())
                            .addValue("executionTimeMs", result.executionTimeMs())
                            .addValue("costUsd", result.costUsd())
                            .addValue("rawResponse", result.rawResponse()));
        }
    }

    public List<JevController.HistoricoExecucao> findAll() {
        List<HistoryRow> rows = jdbcTemplate.query("""
                select e.id, e.executed_at, e.execution_type, e.log_count,
                       e.execution_time_ms, e.cost_usd,
                       r.log, r.team, r.runbook, r.chunk_number, r.request_number,
                       r.execution_time_ms as result_time_ms,
                       r.cost_usd as result_cost_usd, r.raw_response
                from triagem_execution e
                left join triagem_result r on r.execution_id = e.id
                order by e.executed_at desc, r.result_order
                """,
                (rs, rowNum) -> new HistoryRow(
                        rs.getString("id"),
                        rs.getTimestamp("executed_at").toInstant(),
                        rs.getString("execution_type"),
                        rs.getInt("log_count"),
                        rs.getLong("execution_time_ms"),
                        rs.getDouble("cost_usd"),
                        rs.getString("log"),
                        rs.getString("team"),
                        rs.getString("runbook"),
                        rs.getInt("chunk_number"),
                        rs.getInt("request_number"),
                        rs.getLong("result_time_ms"),
                        rs.getDouble("result_cost_usd"),
                        rs.getString("raw_response")));

        Map<String, JevController.HistoricoExecucao> grouped = new LinkedHashMap<>();
        for (HistoryRow row : rows) {
            grouped.computeIfAbsent(row.id(), id -> new JevController.HistoricoExecucao(
                    row.id(), row.executedAt(), row.type(), row.logCount(),
                    row.executionTimeMs(), row.costUsd(), new ArrayList<>()));
            if (row.log() != null) {
                grouped.get(row.id()).results().add(new JevController.TriagemResultado(
                        row.log(), row.team(), row.runbook(), row.chunkNumber(), row.requestNumber(),
                        row.resultTimeMs(), row.resultCostUsd(), row.rawResponse()));
            }
        }
        return new ArrayList<>(grouped.values());
    }

    @Transactional
    public void clear() {
        jdbcTemplate.update("delete from triagem_execution", new MapSqlParameterSource());
    }

    private record HistoryRow(
            String id, Instant executedAt, String type, int logCount,
            long executionTimeMs, double costUsd, String log, String team,
            String runbook, int chunkNumber, int requestNumber, long resultTimeMs,
            double resultCostUsd, String rawResponse) {}
}
