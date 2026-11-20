package edu.campus.circulation.repository;

import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import edu.campus.circulation.service.DemoService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static edu.campus.circulation.service.DemoService.Profile.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeTimeMigrationTest {
    @TempDir Path directory;

    @Test
    void preservesOldConditionsAndRequiresMutualConsentToAddAMissingTime() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("v4.db");
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            for (String file : new String[]{"001_initial.sql", "002_meetup_locations.sql",
                    "003_exchange_negotiations.sql", "004_notifications.sql"}) {
                try (var resource = getClass().getResourceAsStream("/database/" + file)) {
                    for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                        if (!sql.isBlank()) statement.execute(sql);
                    }
                }
            }
            statement.executeUpdate("""
                    INSERT INTO users VALUES (1,'student_lin','DEMO_PROFILE','STUDENT',1,'2026-11-01T00:00:00.000Z'),
                    (2,'student_chen','DEMO_PROFILE','STUDENT',1,'2026-11-01T00:00:00.000Z')
                    """);
            statement.executeUpdate("INSERT INTO categories(id,name) VALUES(1,'教材')");
            statement.executeUpdate("INSERT INTO items VALUES(1,1,1,'旧教材','原描述','GOOD','SALE',1500,NULL,'AVAILABLE','2026-11-01T00:00:00.000Z')");
            statement.executeUpdate("INSERT INTO item_meetup_locations VALUES(1,'NANHU','校门口')");
            statement.executeUpdate("INSERT INTO exchanges VALUES(1,1,2,'PENDING','2026-11-01T00:00:00.000Z',NULL)");
            statement.executeUpdate("INSERT INTO exchange_terms VALUES(1,1500,'NANHU','校门口',0)");
            statement.executeUpdate("""
                    INSERT INTO exchange_proposals(exchange_id,proposer_id,base_version,price_cents,campus,location,created_at)
                    VALUES(1,2,0,1200,'NANHU','校门口','2026-11-01T00:00:00.000Z')
                    """);
        }
        var manager = new DatabaseManager(url);
        manager.initialize();
        manager.initialize();
        var service = new DemoService(manager);
        var old = service.history(CHEN).get(0);
        assertNull(old.meetupAt());
        assertNull(old.proposals().get(0).meetupAt());
        assertEquals(1500L, old.priceCents());
        assertEquals(1200L, old.proposals().get(0).priceCents());
        assertThrows(IllegalArgumentException.class, () -> service.transition(LIN, old.id(), DemoService.Action.ACCEPT));
        assertThrows(IllegalArgumentException.class, () -> service.respondToProposal(LIN, old.id(), old.proposals().get(0).id(), true));
        service.withdrawProposal(CHEN, old.id(), old.proposals().get(0).id());
        Instant appointment = Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        service.proposeTerms(CHEN, old.id(), 1500L, new MeetupLocation(Campus.NANHU, "校门口"), appointment);
        assertNull(service.history(CHEN).get(0).meetupAt());
        long proposal = service.history(CHEN).get(0).proposals().get(1).id();
        service.respondToProposal(LIN, old.id(), proposal, true);
        assertEquals(appointment, service.history(CHEN).get(0).meetupAt());
        assertNull(service.market("旧教材", null).get(0).meetupAt());
    }
}
