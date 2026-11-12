package edu.campus.circulation.domain;

public record CreditSummary(
        long userId,
        int reviewCount,
        Double averageStars) {
}
