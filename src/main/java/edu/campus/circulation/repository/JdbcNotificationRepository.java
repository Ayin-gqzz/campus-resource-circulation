package edu.campus.circulation.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class JdbcNotificationRepository {
    private final DatabaseManager database;

    public JdbcNotificationRepository(DatabaseManager database) {
        this.database = database;
    }

    /** Called in the same write transaction as reopening the item. */
    public void recordReopened(long exchangeId, long itemId, long actorId, long ownerId, String title, Instant now) {
        database.executeWithConnection(connection -> {
            try (var recipients = connection.prepareStatement("""
                    SELECT DISTINCT requester_id AS recipient FROM exchanges
                    WHERE item_id = ?
                    UNION SELECT ? AS recipient
                    """)) {
                recipients.setLong(1, itemId);
                recipients.setLong(2, ownerId);
                try (var rows = recipients.executeQuery();
                        var insert = connection.prepareStatement("""
                        INSERT INTO notifications (recipient_id, item_id, cause_exchange_id, body, created_at)
                        SELECT id, ?, ?, ?, ? FROM users WHERE id = ? AND enabled = 1
                        ON CONFLICT(cause_exchange_id, recipient_id) DO NOTHING
                        """)) {
                    while (rows.next()) {
                        long recipient = rows.getLong("recipient");
                        if (recipient == actorId) continue;
                        String body = recipient == ownerId
                                ? "「" + title + "」的预定已由对方取消，物品已重新上架。"
                                : "你曾提交意向的「" + title + "」预定已取消，物品已重新上架，可查看后重新提交意向。";
                        insert.setLong(1, itemId);
                        insert.setLong(2, exchangeId);
                        insert.setString(3, body);
                        insert.setString(4, UtcTimestamps.format(now));
                        insert.setLong(5, recipient);
                        insert.executeUpdate();
                    }
                }
            }
            return null;
        });
    }

    public List<Notice> inbox(long recipientId) {
        return database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    SELECT n.*, i.title, (i.status = 'AVAILABLE' AND u.enabled = 1) AS available
                    FROM notifications n JOIN items i ON i.id = n.item_id
                    JOIN users u ON u.id = i.owner_id
                    WHERE n.recipient_id = ? ORDER BY n.created_at DESC, n.id DESC
                    """)) {
                statement.setLong(1, recipientId);
                List<Notice> result = new ArrayList<>();
                try (var row = statement.executeQuery()) {
                    while (row.next()) {
                        String readAt = row.getString("read_at");
                        result.add(new Notice(row.getLong("id"), row.getLong("item_id"), row.getString("title"),
                                row.getString("body"), UtcTimestamps.parse(row.getString("created_at")),
                                readAt == null ? null : UtcTimestamps.parse(readAt), row.getBoolean("available")));
                    }
                }
                return List.copyOf(result);
            }
        });
    }

    public boolean markRead(long id, long recipientId, Instant now) {
        return database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE notifications SET read_at = coalesce(read_at, ?)
                    WHERE id = ? AND recipient_id = ?
                    """)) {
                statement.setString(1, UtcTimestamps.format(now));
                statement.setLong(2, id);
                statement.setLong(3, recipientId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public record Notice(long id, long itemId, String itemTitle, String body, Instant createdAt,
                         Instant readAt, boolean available) {}
}
