package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exchange-rate")
public record ExchangeRateProperties(double fallbackUsdToBrl) {}
