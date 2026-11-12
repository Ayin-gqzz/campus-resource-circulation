package edu.campus.circulation.domain;

public record CategoryStatRow(
        long categoryId,
        String categoryName,
        long completedCount,
        Long avoidedGramsPerItem,
        String factorSource) {
}
