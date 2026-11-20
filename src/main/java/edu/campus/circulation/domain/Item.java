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
        Instant createdAt,
        MeetupLocation meetupLocation,
        Instant meetupAt,
        Inventory inventory) {

    public Item(long id, long ownerId, long categoryId, String title, String description, ItemCondition condition,
            TradeType tradeType, Long priceCents, String swapNote, ItemStatus status, List<String> tags,
            Instant createdAt, MeetupLocation meetupLocation, Instant meetupAt) {
        this(id, ownerId, categoryId, title, description, condition, tradeType, priceCents, swapNote, status, tags,
                createdAt, meetupLocation, meetupAt, new Inventory(1, 0, 0));
    }

    public Item(long id, long ownerId, long categoryId, String title, String description, ItemCondition condition,
            TradeType tradeType, Long priceCents, String swapNote, ItemStatus status, List<String> tags,
            Instant createdAt, MeetupLocation meetupLocation) {
        this(id, ownerId, categoryId, title, description, condition, tradeType, priceCents, swapNote, status, tags,
                createdAt, meetupLocation, null);
    }

    public Item(long id, long ownerId, long categoryId, String title, String description, ItemCondition condition,
            TradeType tradeType, Long priceCents, String swapNote, ItemStatus status, List<String> tags,
            Instant createdAt) {
        this(id, ownerId, categoryId, title, description, condition, tradeType, priceCents, swapNote, status, tags,
                createdAt, null, null);
    }
}
