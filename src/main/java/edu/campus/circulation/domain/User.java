package edu.campus.circulation.domain;

import java.time.Instant;

public record User(
        long id,
        String username,
        String passwordHash,
        Role role,
        boolean enabled,
        Instant createdAt) {
}
