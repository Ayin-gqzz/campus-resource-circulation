package edu.campus.circulation.repository;

import edu.campus.circulation.domain.Inventory;
import edu.campus.circulation.domain.ItemStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class InventoryMigrationTest {
    @TempDir Path directory;

    @Test
    void oldListingsBecomeSingleUnitsAndKeepReservationsAndCompletedStock() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("v5.db");
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            for (String file : new String[]{"001_initial.sql", "002_meetup_locations.sql", "003_exchange_negotiations.sql",
                    "004_notifications.sql", "005_meetup_time.sql"}) {
                try (var resource = getClass().getResourceAsStream("/database/" + file)) {
                    for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                        if (!sql.isBlank()) statement.execute(sql);
                    }
                }
            }
            statement.executeUpdate("""
                    INSERT INTO users VALUES (1,'owner','hash','STUDENT',1,'2026-11-01T00:00:00.000Z'),
                    (2,'buyer','hash','STUDENT',1,'2026-11-01T00:00:00.000Z')
                    """);
            statement.executeUpdate("INSERT INTO categories(id,name) VALUES(1,'教材')");
            statement.executeUpdate("""
                    INSERT INTO items VALUES
                    (1,1,1,'在架教材','保留说明','GOOD','SALE',1500,NULL,'AVAILABLE','2026-11-01T00:00:00.000Z'),
                    (2,1,1,'预定教材','保留说明','GOOD','SALE',2000,NULL,'RESERVED','2026-11-01T00:00:00.000Z'),
                    (3,1,1,'完成教材','保留说明','GOOD','SALE',2500,NULL,'COMPLETED','2026-11-01T00:00:00.000Z')
                    """);
            statement.executeUpdate("""
                    INSERT INTO exchanges VALUES (1,2,2,'ACCEPTED','2026-11-01T00:00:00.000Z',NULL),
                    (2,3,2,'COMPLETED','2026-11-01T00:00:00.000Z','2026-11-02T00:00:00.000Z')
                    """);
        }
        var manager = new DatabaseManager(url);
        manager.initialize();
        manager.initialize();
        var items = new JdbcItemRepository(manager);
        assertEquals(new Inventory(1,0,0), items.findById(1).orElseThrow().inventory());
        assertEquals(new Inventory(1,1,0), items.findById(2).orElseThrow().inventory());
        assertEquals(new Inventory(1,0,1), items.findById(3).orElseThrow().inventory());
        assertEquals(ItemStatus.RESERVED, items.findById(2).orElseThrow().status());
        assertNull(items.findById(2).orElseThrow().building());
        assertEquals(ItemStatus.COMPLETED, items.findById(3).orElseThrow().status());
        assertEquals(2000L, items.findById(2).orElseThrow().priceCents());
        assertEquals(1, new JdbcExchangeRepository(manager).findById(1).orElseThrow().quantity());
    }
}
