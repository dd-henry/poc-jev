package org.newsletter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.newsletter.service.TabNewsService;
import org.newsletter.service.NewsletterScriptService;
import org.newsletter.service.NewsletterGenerationService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/newsletter")
public class NewsletterController {
    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final TabNewsService tabNewsService;
    private final NewsletterScriptService newsletterScriptService;
    private final NewsletterGenerationService newsletterGenerationService;

    public NewsletterController(
            TabNewsService tabNewsService,
            NewsletterScriptService newsletterScriptService,
            NewsletterGenerationService newsletterGenerationService) {
        this.tabNewsService = tabNewsService;
        this.newsletterScriptService = newsletterScriptService;
        this.newsletterGenerationService = newsletterGenerationService;
    }

    @PostMapping("/script")
    public NewsletterGenerationService.Generation generateScriptAndAudio(
            @RequestParam(defaultValue = "NewsletterOficial") String username,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int perPage,
            @RequestParam(defaultValue = "new") String strategy) {
        validateStrategy(strategy);
        LocalDate requestedDate = date == null ? LocalDate.now(BRAZIL_ZONE) : date;
        try {
            return newsletterGenerationService.submit(username, requestedDate, page, perPage, strategy);
        } catch (TabNewsService.TabNewsApiException | NewsletterScriptService.NewsletterAiException exception) {
            throw new ResponseStatusException(
                    exception.getMessage().contains("Nenhuma notícia")
                            ? HttpStatus.UNPROCESSABLE_ENTITY
                            : HttpStatus.BAD_GATEWAY,
                    exception.getMessage(),
                    exception);
        }
    }

    @GetMapping("/script/{executionCode}")
    public NewsletterGenerationService.Generation scriptStatus(@PathVariable String executionCode) {
        try {
            return newsletterGenerationService.find(executionCode);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @GetMapping("/generations")
    public List<NewsletterGenerationService.AudioSummary> completedGenerations(
            @RequestParam(defaultValue = "50") @Min(1) int limit) {
        if (limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit deve ser no máximo 100.");
        }
        return newsletterGenerationService.findCompleted(limit);
    }

    @GetMapping(value = "/script/{executionCode}/audio", produces = "audio/wav")
    public ResponseEntity<ByteArrayResource> scriptAudio(
            @PathVariable String executionCode,
            @RequestParam(defaultValue = "false") boolean download) {
        try {
            NewsletterGenerationService.Generation generation = newsletterGenerationService.find(executionCode);
            String filename = generation.audioPath() != null
                    ? java.nio.file.Path.of(generation.audioPath()).getFileName().toString()
                    : "newsletter-" + executionCode + ".wav";
            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            (download ? "attachment" : "inline")
                                    + "; filename=\"" + filename + "\"")
                    .body(new ByteArrayResource(newsletterGenerationService.audio(executionCode)));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @GetMapping("/contents")
    public JsonNode getContents(
            @RequestParam(defaultValue = "NewsletterOficial") String username,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int perPage,
            @RequestParam(defaultValue = "new") String strategy) {
        validateStrategy(strategy);

        try {
            LocalDate requestedDate = date == null
                    ? LocalDate.now(BRAZIL_ZONE)
                    : date;
            return tabNewsService.getUserContents(username, requestedDate, page, perPage, strategy);
        } catch (TabNewsService.TabNewsApiException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    exception.getMessage(),
                    exception);
        }
    }

    @GetMapping("/script")
    public NewsletterScriptService.ScriptResult generateScript(
            @RequestParam(defaultValue = "NewsletterOficial") String username,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int perPage,
            @RequestParam(defaultValue = "new") String strategy) {
        validateStrategy(strategy);
        try {
            LocalDate requestedDate = date == null ? LocalDate.now(BRAZIL_ZONE) : date;
            return newsletterScriptService.generate(
                    tabNewsService.getUserContents(username, requestedDate, page, perPage, strategy));
        } catch (TabNewsService.TabNewsApiException | NewsletterScriptService.NewsletterAiException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    private void validateStrategy(String strategy) {
        if (!strategy.equals("new") && !strategy.equals("old") && !strategy.equals("relevant")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "strategy deve ser new, old ou relevant.");
        }
    }
}
