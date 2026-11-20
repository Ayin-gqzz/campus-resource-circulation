package edu.campus.circulation.ui;

import edu.campus.circulation.repository.DemoDatabase;
import edu.campus.circulation.service.DemoService;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static edu.campus.circulation.service.DemoService.Profile.*;
import static org.junit.jupiter.api.Assertions.*;

class ExchangeGroupsTest {
    @TempDir Path directory;

    @Test
    void buyingAndSellingSeparateMyRequestsFromRequestsForMyListings() throws Exception {
        var service = new DemoService(DemoDatabase.open(directory.resolve("sides.db")));
        assertTrue(ExchangeGroups.forSide(service.history(LIN), LIN.id(), ExchangeGroups.Side.BUY).isEmpty());
        long bottle = service.market("保温杯", null).get(0).id();
        service.request(LIN, bottle, "想购买保温杯");
        var records = service.history(LIN);
        var buying = ExchangeGroups.forSide(records, LIN.id(), ExchangeGroups.Side.BUY);
        var selling = ExchangeGroups.forSide(records, LIN.id(), ExchangeGroups.Side.SELL);
        assertEquals(1, buying.size());
        assertEquals(bottle, buying.get(0).itemId());
        assertEquals(4, selling.size());
        assertTrue(selling.stream().allMatch(exchange -> exchange.ownerId() == LIN.id()));
        assertTrue(selling.stream().anyMatch(exchange -> exchange.status() == DemoService.Status.COMPLETED));
        assertTrue(selling.stream().noneMatch(exchange -> exchange.id() == buying.get(0).id()));
        var buyRows = ExchangeGroups.rows(buying, Set.of(bottle));
        assertEquals(2, buyRows.size());
        assertEquals(buying.get(0).id(), buyRows.get(1).exchange().id());
        assertEquals(2, ExchangeGroups.rows(selling, Set.of()).size());
        assertEquals(buying, ExchangeGroups.forSide(records, LIN.id(), ExchangeGroups.Side.BUY));
        var chen = service.history(CHEN);
        assertEquals(2, ExchangeGroups.forSide(chen, CHEN.id(), ExchangeGroups.Side.BUY).size());
        var chenSelling = ExchangeGroups.forSide(chen, CHEN.id(), ExchangeGroups.Side.SELL);
        assertEquals(1, chenSelling.size());
        assertEquals(buying.get(0).id(), chenSelling.get(0).id());
        assertTrue(ExchangeGroups.forSide(records, 9999, ExchangeGroups.Side.BUY).isEmpty());
        assertTrue(ExchangeGroups.forSide(records, 9999, ExchangeGroups.Side.SELL).isEmpty());
    }

    @Test
    void collapsedHeadingsCannotTargetAPersonAndExpandedChildrenKeepTheirOriginalExchange() throws Exception {
        var service = new DemoService(DemoDatabase.open(directory.resolve("groups.db")));
        var records = service.history(LIN);
        long bicycle = service.market("多人意向演示", null).get(0).id();
        var collapsed = ExchangeGroups.rows(records, Set.of());
        assertEquals(2, collapsed.size());
        assertTrue(collapsed.stream().allMatch(ExchangeGroups.Row::heading));
        var bicycleGroup = collapsed.stream().filter(row -> row.itemId() == bicycle).findFirst().orElseThrow();
        assertEquals(3, bicycleGroup.count());
        assertEquals(3, bicycleGroup.pending());
        assertNull(bicycleGroup.exchange());
        var expanded = ExchangeGroups.rows(records, Set.of(bicycle));
        assertEquals(5, expanded.size());
        assertEquals(records.stream().filter(e -> e.itemId() == bicycle).map(e -> e.id()).toList(),
                expanded.stream().filter(row -> !row.heading()).map(row -> row.exchange().id()).toList());
        assertEquals(3, expanded.stream().filter(row -> !row.heading())
                .map(row -> row.exchange().requesterId()).distinct().count());
        assertEquals(collapsed, ExchangeGroups.rows(records, Set.of()));
        var requesterGroups = ExchangeGroups.rows(service.history(CHEN), Set.of(bicycle));
        assertEquals(1, requesterGroups.stream().filter(row -> !row.heading()).count());
        assertTrue(requesterGroups.stream().filter(row -> !row.heading())
                .allMatch(row -> row.exchange().requesterId() == CHEN.id()));
    }
}
