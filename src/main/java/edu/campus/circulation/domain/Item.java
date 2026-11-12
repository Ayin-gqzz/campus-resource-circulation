package edu.campus.circulation.domain;

import java.time.Instant;
import java.util.List;

public record Item(
        long id,
        long ownerId,
        long categoryId,
        String title,
        String description,
        ItemCondition condition,
        TradeType tradeType,
        Long priceCents,
        String swapNote,
        ItemStatus status,
        List<String> tags,
        Instant createdAt) {
}
