package com.financetracker.exception;

/**
 * Thrown by {@link com.financetracker.ai.AnthropicClient} when a call to Claude
 * fails for ANY reason: the key is missing, the network is down, Claude returns
 * a non-2xx status, or the response can't be parsed.
 *
 * Note what we DON'T do: we don't let this bubble up to the user as a 500. The
 * AI service layer catches it and returns a friendly fallback message instead,
 * so a Claude outage degrades one feature gracefully rather than failing the
 * request. (Contrast with ResourceNotFoundException/UnauthorizedException, which
 * ARE meant to reach the client as 404/403.)
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
