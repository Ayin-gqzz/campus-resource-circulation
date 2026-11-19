package edu.campus.circulation.service;

import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import edu.campus.circulation.domain.Role;
import edu.campus.circulation.repository.DemoDatabase;
import edu.campus.circulation.repository.JdbcExchangeRepository;
import edu.campus.circulation.repository.JdbcItemRepository;
import edu.campus.circulation.repository.JdbcNegotiationRepository;
import edu.campus.circulation.repository.JdbcUserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static edu.campus.circulation.service.DemoService.Profile.*;
import static org.junit.jupiter.api.Assertions.*;

class NegotiationTest {
    @TempDir Path directory;

    @Test
    void messagesAndAgreedTermsSurviveReopenWithoutChangingTheListing() throws Exception {
        Path file = directory.resolve("chat.db");
        var database = DemoDatabase.open(file);
        var service = new DemoService(database);
        long item = service.market("高等数学", null).get(0).id();
        service.request(CHEN, item, "下午交接可以吗？");
        long id = service.history(CHEN).get(0).id();
        service.sendMessage(LIN, id, "可以，到南湖校区见。");
        var original = service.history(CHEN).get(0);
        assertEquals(2, original.messages().size());
        assertEquals(LIN.id(), original.messages().get(1).senderId());
        assertNotNull(original.messages().get(1).sentAt());
        var place = new MeetupLocation(Campus.NANHU, "教学楼入口");
        service.proposeTerms(CHEN, id, 1200L, place);
        var proposed = service.history(LIN).get(0);
        assertEquals(1500L, proposed.priceCents());
        assertEquals(original.location(), proposed.location());
        long proposalId = proposed.proposals().get(0).id();
        assertThrows(IllegalArgumentException.class, () -> service.respondToProposal(CHEN, id, proposalId, true));
        assertThrows(IllegalArgumentException.class, () -> service.proposeTerms(LIN, id, 1000L, place));
        service.respondToProposal(LIN, id, proposalId, true);
        assertThrows(IllegalArgumentException.class, () -> service.respondToProposal(LIN, id, proposalId, true));
        var reopened = new DemoService(DemoDatabase.open(file)).history(CHEN).get(0);
        assertEquals(1200L, reopened.priceCents());
        assertEquals(place, reopened.location());
        assertEquals("AGREED", reopened.proposals().get(0).status());
        assertNotNull(reopened.proposals().get(0).resolvedAt());
        assertEquals(2, reopened.messages().size());
        var listing = service.market("高等数学", null).get(0);
        assertEquals(1500L, listing.priceCents());
        assertEquals(original.location(), listing.location());
        service.transition(LIN, id, DemoService.Action.ACCEPT);
        service.proposeTerms(LIN, id, 1100L, place);
        long next = service.history(CHEN).get(0).proposals().get(1).id();
        service.respondToProposal(CHEN, id, next, true);
        service.transition(LIN, id, DemoService.Action.COMPLETE);
        service.review(CHEN, id, 5, "顺利");
        assertEquals(1100L, service.history(CHEN).get(0).priceCents());
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(CHEN, id, "已结束"));
        assertThrows(IllegalArgumentException.class, () -> service.proposeTerms(LIN, id, 900L, place));
    }

    @Test
    void declineWithdrawAndCancelKeepEffectiveTermsAndHistory() throws Exception {
        var service = new DemoService(DemoDatabase.open(directory.resolve("cancel.db")));
        long item = service.market("台灯", null).get(0).id();
        service.request(CHEN, item, "交换");
        long id = service.history(CHEN).get(0).id();
        var original = service.history(LIN).get(0);
        var place = new MeetupLocation(Campus.GUIZISHAN, "校门口");
        assertThrows(IllegalArgumentException.class, () -> service.proposeTerms(CHEN, id, 100L, place));
        service.proposeTerms(CHEN, id, null, place);
        long proposal = service.history(LIN).get(0).proposals().get(0).id();
        service.respondToProposal(LIN, id, proposal, false);
        assertEquals(original.location(), service.history(LIN).get(0).location());
        service.proposeTerms(LIN, id, null, place);
        long second = service.history(CHEN).get(0).proposals().get(1).id();
        assertThrows(IllegalArgumentException.class, () -> service.withdrawProposal(CHEN, id, second));
        service.withdrawProposal(LIN, id, second);
        service.proposeTerms(CHEN, id, null, place);
        service.transition(CHEN, id, DemoService.Action.CANCEL);
        var history = service.history(LIN).get(0);
        assertEquals(java.util.List.of("DECLINED", "WITHDRAWN", "EXPIRED"),
                history.proposals().stream().map(value -> value.status()).toList());
        assertEquals(original.location(), history.location());
        assertThrows(IllegalArgumentException.class, () -> service.respondToProposal(LIN, id,
                history.proposals().get(2).id(), true));
    }

    @Test
    void rejectsOutsidersAndSerializesConflictingReplies() throws Exception {
        var database = DemoDatabase.open(directory.resolve("concurrent.db"));
        var service = new DemoService(database);
        var userRepo = new JdbcUserRepository(database);
        long outsider = userRepo.insert("third_student", "hash", Role.STUDENT, true, Instant.now());
        long outsiderItem = new JdbcItemRepository(database).insert(outsider,
                new edu.campus.circulation.domain.ItemDraft(1, "第三人的书", "说明",
                    edu.campus.circulation.domain.ItemCondition.GOOD,
                    edu.campus.circulation.domain.TradeType.SALE, 500L, null, java.util.List.of()), Instant.now());
        var exchangeRepo = new JdbcExchangeRepository(database);
        long foreign = exchangeRepo.insertWithFirstMessage(outsiderItem, LIN.id(), "留言", Instant.now());
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(CHEN, foreign, "越权"));
        var place = new MeetupLocation(Campus.NANHU, "校门口");
        assertThrows(IllegalArgumentException.class, () -> service.proposeTerms(CHEN, foreign, 400L, place));
        long book = service.market("高等数学", null).get(0).id();
        service.request(CHEN, book, "你好");
        long id = service.history(CHEN).get(0).id();
        service.proposeTerms(CHEN, id, 1200L, place);
        long proposal = service.history(CHEN).get(0).proposals().get(0).id();
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> reply = () -> {
            ready.await();
            try {
                service.respondToProposal(LIN, id, proposal, true);
                return true;
            } catch (IllegalArgumentException alreadyResolved) {
                return false;
            }
        };
        try {
            var first = executor.submit(reply);
            var second = executor.submit(reply);
            ready.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, new JdbcNegotiationRepository(database).terms(id).version());
        } finally {
            executor.shutdownNow();
        }
    }
}
