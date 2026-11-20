package edu.campus.circulation.service;

import edu.campus.circulation.repository.DataAccessException;
import edu.campus.circulation.repository.DemoDatabase;
import edu.campus.circulation.repository.JdbcUserRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static edu.campus.circulation.service.DemoService.Profile.*;
import static edu.campus.circulation.service.DemoService.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {
    @TempDir Path directory;

    @Test
    void reopeningNotifiesEachPreviousStudentOnceAndKeepsReadStateAcrossRestart() throws Exception {
        Path file = directory.resolve("reminders.db");
        var service = new DemoService(DemoDatabase.open(file));
        var wang = service.profiles().stream().filter(p -> p.label().equals("王同学")).findFirst().orElseThrow();
        var zhou = service.profiles().stream().filter(p -> p.label().equals("周同学")).findFirst().orElseThrow();
        long item = service.market("多人意向演示", null).get(0).id();
        service.transition(wang, service.history(wang).get(0).id(), CANCEL);
        service.request(wang, item, "重新提交一次");
        assertTrue(service.notifications(zhou).isEmpty());
        long chosen = service.history(CHEN).stream().filter(e -> e.itemId() == item).findFirst().orElseThrow().id();
        service.transition(LIN, chosen, ACCEPT);
        service.transition(CHEN, chosen, CANCEL);
        assertEquals(1, service.notifications(wang).size());
        assertEquals(1, service.notifications(zhou).size());
        assertEquals(1, service.notifications(LIN).size());
        assertTrue(service.notifications(CHEN).isEmpty());
        var notice = service.notifications(wang).get(0);
        assertEquals(item, notice.itemId());
        assertTrue(notice.available());
        assertNull(notice.readAt());
        assertTrue(notice.body().contains("重新提交意向"));
        assertThrows(IllegalArgumentException.class, () -> service.markNotificationRead(zhou, notice.id()));
        service.markNotificationRead(wang, notice.id());
        var readAt = service.notifications(wang).get(0).readAt();
        service.markNotificationRead(wang, notice.id());
        assertEquals(readAt, service.notifications(wang).get(0).readAt());
        var reopened = new DemoService(DemoDatabase.open(file));
        assertEquals(1, reopened.notifications(wang).size());
        assertEquals(readAt, reopened.notifications(wang).get(0).readAt());
        assertNull(reopened.notifications(zhou).get(0).readAt());

        service.request(zhou, item, "收到提醒，再次申请");
        long next = service.history(zhou).get(0).id();
        service.transition(LIN, next, ACCEPT);
        assertFalse(service.notifications(wang).get(0).available());
        service.transition(LIN, next, CANCEL);
        assertEquals(2, service.notifications(wang).size());
        assertEquals(2, service.notifications(zhou).size());
        assertEquals(1, service.notifications(CHEN).size());
        assertEquals(1, service.notifications(LIN).size());
        assertEquals(1, service.notifications(wang).stream().filter(n -> n.readAt() == null).count());
    }

    @Test
    void cancellationAndRemindersCommitTogetherAndIgnoreDisabledRecipients() throws Exception {
        var database = DemoDatabase.open(directory.resolve("atomic.db"));
        var service = new DemoService(database);
        var zhou = service.profiles().stream().filter(p -> p.label().equals("周同学")).findFirst().orElseThrow();
        var wang = service.profiles().stream().filter(p -> p.label().equals("王同学")).findFirst().orElseThrow();
        long item = service.market("多人意向演示", null).get(0).id();
        long chosen = service.history(CHEN).stream().filter(e -> e.itemId() == item).findFirst().orElseThrow().id();
        service.transition(LIN, chosen, ACCEPT);
        database.executeWithConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_notice BEFORE INSERT ON notifications BEGIN SELECT RAISE(ABORT, 'test failure'); END");
            }
            return null;
        });
        assertThrows(DataAccessException.class, () -> service.transition(CHEN, chosen, CANCEL));
        assertTrue(service.market("多人意向演示", null).isEmpty());
        assertEquals(DemoService.Status.ACCEPTED, service.history(CHEN).stream()
                .filter(e -> e.id() == chosen).findFirst().orElseThrow().status());
        assertTrue(service.notifications(wang).isEmpty());
        database.executeWithConnection(connection -> {
            try (var statement = connection.createStatement()) { statement.execute("DROP TRIGGER fail_notice"); }
            return null;
        });
        new JdbcUserRepository(database).updateEnabled(zhou.id(), false);
        service.transition(CHEN, chosen, CANCEL);
        assertEquals(1, service.notifications(wang).size());
        assertTrue(new edu.campus.circulation.repository.JdbcNotificationRepository(database).inbox(zhou.id()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.transition(CHEN, chosen, CANCEL));
        assertEquals(1, service.notifications(wang).size());
    }
}
