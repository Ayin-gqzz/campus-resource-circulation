package edu.campus.circulation.domain;

public record ItemSearchFilter(
        Long categoryId,
        String keyword,
        Long minPriceCents,
        Long maxPriceCents) {
}
