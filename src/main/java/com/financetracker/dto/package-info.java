/**
 * Data Transfer Objects — request and response shapes live here.
 *
 * A DTO is a simple class whose only purpose is to carry data across a boundary.
 * There are two kinds:
 *
 *   Request DTOs  (e.g. CreateTransactionRequest)
 *     - Represent what the client sends in the request body
 *     - Carry validation annotations: @NotNull, @NotBlank, @Positive, @Email
 *     - Never contain an id field (the client doesn't decide the ID)
 *
 *   Response DTOs  (e.g. TransactionResponse)
 *     - Represent what the API returns as JSON
 *     - Contain exactly what the client needs — no more, no less
 *     - Never expose internal IDs of related objects the client shouldn't see
 *
 * The mapping between Entity ↔ DTO happens in the service layer.
 * You can do this manually or use a library like MapStruct.
 *
 * Using Lombok here:
 *   @Data            — generates getters, setters, equals, hashCode, toString
 *   @Builder         — generates a builder pattern for clean construction
 *   @NoArgsConstructor / @AllArgsConstructor — generate constructors
 */
package com.financetracker.dto;
