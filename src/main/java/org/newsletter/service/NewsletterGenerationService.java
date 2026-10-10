package org.newsletter.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsletter.config.OmniVoiceProperties;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterGenerationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TabNewsService tabNewsService;
    private final NewsletterScriptService scriptService;
    private final OmniVoiceClient omniVoiceClient;
    private final SupabaseStorageClient supabaseStorageClient;
    private final OmniVoiceProperties omniVoiceProperties;

    public Generation submit(
            String username, LocalDate date, int page, int perPage, String strategy) {
        log.info("Newsletter: buscando notícias para username={}, date={}, page={}, perPage={}, strategy={}",
                username, date, page, perPage, strategy);
        JsonNode contents = tabNewsService.getUserContents(username, date, page, perPage, strategy);
        log.info("Newsletter: notícias encontradas; gerando roteiro para username={}", username);
        NewsletterScriptService.ScriptResult script = scriptService.generate((com.fasterxml.jackson.databind.node.ArrayNode) contents);
        String code = UUID.randomUUID().toString();
        int counter = jdbcTemplate.queryForObject("""
                select count(*) + 1
                from newsletter_generation
                where requested_date = :date
                """, new MapSqlParameterSource("date", date), Integer.class);
        String audioFilename = "newsletter-%s-%d.wav".formatted(
                date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")), counter);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                insert into newsletter_generation
                    (execution_code, username, requested_date, script, status, created_at, updated_at)
                values (:code, :username, :date, :script, 'PROCESSING', :now, :now)
                """, new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("username", username)
                .addValue("date", date)
                .addValue("script", script.script())
                .addValue("now", Timestamp.from(now)));
        log.info("Newsletter {}: job persistido como PROCESSING (roteiro com {} caracteres).",
                code, script.script().length());
        try {
            log.info("Newsletter {}: enviando roteiro para OmniVoice.", code);
            omniVoiceClient.submit(code, audioFilename, script.script());
            log.info("Newsletter {}: OmniVoice aceitou o job; geração continua em background.", code);
        } catch (RuntimeException exception) {
            log.error("Newsletter {}: falha ao enviar job para OmniVoice.", code, exception);
            markFailed(code, exception.getMessage());
            throw exception;
        }
        return find(code);
    }

    public Generation find(String code) {
        return jdbcTemplate.query("""
                select execution_code, username, requested_date, script, status, audio_path, error,
                       created_at, updated_at
                from newsletter_generation where execution_code = :code
                """, new MapSqlParameterSource("code", code), (rs, rowNum) ->
                new Generation(
                        rs.getString("execution_code"), rs.getString("username"),
                        rs.getObject("requested_date", LocalDate.class), rs.getString("script"),
                        rs.getString("status"), rs.getString("audio_path"), rs.getString("error"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Execução não encontrada: " + code));
    }

    public List<AudioSummary> findCompleted(int limit) {
        return jdbcTemplate.query("""
                select execution_code, username, requested_date, status, audio_path,
                       created_at, updated_at
                from newsletter_generation
                where status = 'COMPLETED'
                  and audio_path is not null
                  and audio_path not like '%/%'
                order by created_at desc
                limit :limit
                """, new MapSqlParameterSource("limit", limit), (rs, rowNum) ->
                new AudioSummary(
                        rs.getString("execution_code"), rs.getString("username"),
                        rs.getObject("requested_date", LocalDate.class),
                        rs.getString("status"), rs.getString("audio_path"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
    }

    private void markFailed(String code, String error) {
        log.warn("Newsletter {}: marcando job como FAILED: {}", code, error);
        jdbcTemplate.update("""
                update newsletter_generation
                   set status = 'FAILED', error = :error, updated_at = :now
                 where execution_code = :code
                """, new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("error", error)
                .addValue("now", Timestamp.from(Instant.now())));
    }

    public byte[] audio(String code) {
        Generation generation = find(code);
        if (!"COMPLETED".equals(generation.status()) || generation.audioPath() == null) {
            throw new IllegalStateException("O áudio ainda não está disponível.");
        }
        byte[] localAudio = readLegacyLocalAudio(generation.audioPath());
        if (localAudio != null) {
            return localAudio;
        }
        try {
            return supabaseStorageClient.download(generation.audioPath());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Não foi possível ler o áudio armazenado.", exception);
        }
    }

    private byte[] readLegacyLocalAudio(String audioPath) {
        if (omniVoiceProperties.audioDirectory() == null
                || omniVoiceProperties.audioDirectory().isBlank()) {
            return null;
        }
        Path audioDirectory = Path.of(omniVoiceProperties.audioDirectory()).toAbsolutePath().normalize();
        Path candidate = audioDirectory.resolve(Path.of(audioPath).getFileName()).normalize();
        if (!candidate.startsWith(audioDirectory) || !Files.isRegularFile(candidate)) {
            return null;
        }
        try {
            return Files.readAllBytes(candidate);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler o áudio local legado.", exception);
        }
    }

    public record Generation(
            String executionCode, String username, LocalDate date, String script, String status,
            String audioPath, String error, Instant createdAt, Instant updatedAt) {
    }

    public record AudioSummary(
            String executionCode, String username, LocalDate date, String status,
            String audioPath, Instant createdAt, Instant updatedAt) {
    }
}
