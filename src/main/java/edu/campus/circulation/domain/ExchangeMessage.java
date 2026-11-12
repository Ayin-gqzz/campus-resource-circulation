package edu.campus.circulation.domain;

import java.time.Instant;

public record ExchangeMessage(
        long id,
        long exchangeId,
        long senderId,
        String body,
        Instant sentAt) {
}
