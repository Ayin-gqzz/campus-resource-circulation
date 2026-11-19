package edu.campus.circulation.repository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NegotiationMigrationTest {
    @TempDir Path directory;

    @Test
    void upgradesVersionTwoAndKeepsTheOriginalMessageAndTransactionConditions() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("old.db");
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            for (String file : new String[]{"001_initial.sql", "002_meetup_locations.sql"}) {
                try (var resource = getClass().getResourceAsStream("/database/" + file)) {
                    for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                        if (!sql.isBlank()) statement.execute(sql);
                    }
                }
            }
            statement.executeUpdate("""
                    INSERT INTO users VALUES
                    (1, 'owner', 'hash', 'STUDENT', 1, '2026-11-12T00:00:00.000Z'),
                    (2, 'buyer', 'hash', 'STUDENT', 1, '2026-11-12T00:00:00.000Z')
                    """);
            statement.executeUpdate("INSERT INTO categories (id, name) VALUES (1, '教材')");
            statement.executeUpdate("""
                    INSERT INTO items VALUES
                    (1, 1, 1, '旧教材', '说明', 'GOOD', 'SALE', 1500, NULL, 'RESERVED', '2026-11-12T00:00:00.000Z')
                    """);
            statement.executeUpdate("INSERT INTO item_meetup_locations VALUES (1, 'NANHU', '校门口')");
            statement.executeUpdate("""
                    INSERT INTO exchanges VALUES (1, 1, 2, 'ACCEPTED', '2026-11-12T00:00:00.000Z', NULL)
                    """);
            statement.executeUpdate("""
                    INSERT INTO exchange_messages VALUES (1, 1, 2, '保留的留言', '2026-11-12T00:00:00.000Z')
                    """);
        }
        var manager = new DatabaseManager(url);
        manager.initialize();
        manager.initialize();
        var terms = new JdbcNegotiationRepository(manager).terms(1);
        assertEquals(1500L, terms.priceCents());
        assertEquals("南湖校区 · 校门口", terms.location().displayName());
        assertEquals(0, terms.version());
        assertNull(terms.meetupAt());
        assertEquals("保留的留言", new JdbcExchangeRepository(manager).findMessages(1).get(0).body());
        assertEquals(edu.campus.circulation.domain.ExchangeStatus.ACCEPTED,
                new JdbcExchangeRepository(manager).findById(1).orElseThrow().status());
    }
}
