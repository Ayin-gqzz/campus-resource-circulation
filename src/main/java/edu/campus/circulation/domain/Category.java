package edu.campus.circulation.domain;

public record Category(
        long id,
        String name,
        boolean enabled,
        Long avoidedGrams,
        String factorSource) {
}
