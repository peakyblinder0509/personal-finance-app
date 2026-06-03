package com.financetracker.ai;

import com.financetracker.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * The ONLY class that knows how to talk to Claude over HTTP.
 *
 * Responsibilities:
 *   - hold the API key + endpoint config (from environment variables)
 *   - build the request headers/body in Anthropic's format
 *   - make the call and pull out the assistant's text
 *   - translate every possible failure into a single {@link AiServiceException}
 *
 * It deliberately knows NOTHING about budgets, transactions or prompts — it just
 * sends a system + user message and returns text. That separation keeps the
 * "how do we call Claude" concern out of the "what do we ask Claude" concern
 * (which lives in {@code AiService}).
 */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;
    private final String anthropicVersion;
    private final String model;
    private final int maxTokens;

    // Constructor injection (project rule: never @Autowired on fields).
    // @Qualifier picks the timeout-configured RestTemplate from AiConfig, not just
    // any RestTemplate that might exist in the context.
    public AnthropicClient(
            @Qualifier("anthropicRestTemplate") RestTemplate restTemplate,
            @Value("${app.anthropic.api-key}") String apiKey,
            @Value("${app.anthropic.base-url}") String baseUrl,
            @Value("${app.anthropic.version}") String anthropicVersion,
            @Value("${app.anthropic.model}") String model,
            @Value("${app.anthropic.max-tokens}") int maxTokens) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.anthropicVersion = anthropicVersion;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /**
     * Sends one system + user prompt to Claude and returns the plain-text reply.
     *
     * @throws AiServiceException if the key is missing, the call fails, Claude
     *                            returns a non-2xx status, or the body is empty.
     */
    public String complete(String systemPrompt, String userPrompt) {
        // Fail fast (and clearly) if the key was never configured. We check
        // isBlank() rather than just null because the default in application.properties
        // is an empty string.
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiServiceException("ANTHROPIC_API_KEY is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);                 // authentication
        headers.set("anthropic-version", anthropicVersion); // required by the API

        AnthropicMessageRequest body = new AnthropicMessageRequest(
                model,
                maxTokens,
                systemPrompt,
                List.of(AnthropicMessageRequest.Message.user(userPrompt)));

        try {
            // postForObject sends the body as JSON and deserializes the JSON reply
            // straight into our response record.
            AnthropicMessageResponse response = restTemplate.postForObject(
                    baseUrl,
                    new HttpEntity<>(body, headers),
                    AnthropicMessageResponse.class);

            String text = response != null ? response.firstText() : null;
            if (text == null || text.isBlank()) {
                throw new AiServiceException("Claude returned an empty response");
            }
            log.debug("Claude call succeeded ({} chars returned)", text.length());
            return text.trim();

        } catch (RestClientException e) {
            // Covers timeouts, connection refused, 4xx/5xx from Claude, parse errors.
            // We log the cause server-side but never the API key or the user's prompt.
            log.warn("Claude API call failed: {}", e.getMessage());
            throw new AiServiceException("Claude API call failed", e);
        }
    }
}
