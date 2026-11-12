package edu.campus.circulation.repository;

import edu.campus.circulation.domain.Item;
import edu.campus.circulation.domain.ItemCondition;
import edu.campus.circulation.domain.ItemDraft;
import edu.campus.circulation.domain.ItemSearchFilter;
import edu.campus.circulation.domain.ItemStatus;
import edu.campus.circulation.domain.TradeType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class JdbcItemRepository implements ItemRepository {
    private static final String ITEM_COLUMNS = """
            SELECT i.id, i.owner_id, i.category_id, i.title, i.description, i.condition,
                   i.trade_type, i.price_cents, i.swap_note, i.status, i.created_at
            FROM items i""";

    private final DatabaseManager database;

    public JdbcItemRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public long insert(long ownerId, ItemDraft draft, Instant createdAt) {
        List<String> tags = normalizeTags(draft.tags());
        return database.inWriteTransaction(() -> database.executeWithConnection(connection -> {
            long itemId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO items (owner_id, category_id, title, description, condition, trade_type,
                                       price_cents, swap_note, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?) RETURNING id
                    """)) {
                statement.setLong(1, ownerId);
                bindEditableFields(statement, 2, draft);
                statement.setString(9, UtcTimestamps.format(createdAt));
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    itemId = result.getLong(1);
                }
            }
            insertTags(connection, itemId, tags);
            return itemId;
        }));
    }

    @Override
    public boolean updateEditableFields(long itemId, ItemStatus expectedStatus, ItemDraft draft) {
        List<String> tags = normalizeTags(draft.tags());
        return database.inWriteTransaction(() -> database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE items
                    SET category_id = ?, title = ?, description = ?, condition = ?,
                        trade_type = ?, price_cents = ?, swap_note = ?
                    WHERE id = ? AND status = ?
                    """)) {
                bindEditableFields(statement, 1, draft);
                statement.setLong(8, itemId);
                statement.setString(9, expectedStatus.name());
                if (statement.executeUpdate() != 1) {
                    return false;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM item_tags WHERE item_id = ?")) {
                statement.setLong(1, itemId);
                statement.executeUpdate();
            }
            insertTags(connection, itemId, tags);
            return true;
        }));
    }

    @Override
    public boolean updateStatus(long itemId, ItemStatus expectedStatus, ItemStatus newStatus) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE items SET status = ? WHERE id = ? AND status = ?")) {
                statement.setString(1, newStatus.name());
                statement.setLong(2, itemId);
                statement.setString(3, expectedStatus.name());
                return statement.executeUpdate() == 1;
            }
        });
    }

    @Override
    public Optional<Item> findById(long id) {
        List<Item> items = database.executeWithConnection(
                connection -> readItems(connection, ITEM_COLUMNS + " WHERE i.id = ?", id));
        return items.stream().findFirst();
    }

    @Override
    public List<Item> findByOwner(long ownerId) {
        return database.executeWithConnection(connection -> readItems(connection,
                ITEM_COLUMNS + " WHERE i.owner_id = ? ORDER BY i.created_at DESC, i.id DESC", ownerId));
    }

    @Override
    public List<Item> searchAvailable(ItemSearchFilter filter) {
        return database.executeWithConnection(connection -> {
            StringBuilder sql = new StringBuilder(ITEM_COLUMNS)
                    .append(" JOIN users u ON u.id = i.owner_id")
                    .append(" WHERE i.status = 'AVAILABLE' AND u.enabled = 1");
            List<Object> parameters = new ArrayList<>();
            if (filter.categoryId() != null) {
                sql.append(" AND i.category_id = ?");
                parameters.add(filter.categoryId());
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String pattern = "%" + escapeLike(filter.keyword()) + "%";
                sql.append(" AND (i.title LIKE ? ESCAPE '\\' OR i.description LIKE ? ESCAPE '\\')");
                parameters.add(pattern);
                parameters.add(pattern);
            }
            if (filter.minPriceCents() != null || filter.maxPriceCents() != null) {
                sql.append(" AND i.trade_type = 'SALE'");
                if (filter.minPriceCents() != null) {
                    sql.append(" AND i.price_cents >= ?");
                    parameters.add(filter.minPriceCents());
                }
                if (filter.maxPriceCents() != null) {
                    sql.append(" AND i.price_cents <= ?");
                    parameters.add(filter.maxPriceCents());
                }
            }
            sql.append(" ORDER BY i.created_at DESC, i.id DESC");
            return readItems(connection, sql.toString(), parameters.toArray());
        });
    }

    @Override
    public void addFavorite(long userId, long itemId, Instant createdAt) {
        database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO favorites (user_id, item_id, created_at) VALUES (?, ?, ?)")) {
                statement.setLong(1, userId);
                statement.setLong(2, itemId);
                statement.setString(3, UtcTimestamps.format(createdAt));
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public boolean removeFavorite(long userId, long itemId) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM favorites WHERE user_id = ? AND item_id = ?")) {
                statement.setLong(1, userId);
                statement.setLong(2, itemId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    @Override
    public boolean isFavorite(long userId, long itemId) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM favorites WHERE user_id = ? AND item_id = ?")) {
                statement.setLong(1, userId);
                statement.setLong(2, itemId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    @Override
    public List<Item> findFavoriteItems(long userId) {
        return database.executeWithConnection(connection -> readItems(connection, ITEM_COLUMNS + """
                 JOIN favorites f ON f.item_id = i.id
                WHERE f.user_id = ?
                ORDER BY f.created_at DESC, i.id DESC""", userId));
    }

    private static List<Item> readItems(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             PreparedStatement tagStatement = connection.prepareStatement(
                     "SELECT tag FROM item_tags WHERE item_id = ? ORDER BY tag")) {
            bindParameters(statement, parameters);
            List<Item> items = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    items.add(mapItem(result, tagStatement));
                }
            }
            return List.copyOf(items);
        }
    }

    private static void bindParameters(PreparedStatement statement, Object[] parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            Object value = parameters[index];
            if (value instanceof Long longValue) {
                statement.setLong(index + 1, longValue);
            } else {
                statement.setString(index + 1, (String) value);
            }
        }
    }

    private static void bindEditableFields(PreparedStatement statement, int startIndex, ItemDraft draft)
            throws SQLException {
        statement.setLong(startIndex, draft.categoryId());
        statement.setString(startIndex + 1, draft.title());
        statement.setString(startIndex + 2, draft.description());
        statement.setString(startIndex + 3, draft.condition().name());
        statement.setString(startIndex + 4, draft.tradeType().name());
        if (draft.priceCents() == null) {
            statement.setNull(startIndex + 5, Types.BIGINT);
        } else {
            statement.setLong(startIndex + 5, draft.priceCents());
        }
        statement.setString(startIndex + 6, draft.swapNote());
    }

    private static void insertTags(Connection connection, long itemId, List<String> tags) throws SQLException {
        if (tags.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO item_tags (item_id, tag) VALUES (?, ?)")) {
            for (String tag : tags) {
                statement.setLong(1, itemId);
                statement.setString(2, tag);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** Trims tags and drops case-insensitive duplicates while keeping the first spelling seen. */
    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Map<String, String> uniqueTags = new LinkedHashMap<>();
        for (String tag : tags) {
            String trimmed = tag.trim();
            uniqueTags.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return List.copyOf(uniqueTags.values());
    }

    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Item mapItem(ResultSet result, PreparedStatement tagStatement) throws SQLException {
        long id = result.getLong("id");
        long storedPriceCents = result.getLong("price_cents");
        Long priceCents = result.wasNull() ? null : storedPriceCents;
        List<String> tags = new ArrayList<>();
        tagStatement.setLong(1, id);
        try (ResultSet tagResult = tagStatement.executeQuery()) {
            while (tagResult.next()) {
                tags.add(tagResult.getString(1));
            }
        }
        return new Item(id, result.getLong("owner_id"), result.getLong("category_id"),
                result.getString("title"), result.getString("description"),
                ItemCondition.valueOf(result.getString("condition")),
                TradeType.valueOf(result.getString("trade_type")),
                priceCents, result.getString("swap_note"),
                ItemStatus.valueOf(result.getString("status")),
                List.copyOf(tags),
                UtcTimestamps.parse(result.getString("created_at")));
    }
}
