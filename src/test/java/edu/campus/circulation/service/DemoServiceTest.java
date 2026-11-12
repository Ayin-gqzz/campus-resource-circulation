package edu.campus.circulation.service;

import static edu.campus.circulation.service.DemoService.Profile.CHEN;
import static edu.campus.circulation.service.DemoService.Profile.LIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campus.circulation.repository.DemoDatabase;
import edu.campus.circulation.service.DemoService.Action;
import edu.campus.circulation.service.DemoService.Condition;
import edu.campus.circulation.service.DemoService.Draft;
import edu.campus.circulation.service.DemoService.Status;
import edu.campus.circulation.service.DemoService.TradeType;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoServiceTest {
    @TempDir
    Path directory;

    @Test
    void publishRequestCompleteAndReviewAcrossReopen() throws Exception {
        Path file = directory.resolve("demo.db");
        DemoService service = open(file);
        service.publish(LIN, new Draft(1, "线性代数教材", "保存完好", Condition.GOOD, TradeType.SALE, 1800L, ""));
        var item = service.market("线性代数", 1L).get(0);
        assertEquals(1800L, item.priceCents());
        assertThrows(IllegalArgumentException.class, () -> service.request(LIN, item.id(), "自购"));
        service.request(CHEN, item.id(), "周五可以交接吗？");
        assertThrows(IllegalArgumentException.class, () -> service.request(CHEN, item.id(), "重复请求"));
        var exchange = service.history(LIN).get(0);
        assertEquals("周五可以交接吗？", exchange.message());
        assertThrows(IllegalArgumentException.class, () -> service.transition(CHEN, exchange.id(), Action.ACCEPT));
        assertThrows(IllegalArgumentException.class, () -> service.review(LIN, exchange.id(), 5, "提前评价"));
        service.transition(LIN, exchange.id(), Action.ACCEPT);
        assertTrue(service.market("线性代数", 1L).isEmpty());
        service.transition(LIN, exchange.id(), Action.COMPLETE);
        service.review(CHEN, exchange.id(), 5, "交接顺利");
        service.review(LIN, exchange.id(), 4, "");
        assertThrows(IllegalArgumentException.class, () -> service.review(CHEN, exchange.id(), 4, ""));

        DemoService reopened = open(file);
        var persisted = reopened.history(CHEN).get(0);
        assertEquals(Status.COMPLETED, persisted.status());
        assertEquals(5, persisted.stars());
        assertEquals(4.0, reopened.credit(CHEN).average());
        assertEquals(5.0, reopened.credit(LIN).average());
        assertEquals(3, reopened.market("", null).size());
    }

    @Test
    void cancellingAcceptedSwapMakesItAvailableForAnotherRequest() throws Exception {
        DemoService service = open(directory.resolve("demo.db"));
        var item = service.market("台灯", null).get(0);
        assertEquals(TradeType.SWAP, item.type());
        service.request(CHEN, item.id(), "我有收纳盒。");
        long id = service.history(CHEN).get(0).id();
        service.transition(LIN, id, Action.ACCEPT);
        service.transition(CHEN, id, Action.CANCEL);
        assertEquals(Status.CANCELLED, service.history(LIN).get(0).status());
        assertEquals(item.id(), service.market("台灯", null).get(0).id());
        service.request(CHEN, item.id(), "可以重新约时间吗？");
        assertEquals(Status.PENDING, service.history(LIN).get(0).status());
    }

    private static DemoService open(Path file) throws Exception {
        return new DemoService(DemoDatabase.open(file));
    }
}
