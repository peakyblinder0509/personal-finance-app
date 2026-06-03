package com.financetracker.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The JSON body we POST to Claude's Messages API.
 *
 * Shape Anthropic expects:
 * <pre>
 * {
 *   "model": "claude-haiku-4-5-20251001",
 *   "max_tokens": 1024,
 *   "system": "You are a helpful finance assistant...",
 *   "messages": [ { "role": "user", "content": "..." } ]
 * }
 * </pre>
 *
 * Anthropic uses snake_case ({@code max_tokens}); Java prefers camelCase. The
 * {@code @JsonProperty} annotation bridges the two: the Java field is
 * {@code maxTokens}, but Jackson serializes it as {@code max_tokens}.
 */
public record AnthropicMessageRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<Message> messages
) {

    /** A single turn in the conversation. For us it's always one user message. */
    public record Message(String role, String content) {

        public static Message user(String content) {
            return new Message("user", content);
        }
    }
}
