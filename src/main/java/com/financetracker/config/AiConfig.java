package com.financetracker.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Builds the {@link RestTemplate} used to call Claude.
 *
 * We give it its OWN bean (named "anthropicRestTemplate") with explicit timeouts
 * rather than reusing a shared, default RestTemplate. The default has NO read
 * timeout, which means a slow or hung Claude call could block one of our HTTP
 * worker threads forever. With timeouts, a stuck call fails fast and we fall back.
 */
@Configuration
public class AiConfig {

    @Bean
    public RestTemplate anthropicRestTemplate(
            RestTemplateBuilder builder,
            // Pulled from application.properties (which reads them from env vars).
            // Constructor/builder config, NOT @Autowired field injection — same rule
            // the rest of the project follows.
            @org.springframework.beans.factory.annotation.Value("${app.anthropic.connect-timeout-ms}") long connectTimeoutMs,
            @org.springframework.beans.factory.annotation.Value("${app.anthropic.read-timeout-ms}") long readTimeoutMs) {

        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs)) // time to establish the TCP connection
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))       // time to wait for Claude's response
                .build();
    }
}
