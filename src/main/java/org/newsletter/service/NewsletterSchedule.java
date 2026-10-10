package org.newsletter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsletterSchedule {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final String USERNAME = "NewsletterOficial";

    private final NewsletterGenerationService generationService;

    @Scheduled(cron = "0 30 11 * * MON-FRI", zone = "America/Sao_Paulo")
    public void generateDailyNewsletter() {
        LocalDate today = LocalDate.now(SAO_PAULO);
        try {
            NewsletterGenerationService.Generation generation =
                    generationService.submit(USERNAME, today, 1, 100, "new");
            log.info("Geração diária da newsletter iniciada: executionCode={}, date={}, status={}",
                    generation.executionCode(), today, generation.status());
        } catch (RuntimeException exception) {
            log.error("Falha na geração diária da newsletter para {}.", today, exception);
        }
    }
}
