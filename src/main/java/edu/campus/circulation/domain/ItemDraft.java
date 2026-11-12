package edu.campus.circulation.domain;

import java.util.List;

public record ItemDraft(
        long categoryId,
        String title,
        String description,
        ItemCondition condition,
        TradeType tradeType,
        Long priceCents,
        String swapNote,
        List<String> tags) {
}
