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
        List<String> tags,
        int quantity,
        CampusBuilding building) {
    public ItemDraft(long categoryId, String title, String description, ItemCondition condition, TradeType tradeType,
            Long priceCents, String swapNote, List<String> tags, int quantity) {
        this(categoryId, title, description, condition, tradeType, priceCents, swapNote, tags, quantity, null);
    }

    public ItemDraft(long categoryId, String title, String description, ItemCondition condition, TradeType tradeType,
            Long priceCents, String swapNote, List<String> tags) {
        this(categoryId, title, description, condition, tradeType, priceCents, swapNote, tags, 1);
    }
}
