package edu.campus.circulation.domain;

import java.time.Instant;

public record Review(
        long exchangeId,
        long reviewerId,
        long revieweeId,
        int stars,
        String comment,
        Instant createdAt) {
}
