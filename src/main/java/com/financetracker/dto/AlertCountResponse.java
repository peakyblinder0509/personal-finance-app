package com.financetracker.dto;

// Wraps the unread-alert count in a small object so the API returns JSON
// ({"count": 3}) instead of a bare number. This keeps the response easy to
// extend later (e.g. adding a per-type breakdown) without changing its shape.
public record AlertCountResponse(long count) {}
