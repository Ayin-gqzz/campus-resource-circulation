package edu.campus.circulation.domain;

import java.time.Instant;

public record Exchange(
        long id,
        long itemId,
        long requesterId,
        ExchangeStatus status,
        Instant createdAt,
        Instant completedAt) {
}
