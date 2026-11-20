package edu.campus.circulation.service;

import edu.campus.circulation.domain.Inventory;
import edu.campus.circulation.domain.ItemStatus;
import edu.campus.circulation.domain.StatsFilter;
import edu.campus.circulation.repository.DemoDatabase;
import edu.campus.circulation.repository.JdbcExchangeRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static edu.campus.circulation.service.DemoService.Profile.*;
import static edu.campus.circulation.service.DemoService.Action.*;
import static edu.campus.circulation.service.DemoService.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {
    @TempDir Path directory;

    @Test
    void reservesCompletesAndReleasesOnlyTheQuantityInEachOrder() throws Exception {
        Path file = directory.resolve("stock.db");
        var database = DemoDatabase.open(file);
        var service = new DemoService(database);
        var wang = service.profiles().stream().filter(p -> p.label().equals("王同学")).findFirst().orElseThrow();
        var zhou = service.profiles().stream().filter(p -> p.label().equals("周同学")).findFirst().orElseThrow();
        var item = service.market("多件演示", null).get(0);
        assertEquals(new Inventory(5, 0, 0), item.inventory());
        service.request(CHEN, item.id(), "要两本", 2);
        service.request(wang, item.id(), "要一本", 1);
        service.request(zhou, item.id(), "要两本", 2);
        var chen = order(service, CHEN, item.id());
        var other = order(service, wang, item.id());
        var last = order(service, zhou, item.id());
        service.proposeTerms(CHEN, chen.id(), 400L, chen.location(), chen.meetupAt());
        service.respondToProposal(LIN, chen.id(), order(service, CHEN, item.id()).proposals().get(0).id(), true);
        assertEquals(2, order(service, CHEN, item.id()).quantity());
        assertEquals(400L, order(service, CHEN, item.id()).priceCents());
        assertEquals(500L, service.market("多件演示", null).get(0).priceCents());
        service.transition(LIN, chen.id(), ACCEPT);
        assertEquals(new Inventory(5, 2, 0), service.market("多件演示", null).get(0).inventory());
        assertEquals(PENDING, order(service, wang, item.id()).status());
        assertEquals(PENDING, order(service, zhou, item.id()).status());
        service.transition(LIN, other.id(), ACCEPT);
        service.transition(LIN, chen.id(), COMPLETE);
        assertEquals(new Inventory(5, 1, 2), service.market("多件演示", null).get(0).inventory());
        assertTrue(service.notifications(zhou).isEmpty());
        service.transition(LIN, last.id(), ACCEPT);
        assertTrue(service.market("多件演示", null).isEmpty());
        service.transition(wang, other.id(), CANCEL);
        assertEquals(new Inventory(5, 2, 2), service.market("多件演示", null).get(0).inventory());
        assertEquals(1, service.notifications(zhou).size());
        assertEquals(1, service.notifications(CHEN).size());
        assertThrows(IllegalArgumentException.class, () -> service.transition(wang, other.id(), CANCEL));
        assertEquals(1, service.market("多件演示", null).get(0).inventory().available());
        service.transition(LIN, last.id(), COMPLETE);
        var reopened = new DemoService(DemoDatabase.open(file));
        assertEquals(new Inventory(5, 0, 4), reopened.market("多件演示", null).get(0).inventory());
        service.request(wang, item.id(), "最后一本", 1);
        long finalOrder = order(service, wang, item.id()).id();
        service.transition(LIN, finalOrder, ACCEPT);
        service.transition(LIN, finalOrder, COMPLETE);
        assertEquals(new Inventory(5, 0, 5), order(service, wang, item.id()).inventory());
        assertTrue(service.market("多件演示", null).isEmpty());
        assertEquals(ItemStatus.COMPLETED, service.myItems(LIN).stream()
                .filter(i -> i.title().contains("多件演示")).findFirst().orElseThrow().status());
        assertEquals(6, new JdbcExchangeRepository(database).summarizeCompletedByCategory(
                new StatsFilter(null, null, null, null)).stream().mapToLong(row -> row.completedCount()).sum());
    }

    @Test
    void concurrentRequestsCannotReserveMoreThanTheRemainingStock() throws Exception {
        var service = new DemoService(DemoDatabase.open(directory.resolve("race.db")));
        var wang = service.profiles().stream().filter(p -> p.label().equals("王同学")).findFirst().orElseThrow();
        long item = service.market("多件演示", null).get(0).id();
        service.request(CHEN, item, "要三本", 3);
        service.request(wang, item, "也要三本", 3);
        long a = order(service, CHEN, item).id();
        long b = order(service, wang, item).id();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> acceptA = () -> acceptAfter(start, service, a);
            java.util.concurrent.Callable<Boolean> acceptB = () -> acceptAfter(start, service, b);
            var first = executor.submit(acceptA);
            var second = executor.submit(acceptB);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertEquals(new Inventory(5, 3, 0), service.market("多件演示", null).get(0).inventory());
        assertEquals(1, service.history(LIN).stream().filter(e -> e.itemId() == item && e.status() == ACCEPTED).count());
        assertEquals(1, service.history(LIN).stream().filter(e -> e.itemId() == item && e.status() == PENDING).count());
        long accepted = service.history(LIN).stream().filter(e -> e.itemId() == item && e.status() == ACCEPTED).findFirst().orElseThrow().id();
        service.transition(LIN, accepted, CANCEL);
        assertEquals(new Inventory(5, 0, 0), service.market("多件演示", null).get(0).inventory());
        assertTrue(service.notifications(CHEN).isEmpty());
        assertTrue(service.notifications(wang).isEmpty());
    }

    @Test
    void checksQuantityBoundsAndSupportsMultiUnitSwaps() throws Exception {
        var service = new DemoService(DemoDatabase.open(directory.resolve("bounds.db")));
        long item = service.market("多件演示", null).get(0).id();
        assertThrows(IllegalArgumentException.class, () -> service.request(CHEN, item, "零件", 0));
        assertThrows(IllegalArgumentException.class, () -> service.request(CHEN, item, "超过库存", 6));
        var place = service.market("多件演示", null).get(0).location();
        Instant time = Instant.now().plusSeconds(86400);
        assertThrows(IllegalArgumentException.class, () -> service.publish(LIN, new DemoService.Draft(1,
                "非法数量", "说明", DemoService.Condition.NEW, DemoService.TradeType.SALE, 100L, null, place, time, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.publish(LIN, new DemoService.Draft(1,
                "非法数量", "说明", DemoService.Condition.NEW, DemoService.TradeType.SALE, 100L, null, place, time, 10000)));
        service.publish(LIN, new DemoService.Draft(1, "多本换物", "每件为一本", DemoService.Condition.NEW,
                DemoService.TradeType.SWAP, null, "每件换一本便签", place, time, 3));
        long swap = service.market("多本换物", null).get(0).id();
        service.request(CHEN, swap, "换两本", 2);
        long exchange = order(service, CHEN, swap).id();
        service.transition(LIN, exchange, ACCEPT);
        service.transition(LIN, exchange, COMPLETE);
        assertEquals(new Inventory(3, 0, 2), service.market("多本换物", null).get(0).inventory());
    }

    private static DemoService.Exchange order(DemoService service, DemoService.Profile profile, long item) {
        return service.history(profile).stream().filter(e -> e.itemId() == item).findFirst().orElseThrow();
    }

    private static boolean acceptAfter(CountDownLatch start, DemoService service, long exchange) throws Exception {
        start.await();
        try { service.transition(LIN, exchange, ACCEPT); return true; }
        catch (IllegalArgumentException insufficient) { return false; }
    }
}
