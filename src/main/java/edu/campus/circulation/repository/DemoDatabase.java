package edu.campus.circulation.repository;

import edu.campus.circulation.domain.ItemCondition;
import edu.campus.circulation.domain.ItemDraft;
import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import edu.campus.circulation.domain.Role;
import edu.campus.circulation.domain.TradeType;
import edu.campus.circulation.domain.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Opens a demo file and adds sample data once. Ordinary data access uses the shared JDBC repositories. */
public final class DemoDatabase {
    private static final String DEMO_MARKER = "DEMO_PROFILE";

    private DemoDatabase() {}

    public static DatabaseManager open(Path file) throws IOException {
        Path path = file.toAbsolutePath();
        Files.createDirectories(path.getParent());
        DatabaseManager manager = new DatabaseManager("jdbc:sqlite:" + path);
        manager.initialize();
        UserRepository users = new JdbcUserRepository(manager);
        CategoryRepository categories = new JdbcCategoryRepository(manager);
        ItemRepository items = new JdbcItemRepository(manager);
        manager.inWriteTransaction(() -> {
            var lin = users.findById(1);
            var chen = users.findById(2);
            if (lin.isPresent() || chen.isPresent()) {
                if (lin.filter(user -> isDemo(user, "student_lin")).isEmpty()
                        || chen.filter(user -> isDemo(user, "student_chen")).isEmpty()) {
                    throw new DataAccessException("该文件不是演示数据库，请另选演示文件。");
                }
                return;
            }

            Instant now = Instant.now();
            long linId = users.insert("student_lin", DEMO_MARKER, Role.STUDENT, true, now);
            long chenId = users.insert("student_chen", DEMO_MARKER, Role.STUDENT, true, now);
            if (linId != 1 || chenId != 2) {
                throw new DataAccessException("该文件不是空的演示数据库，请另选演示文件。");
            }
            long books = categories.insert("教材文具", true, null, null);
            long electronics = categories.insert("数码配件", true, null, null);
            long household = categories.insert("生活用品", true, null, null);
            long book = items.insert(linId, new ItemDraft(books, "高等数学教材", "封面完好，内页有少量课堂笔记。",
                    ItemCondition.GOOD, TradeType.SALE, 1500L, null, List.of()), now);
            long lamp = items.insert(linId, new ItemDraft(electronics, "USB 台灯", "适合宿舍书桌，亮度可调。",
                    ItemCondition.FAIR, TradeType.SWAP, null, "交换一个文件收纳盒", List.of()), now);
            long bottle = items.insert(chenId, new ItemDraft(household, "不锈钢保温杯", "未使用，容量 500 mL。",
                    ItemCondition.NEW, TradeType.SALE, 2000L, null, List.of()), now);
            items.setInitialMeetupLocation(book, new MeetupLocation(Campus.GUIZISHAN, "图书馆入口（示例）"));
            items.setInitialMeetupLocation(lamp, new MeetupLocation(Campus.NANHU, "校门口（示例）"));
            items.setInitialMeetupLocation(bottle, new MeetupLocation(Campus.NANHU, "教学楼入口（示例）"));
        });
        return manager;
    }

    private static boolean isDemo(User user, String username) {
        return username.equals(user.username()) && DEMO_MARKER.equals(user.passwordHash()) && user.role() == Role.STUDENT;
    }
}
