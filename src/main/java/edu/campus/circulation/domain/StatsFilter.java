package edu.campus.circulation.domain;

import java.time.Instant;

public record StatsFilter(
        Instant fromInclusive,
        Instant toExclusive,
        Long categoryId,
        Long participantUserId) {
}
