/**
 * Integration with Anthropic's Claude API lives here.
 *
 * This package is the "gateway" to an external service — the AI equivalent of a
 * repository. Just as a repository is the only place that talks to the database,
 * {@link com.financetracker.ai.AnthropicClient} is the only place that talks to
 * Claude over HTTP. Everything above it (services, controllers) stays unaware of
 * URLs, headers, JSON shapes or retries.
 *
 * Why a separate package and not {@code dto}?
 *   The records here ({@link com.financetracker.ai.AnthropicMessageRequest},
 *   {@link com.financetracker.ai.AnthropicMessageResponse}) model Anthropic's
 *   *wire format* — the exact JSON Claude expects and returns. They are NOT our
 *   public API contract, so they don't belong with the DTOs in {@code dto}.
 *   Keeping them here means: if Anthropic changes its JSON, only this package
 *   changes.
 */
package com.financetracker.ai;
