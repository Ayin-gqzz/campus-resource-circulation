package edu.campus.circulation.domain;

import java.time.Instant;

public record Exchange(
        long id,
        long itemId,
        long requesterId,
        ExchangeStatus status,
        Instant createdAt,
        Instant completedAt,
        int quantity) {
    public Exchange(long id, long itemId, long requesterId, ExchangeStatus status, Instant createdAt, Instant completedAt) {
        this(id, itemId, requesterId, status, createdAt, completedAt, 1);
    }
}
