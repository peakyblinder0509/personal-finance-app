package com.financetracker.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The JSON body Claude sends back. We only model the bits we use; everything
 * else (id, usage, stop_reason, ...) is ignored thanks to
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} — that way Anthropic can
 * add new response fields without breaking our deserialization.
 *
 * Shape:
 * <pre>
 * {
 *   "content": [ { "type": "text", "text": "You spent $340 on food..." } ],
 *   ...
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicMessageResponse(List<ContentBlock> content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String text) {}

    /**
     * Pulls the assistant's plain-text reply out of the (possibly multi-block)
     * content array. Returns null if the response had no text block.
     */
    public String firstText() {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return content.stream()
                .filter(block -> "text".equals(block.type()))
                .map(ContentBlock::text)
                .findFirst()
                .orElse(null);
    }
}
