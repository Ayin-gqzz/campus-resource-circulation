package edu.campus.circulation.domain;

import java.time.Instant;

public record Favorite(
        long userId,
        long itemId,
        Instant createdAt) {
}
