package edu.campus.circulation.service;

import static edu.campus.circulation.service.DemoService.Profile.CHEN;
import static edu.campus.circulation.service.DemoService.Profile.LIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campus.circulation.repository.DemoDatabase;
import edu.campus.circulation.domain.CampusBuilding;
import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import edu.campus.circulation.service.DemoService.Action;
import edu.campus.circulation.service.DemoService.Condition;
import edu.campus.circulation.service.DemoService.Credit;
import edu.campus.circulation.service.DemoService.Draft;
import edu.campus.circulation.service.DemoService.PeerProfile;
import edu.campus.circulation.service.DemoService.Status;
import edu.campus.circulation.service.DemoService.TradeType;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoServiceTest {
    @TempDir
    Path directory;

    @Test
    void peerProfilesExposeOnlyTheCounterpartForAFlowParticipant() throws Exception {
        DemoService service = open(directory.resolve("peer.db"));
        var completed = service.history(CHEN).stream()
                .filter(exchange -> exchange.status() == Status.COMPLETED).findFirst().orElseThrow();
        assertEquals(new PeerProfile(LIN.id(), new Credit(1, 5.0), service.joinedAt(LIN)),
                service.peerProfile(CHEN, completed.id()));
        assertEquals(new PeerProfile(CHEN.id(), new Credit(1, 4.0), service.joinedAt(CHEN)),
                service.peerProfile(LIN, completed.id()));

        var wang = service.profiles().stream().filter(profile -> profile.label().equals("王同学"))
                .findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> service.peerProfile(wang, completed.id()));
        var pending = service.history(wang).get(0);
        assertEquals(new PeerProfile(wang.id(), new Credit(0, 0.0), service.joinedAt(wang)),
                service.peerProfile(LIN, pending.id()));
    }

    @Test
    void countsJoiningDaysAtBeijingMidnight() {
        var peer = new PeerProfile(LIN.id(), new Credit(0, 0.0), Instant.parse("2026-11-18T15:55:00Z"));
        assertEquals(0, peer.daysSinceJoining(Instant.parse("2026-11-18T15:59:00Z")));
        assertEquals(1, peer.daysSinceJoining(Instant.parse("2026-11-18T16:00:00Z")));
        assertEquals(0, peer.daysSinceJoining(Instant.parse("2026-11-17T16:00:00Z")));
    }

    @Test
    void publishRequestCompleteAndReviewAcrossReopen() throws Exception {
        Path file = directory.resolve("demo.db");
        DemoService service = open(file);
        int initialMarketSize = service.market("", null).size();
        Instant joined = service.joinedAt(LIN);
        assertEquals(5.0, service.credit(LIN).average());
        assertEquals(4.0, service.credit(CHEN).average());
        assertEquals(1, service.credit(LIN).count());
        assertEquals(1, service.credit(CHEN).count());
        assertEquals(Status.COMPLETED, service.history(CHEN).get(0).status());
        assertTrue(service.history(CHEN).get(0).itemTitle().startsWith("【示例】"));
        MeetupLocation location = new MeetupLocation(Campus.GUIZISHAN, "图书馆入口");
        assertThrows(IllegalArgumentException.class, () -> service.publish(LIN,
                new Draft(1, "线性代数教材", "保存完好", Condition.GOOD, TradeType.SALE, 1800L, "", null)));
        service.publish(LIN, new Draft(1, "线性代数教材", "保存完好", Condition.GOOD, TradeType.SALE, 1800L, "", location,
                Instant.now().plusSeconds(86400)));
        var item = service.market("线性代数", 1L).get(0);
        assertEquals(1800L, item.priceCents());
        assertEquals(location, item.location());
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
        assertEquals(location, persisted.location());
        assertEquals(4.0, reopened.credit(CHEN).average());
        assertEquals(5.0, reopened.credit(LIN).average());
        assertEquals(2, reopened.credit(CHEN).count());
        assertEquals(2, reopened.credit(LIN).count());
        assertEquals(joined, reopened.joinedAt(LIN));
        assertEquals(initialMarketSize, reopened.market("", null).size());
    }

    @Test
    void publishesAndReloadsBuildingIndependentlyOfMeetupLocation() throws Exception {
        Path file = directory.resolve("building.db");
        DemoService service = open(file);
        var meetup = new MeetupLocation(Campus.NANHU, "南湖综合楼东门");
        service.publish(LIN, new Draft(1, "校园地图测试教材", "保存完好", Condition.GOOD, TradeType.SALE,
                1800L, "", meetup, Instant.now().plusSeconds(86_400), 1, CampusBuilding.D05));

        var reopened = open(file);
        var item = reopened.market("校园地图测试教材", null).get(0);
        assertEquals(CampusBuilding.D05, item.building());
        assertEquals(meetup, item.location());
        assertTrue(reopened.market("USB 台灯", null).stream().anyMatch(existing -> existing.building() == null));
    }

    @Test
    void upgradesLegacyListingsAndAllowsOnlyTheirOwnerToFillTheLocation() throws Exception {
        Path file = directory.resolve("legacy.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                var statement = connection.createStatement();
                var schema = DemoDatabase.class.getResourceAsStream("/database/001_initial.sql")) {
            for (String sql : new String(schema.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
            statement.executeUpdate("""
                    INSERT INTO users (id, username, password_hash, created_at)
                    VALUES (1, 'student_lin', 'DEMO_PROFILE', '2026-11-12T00:00:00.000Z'),
                           (2, 'student_chen', 'DEMO_PROFILE', '2026-11-12T00:00:00.000Z')
                    """);
            statement.executeUpdate("INSERT INTO categories (id, name) VALUES (1, '教材')");
            statement.executeUpdate("""
                    INSERT INTO items (id, owner_id, category_id, title, description, condition,
                                       trade_type, price_cents, created_at)
                    VALUES (1, 1, 1, '旧教材', '保留的物品', 'GOOD', 'SALE', 1000, '2026-11-12T00:00:00.000Z')
                    """);
        }
        DemoService service = open(file);
        assertEquals(Instant.parse("2026-11-12T00:00:00Z"), service.joinedAt(LIN));
        assertEquals(1, service.credit(LIN).count());
        assertEquals(1, open(file).credit(LIN).count());
        var item = service.market("旧教材", null).get(0);
        assertEquals("旧教材", item.title());
        assertThrows(IllegalArgumentException.class, () -> new MeetupLocation(null, "图书馆入口"));
        assertThrows(IllegalArgumentException.class, () -> new MeetupLocation(Campus.NANHU, "  "));
        assertThrows(IllegalArgumentException.class, () -> service.request(CHEN, item.id(), "还在吗？"));
        MeetupLocation location = new MeetupLocation(Campus.NANHU, "校门口");
        assertThrows(IllegalArgumentException.class, () -> service.fillMeetupLocation(CHEN, item.id(), location));
        service.fillMeetupLocation(LIN, item.id(), location);
        assertThrows(IllegalArgumentException.class, () -> service.fillMeetupLocation(LIN, item.id(), location));
        service.request(CHEN, item.id(), "还在吗？");
        assertEquals(location, open(file).history(CHEN).get(0).location());
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
