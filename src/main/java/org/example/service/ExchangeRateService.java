package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.example.config.ExchangeRateProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {
    private static final String RATE_URL = "https://api.frankfurter.app/latest?from=USD&to=BRL";
    private final ExchangeRateProperties properties;
    private final RestClient restClient = RestClient.create();

    public ExchangeRate getUsdToBrl() {
        return new ExchangeRate(1.0, properties.fallbackUsdToBrl(), "fallback", true);
    }

    public record ExchangeRate(double amountUsd, double brlPerUsd, String date, boolean fallback) {}
}
