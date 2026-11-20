package edu.campus.circulation.service;

import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import edu.campus.circulation.repository.DemoDatabase;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static edu.campus.circulation.service.DemoService.Profile.*;
import static edu.campus.circulation.service.DemoService.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeTimeTest {
    @TempDir Path directory;

    @Test
    void changingOnlyTheTimeRequiresTheOtherPersonAndSurvivesRestart() throws Exception {
        Path file = directory.resolve("appointment.db");
        var service = new DemoService(DemoDatabase.open(file));
        var book = service.market("高等数学", null).get(0);
        assertNotNull(book.meetupAt());
        service.request(CHEN, book.id(), "约好交接时间");
        var exchange = service.history(CHEN).get(0);
        assertEquals(book.meetupAt(), exchange.meetupAt());
        Instant nextTime = book.meetupAt().plus(1, ChronoUnit.DAYS);
        service.proposeTerms(CHEN, exchange.id(), exchange.priceCents(), exchange.location(), nextTime);
        var pending = service.history(LIN).get(0);
        assertEquals(book.meetupAt(), pending.meetupAt());
        var proposal = pending.proposals().get(0);
        assertEquals(nextTime, proposal.meetupAt());
        assertThrows(IllegalArgumentException.class, () -> service.respondToProposal(CHEN, exchange.id(), proposal.id(), true));
        service.respondToProposal(LIN, exchange.id(), proposal.id(), true);
        var reopened = new DemoService(DemoDatabase.open(file));
        var agreed = reopened.history(CHEN).get(0);
        assertEquals(nextTime, agreed.meetupAt());
        assertEquals(exchange.priceCents(), agreed.priceCents());
        assertEquals(exchange.location(), agreed.location());
        assertEquals(book.meetupAt(), reopened.market("高等数学", null).get(0).meetupAt());
        service.transition(LIN, exchange.id(), ACCEPT);
        service.transition(LIN, exchange.id(), COMPLETE);
        var completed = service.history(CHEN).get(0);
        assertEquals(nextTime, completed.meetupAt());
        assertNotNull(completed.createdAt());
        assertNotNull(completed.completedAt());
        assertNotEquals(nextTime, completed.completedAt());
    }

    @Test
    void rejectsMissingAndPastTimesAndRechecksBeforeAgreement() throws Exception {
        var database = DemoDatabase.open(directory.resolve("invalid.db"));
        var service = new DemoService(database);
        var place = new MeetupLocation(Campus.NANHU, "校门口");
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        var missing = new DemoService.Draft(1, "预约教材", "说明", DemoService.Condition.GOOD,
                DemoService.TradeType.SALE, 1000L, null, place);
        assertThrows(IllegalArgumentException.class, () -> service.publish(LIN, missing));
        var past = new DemoService.Draft(1, "预约教材", "说明", DemoService.Condition.GOOD,
                DemoService.TradeType.SALE, 1000L, null, place, Instant.now().minusSeconds(60));
        assertThrows(IllegalArgumentException.class, () -> service.publish(LIN, past));
        service.publish(LIN, new DemoService.Draft(1, "预约教材", "说明", DemoService.Condition.GOOD,
                DemoService.TradeType.SALE, 1000L, null, place, future));
        var item = service.market("预约教材", null).get(0);
        service.request(CHEN, item.id(), "申请");
        long exchange = service.history(CHEN).get(0).id();
        assertThrows(IllegalArgumentException.class, () -> service.proposeTerms(CHEN, exchange, 1000L, place, null));
        assertThrows(IllegalArgumentException.class, () -> service.proposeTerms(CHEN, exchange, 1000L, place, Instant.now().minusSeconds(1)));
        service.proposeTerms(CHEN, exchange, 1000L, place, future.plusSeconds(3600));
        long proposal = service.history(CHEN).get(0).proposals().get(0).id();
        // Simulate the appointment passing while a reply is pending.
        database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement("UPDATE exchange_proposals SET meetup_at = '2000-01-01T00:00:00.000Z' WHERE id = ?")) {
                statement.setLong(1, proposal);
                statement.executeUpdate();
            }
            return null;
        });
        assertThrows(IllegalArgumentException.class, () -> service.respondToProposal(LIN, exchange, proposal, true));
        assertEquals(future, service.history(CHEN).get(0).meetupAt());
        assertEquals("PENDING", service.history(CHEN).get(0).proposals().get(0).status());
        service.respondToProposal(LIN, exchange, proposal, false);
    }
}
