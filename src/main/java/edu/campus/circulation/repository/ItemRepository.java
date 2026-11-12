package edu.campus.circulation.repository;

import edu.campus.circulation.domain.Item;
import edu.campus.circulation.domain.ItemDraft;
import edu.campus.circulation.domain.ItemSearchFilter;
import edu.campus.circulation.domain.ItemStatus;
import edu.campus.circulation.domain.MeetupLocation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ItemRepository {
    long insert(long ownerId, ItemDraft draft, Instant createdAt);
    boolean updateEditableFields(long itemId, ItemStatus expectedStatus, ItemDraft draft);
    boolean updateStatus(long itemId, ItemStatus expectedStatus, ItemStatus newStatus);
    boolean setInitialMeetupLocation(long itemId, MeetupLocation location);
    Optional<Item> findById(long id);
    List<Item> findByOwner(long ownerId);
    List<Item> searchAvailable(ItemSearchFilter filter);
    void addFavorite(long userId, long itemId, Instant createdAt);
    boolean removeFavorite(long userId, long itemId);
    boolean isFavorite(long userId, long itemId);
    List<Item> findFavoriteItems(long userId);
}
