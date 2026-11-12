package edu.campus.circulation.repository;

import edu.campus.circulation.domain.CategoryStatRow;
import edu.campus.circulation.domain.Exchange;
import edu.campus.circulation.domain.ExchangeMessage;
import edu.campus.circulation.domain.ExchangeStatus;
import edu.campus.circulation.domain.StatsFilter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcExchangeRepository implements ExchangeRepository {
    private static final String EXCHANGE_COLUMNS = """
            SELECT id, item_id, requester_id, status, created_at, completed_at FROM exchanges""";

    private final DatabaseManager database;

    public JdbcExchangeRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public long insertWithFirstMessage(long itemId, long requesterId, String firstMessageBody, Instant now) {
        return database.inWriteTransaction(() -> {
            long exchangeId = database.executeWithConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO exchanges (item_id, requester_id, status, created_at)
                        VALUES (?, ?, 'PENDING', ?) RETURNING id
                        """)) {
                    statement.setLong(1, itemId);
                    statement.setLong(2, requesterId);
                    statement.setString(3, UtcTimestamps.format(now));
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        return result.getLong(1);
                    }
                }
            });
            insertMessage(exchangeId, requesterId, firstMessageBody, now);
            return exchangeId;
        });
    }

    @Override
    public long insertMessage(long exchangeId, long senderId, String body, Instant sentAt) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO exchange_messages (exchange_id, sender_id, body, sent_at)
                    VALUES (?, ?, ?, ?) RETURNING id
                    """)) {
                statement.setLong(1, exchangeId);
                statement.setLong(2, senderId);
                statement.setString(3, body);
                statement.setString(4, UtcTimestamps.format(sentAt));
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
    }

    @Override
    public Optional<Exchange> findById(long id) {
        return database.executeWithConnection(connection ->
                readExchanges(connection, EXCHANGE_COLUMNS + " WHERE id = ?", id).stream().findFirst());
    }

    @Override
    public List<ExchangeMessage> findMessages(long exchangeId) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, exchange_id, sender_id, body, sent_at
                    FROM exchange_messages WHERE exchange_id = ? ORDER BY id ASC
                    """)) {
                statement.setLong(1, exchangeId);
                List<ExchangeMessage> messages = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        messages.add(new ExchangeMessage(result.getLong("id"), result.getLong("exchange_id"),
                                result.getLong("sender_id"), result.getString("body"),
                                UtcTimestamps.parse(result.getString("sent_at"))));
                    }
                }
                return List.copyOf(messages);
            }
        });
    }

    @Override
    public List<Exchange> findByParticipant(long userId) {
        return database.executeWithConnection(connection -> readExchanges(connection, EXCHANGE_COLUMNS + """
                 WHERE requester_id = ? OR item_id IN (SELECT id FROM items WHERE owner_id = ?)
                ORDER BY created_at DESC, id DESC""", userId, userId));
    }

    @Override
    public List<Exchange> findByItem(long itemId) {
        return database.executeWithConnection(connection -> readExchanges(connection, EXCHANGE_COLUMNS
                + " WHERE item_id = ? ORDER BY created_at DESC, id DESC", itemId));
    }

    @Override
    public boolean hasAcceptedExchangeForUser(long userId) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT 1 FROM exchanges WHERE status = 'ACCEPTED'
                    AND (requester_id = ? OR item_id IN (SELECT id FROM items WHERE owner_id = ?)) LIMIT 1
                    """)) {
                statement.setLong(1, userId);
                statement.setLong(2, userId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    @Override
    public boolean transitionStatus(long exchangeId, ExchangeStatus expectedOld, ExchangeStatus newStatus,
                                    Instant completedAt) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE exchanges SET status = ?, completed_at = ? WHERE id = ? AND status = ?
                    """)) {
                statement.setString(1, newStatus.name());
                statement.setString(2, completedAt == null ? null : UtcTimestamps.format(completedAt));
                statement.setLong(3, exchangeId);
                statement.setString(4, expectedOld.name());
                return statement.executeUpdate() == 1;
            }
        });
    }

    @Override
    public int rejectOtherPendingByItem(long itemId, long excludedExchangeId) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE exchanges SET status = 'REJECTED'
                    WHERE item_id = ? AND status = 'PENDING' AND id <> ?
                    """)) {
                statement.setLong(1, itemId);
                statement.setLong(2, excludedExchangeId);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public int cancelPendingByUser(long userId) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE exchanges SET status = 'CANCELLED' WHERE status = 'PENDING'
                    AND (requester_id = ? OR item_id IN (SELECT id FROM items WHERE owner_id = ?))
                    """)) {
                statement.setLong(1, userId);
                statement.setLong(2, userId);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public int cancelPendingByItem(long itemId) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE exchanges SET status = 'CANCELLED' WHERE status = 'PENDING' AND item_id = ?
                    """)) {
                statement.setLong(1, itemId);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public List<CategoryStatRow> summarizeCompletedByCategory(StatsFilter filter) {
        return database.executeWithConnection(connection -> {
            StringBuilder sql = new StringBuilder("""
                    SELECT c.id, c.name, COUNT(*) AS completed_count, c.avoided_grams, c.factor_source
                    FROM exchanges e JOIN items i ON i.id = e.item_id JOIN categories c ON c.id = i.category_id
                    WHERE e.status = 'COMPLETED'""");
            List<Object> parameters = new ArrayList<>();
            if (filter.fromInclusive() != null) {
                sql.append(" AND e.completed_at >= ?");
                parameters.add(UtcTimestamps.format(filter.fromInclusive()));
            }
            if (filter.toExclusive() != null) {
                sql.append(" AND e.completed_at < ?");
                parameters.add(UtcTimestamps.format(filter.toExclusive()));
            }
            if (filter.categoryId() != null) {
                sql.append(" AND i.category_id = ?");
                parameters.add(filter.categoryId());
            }
            if (filter.participantUserId() != null) {
                sql.append(" AND (e.requester_id = ? OR i.owner_id = ?)");
                parameters.add(filter.participantUserId());
                parameters.add(filter.participantUserId());
            }
            sql.append(" GROUP BY c.id, c.name, c.avoided_grams, c.factor_source ORDER BY c.id ASC");
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int index = 0; index < parameters.size(); index++) {
                    Object value = parameters.get(index);
                    if (value instanceof Long longValue) {
                        statement.setLong(index + 1, longValue);
                    } else {
                        statement.setString(index + 1, (String) value);
                    }
                }
                List<CategoryStatRow> rows = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        long grams = result.getLong("avoided_grams");
                        Long avoidedGrams = result.wasNull() ? null : grams;
                        rows.add(new CategoryStatRow(result.getLong("id"), result.getString("name"),
                                result.getLong("completed_count"), avoidedGrams, result.getString("factor_source")));
                    }
                }
                return List.copyOf(rows);
            }
        });
    }

    private static List<Exchange> readExchanges(Connection connection, String sql, long... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setLong(index + 1, parameters[index]);
            }
            List<Exchange> exchanges = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String completedAt = result.getString("completed_at");
                    exchanges.add(new Exchange(result.getLong("id"), result.getLong("item_id"),
                            result.getLong("requester_id"), ExchangeStatus.valueOf(result.getString("status")),
                            UtcTimestamps.parse(result.getString("created_at")),
                            completedAt == null ? null : UtcTimestamps.parse(completedAt)));
                }
            }
            return List.copyOf(exchanges);
        }
    }
}
