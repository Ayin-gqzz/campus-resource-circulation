package edu.campus.circulation.service;

import edu.campus.circulation.domain.ItemCondition;
import edu.campus.circulation.domain.ItemDraft;
import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import edu.campus.circulation.domain.Role;
import edu.campus.circulation.domain.TradeType;
import edu.campus.circulation.repository.DatabaseManager;
import edu.campus.circulation.repository.DemoDatabase;
import edu.campus.circulation.repository.JdbcCategoryRepository;
import edu.campus.circulation.repository.JdbcItemRepository;
import edu.campus.circulation.repository.JdbcUserRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static edu.campus.circulation.service.DemoService.Profile.*;
import static edu.campus.circulation.service.DemoService.Action.*;
import static edu.campus.circulation.service.DemoService.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiBuyerDemoTest {
    @TempDir Path directory;

    @Test
    void choosingOneBuyerClosesOtherRequestsAndRestartDoesNotResetTheDemo() throws Exception {
        Path file = directory.resolve("multi.db");
        var service = new DemoService(DemoDatabase.open(file));
        assertEquals(4, service.profiles().size());
        assertEquals(17, service.market("", null).size());
        assertTrue(service.market("", null).stream().allMatch(item -> item.location() != null));
        assertEquals(2, service.market("", null).stream().map(item -> item.location().campus()).distinct().count());
        assertEquals(5, service.categories().size());
        var bicycle = service.market("多人意向演示", null).get(0);
        assertEquals(3, bicycle.pendingCount());
        var wang = service.profiles().stream().filter(profile -> profile.label().equals("王同学")).findFirst().orElseThrow();
        var zhou = service.profiles().stream().filter(profile -> profile.label().equals("周同学")).findFirst().orElseThrow();
        var chosen = service.history(CHEN).stream().filter(e -> e.itemId() == bicycle.id()).findFirst().orElseThrow();
        var other = service.history(wang).get(0);
        assertEquals(2, chosen.messages().size());
        assertTrue(service.history(zhou).stream().allMatch(e -> e.requesterId() == zhou.id()));
        assertThrows(IllegalArgumentException.class, () -> service.request(CHEN, bicycle.id(), "重复预定"));
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage(wang, chosen.id(), "越权留言"));
        service.proposeTerms(wang, other.id(), 25000L, new MeetupLocation(Campus.NANHU, "校门口"));
        service.transition(LIN, chosen.id(), ACCEPT);
        assertTrue(service.market("多人意向演示", null).isEmpty());
        assertEquals(REJECTED, service.history(wang).get(0).status());
        assertEquals("EXPIRED", service.history(wang).get(0).proposals().get(0).status());
        assertEquals(REJECTED, service.history(zhou).get(0).status());
        assertThrows(IllegalArgumentException.class, () -> service.transition(LIN, other.id(), ACCEPT));
        assertThrows(IllegalArgumentException.class, () -> service.request(zhou, bicycle.id(), "抢购"));

        var reopened = new DemoService(DemoDatabase.open(file));
        assertEquals(4, reopened.profiles().size());
        assertTrue(reopened.market("多人意向演示", null).isEmpty());
        assertEquals(REJECTED, reopened.history(wang).get(0).status());
        service.transition(CHEN, chosen.id(), CANCEL);
        assertEquals(0, service.market("多人意向演示", null).get(0).pendingCount());
        service.request(zhou, bicycle.id(), "重新上架后再次申请");
        reopened = new DemoService(DemoDatabase.open(file));
        assertEquals(17, reopened.market("", null).size());
        assertEquals(1, reopened.market("多人意向演示", null).get(0).pendingCount());
        assertEquals(PENDING, reopened.history(zhou).get(0).status());
        assertEquals(2, reopened.history(zhou).size());
    }

    @Test
    void addsExamplesToAnExistingDemoWithoutOverwritingUsersOrListings() throws Exception {
        Path file = directory.resolve("existing.db");
        var manager = new DatabaseManager("jdbc:sqlite:" + file);
        manager.initialize();
        var users = new JdbcUserRepository(manager);
        Instant oldTime = Instant.parse("2026-11-01T00:00:00Z");
        users.insert("student_lin", "DEMO_PROFILE", Role.STUDENT, true, oldTime);
        users.insert("student_chen", "DEMO_PROFILE", Role.STUDENT, true, oldTime);
        long unrelated = users.insert("existing_student", "original_hash", Role.STUDENT, true, oldTime);
        long category = new JdbcCategoryRepository(manager).insert("教材文具", true, null, null);
        var items = new JdbcItemRepository(manager);
        long original = items.insert(1, new ItemDraft(category, "已有物品", "用户原来发布的内容",
                ItemCondition.GOOD, TradeType.SALE, 1234L, null, List.of()), oldTime);
        items.setInitialMeetupLocation(original, new MeetupLocation(Campus.GUIZISHAN, "教学楼入口"));
        var service = new DemoService(DemoDatabase.open(file));
        DemoDatabase.open(file);
        assertEquals(4, service.profiles().size());
        assertEquals(15, service.market("", null).size());
        assertEquals("original_hash", users.findById(unrelated).orElseThrow().passwordHash());
        assertEquals(original, service.market("已有物品", null).get(0).id());
        assertEquals(1234L, service.market("已有物品", null).get(0).priceCents());
        var wang = service.profiles().stream().filter(profile -> profile.label().equals("王同学")).findFirst().orElseThrow();
        assertNotEquals(unrelated, wang.id());
        assertEquals(3, service.market("多人意向演示", null).get(0).pendingCount());
    }
}
