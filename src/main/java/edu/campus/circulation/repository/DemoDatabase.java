package edu.campus.circulation.repository;

import edu.campus.circulation.domain.ItemCondition;
import edu.campus.circulation.domain.ItemDraft;
import edu.campus.circulation.domain.ItemStatus;
import edu.campus.circulation.domain.ExchangeStatus;
import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import edu.campus.circulation.domain.Role;
import edu.campus.circulation.domain.Review;
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
    private static final String RATING_SAMPLE_TAG = "__demo_rating_v1";

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
                Instant now = Instant.now();
                seedRatingExample(manager, categories, items, 1, 2, now);
                seedMarketExamples(manager, users, categories, items, now);
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
            items.setInitialMeetupLocation(book, new MeetupLocation(Campus.GUIZISHAN, "图书馆入口（示例）"), sampleTime(now, 1));
            items.setInitialMeetupLocation(lamp, new MeetupLocation(Campus.NANHU, "校门口（示例）"), sampleTime(now, 2));
            items.setInitialMeetupLocation(bottle, new MeetupLocation(Campus.NANHU, "教学楼入口（示例）"), sampleTime(now, 1));
            seedRatingExample(manager, categories, items, linId, chenId, now);
            seedMarketExamples(manager, users, categories, items, now);
        });
        return manager;
    }

    private static void seedRatingExample(DatabaseManager database, CategoryRepository categories, ItemRepository items,
            long lin, long chen, Instant now) {
        if (items.findByOwner(lin).stream().anyMatch(item -> item.tags().contains(RATING_SAMPLE_TAG))) {
            return;
        }
        long category = categories.findByName("教材文具").map(value -> value.id())
                .orElseGet(() -> categories.insert("教材文具", true, null, null));
        long item = items.insert(lin, new ItemDraft(category, "【示例】英语四级词汇书", "用于演示交接和双方评价。",
                ItemCondition.GOOD, TradeType.SALE, 800L, null, List.of(RATING_SAMPLE_TAG)), now);
        boolean locationSaved = items.setInitialMeetupLocation(item,
                new MeetupLocation(Campus.GUIZISHAN, "图书馆入口（示例）"), now);
        ExchangeRepository exchanges = new JdbcExchangeRepository(database);
        ReviewRepository reviews = new JdbcReviewRepository(database);
        long exchange = exchanges.insertWithFirstMessage(item, chen, "【示例】约在图书馆入口交接。", now);
        if (!locationSaved
                || !exchanges.transitionStatus(exchange, ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, null)
                || !items.updateStatus(item, ItemStatus.AVAILABLE, ItemStatus.RESERVED)
                || !exchanges.transitionStatus(exchange, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETED, now)
                || !items.updateStatus(item, ItemStatus.RESERVED, ItemStatus.COMPLETED)) {
            throw new DataAccessException("无法初始化评价样例。");
        }
        reviews.insert(new Review(exchange, chen, lin, 5, "【示例】书况和描述一致，交接准时。", now));
        reviews.insert(new Review(exchange, lin, chen, 4, "【示例】沟通清楚，顺利完成交接。", now));
    }

    private static boolean isDemo(User user, String username) {
        return username.equals(user.username()) && DEMO_MARKER.equals(user.passwordHash()) && user.role() == Role.STUDENT;
    }

    private static void seedMarketExamples(DatabaseManager database, UserRepository users,
            CategoryRepository categories, ItemRepository items, Instant now) {
        long wang = demoStudent(users, "student_wang", now.minusSeconds(30L * 86400));
        long zhou = demoStudent(users, "student_zhou", now.minusSeconds(20L * 86400));
        long books = demoCategory(categories, "教材文具");
        long electronics = demoCategory(categories, "数码配件");
        long household = demoCategory(categories, "生活用品");
        long sports = demoCategory(categories, "运动出行");
        long storage = demoCategory(categories, "宿舍收纳");
        var samples = List.of(
                new MarketSample("python-book", 1, books, "Python 程序设计教材", "课程已结束，少量铅笔标注，附配套练习笔记。",
                        ItemCondition.GOOD, 2500L, null, Campus.GUIZISHAN, "图书馆入口"),
                new MarketSample("english-papers", 2, books, "英语六级真题集", "近年真题整理，听力部分未填写，适合备考。",
                        ItemCondition.GOOD, 1800L, null, Campus.NANHU, "教学楼入口"),
                new MarketSample("keyboard", wang, electronics, "无线键盘", "按键正常，支持蓝牙连接，附电池，可现场试用。",
                        ItemCondition.GOOD, 4500L, null, Campus.NANHU, "图书馆入口"),
                new MarketSample("laptop-stand", 1, electronics, "可折叠笔记本支架", "铝合金材质，角度可调，适合宿舍自习使用。",
                        ItemCondition.GOOD, 2800L, null, Campus.GUIZISHAN, "教学楼入口"),
                new MarketSample("usb-hub", zhou, electronics, "Type-C 扩展坞", "含 USB 与 HDMI 接口，功能正常，可带电脑验货。",
                        ItemCondition.GOOD, 5500L, null, Campus.NANHU, "校门口"),
                new MarketSample("organizer", 2, storage, "桌面文件收纳盒", "三层分类收纳，外观整洁，适合放讲义与文具。",
                        ItemCondition.GOOD, 1200L, null, Campus.NANHU, "教学楼入口"),
                new MarketSample("bookshelf", wang, storage, "双层桌面书架", "木色书架，已组装，建议当面自提。",
                        ItemCondition.GOOD, 3500L, null, Campus.GUIZISHAN, "校门口"),
                new MarketSample("drying-rack", zhou, household, "折叠晾衣架", "可折叠收纳，接头完整，使用痕迹已清理。",
                        ItemCondition.FAIR, 1500L, null, Campus.NANHU, "校门口"),
                new MarketSample("badminton", 1, sports, "入门羽毛球拍", "拍框完好，含拍套，握把有使用痕迹。",
                        ItemCondition.FAIR, 4800L, null, Campus.GUIZISHAN, "体育场入口"),
                new MarketSample("yoga-mat", 2, sports, "防滑瑜伽垫", "已清洁，附收纳带，希望交换学习用品。",
                        ItemCondition.GOOD, null, "交换一套未使用的笔记本", Campus.NANHU, "体育馆入口"),
                new MarketSample("canvas-bag", zhou, household, "全新帆布袋", "活动纪念帆布袋，未使用，可装教材。",
                        ItemCondition.NEW, null, "交换一个桌面收纳盒", Campus.GUIZISHAN, "图书馆入口"),
                new MarketSample("alarm-clock", wang, household, "桌面小闹钟", "走时正常，含电池，适合放在书桌上。",
                        ItemCondition.GOOD, 1000L, null, Campus.NANHU, "教学楼入口"));
        Instant sampleTime = now.minusSeconds(86400);
        for (MarketSample sample : samples) {
            addSample(items, sample, sampleTime);
        }

        var notebooks = new MarketSample("notebooks-5", 1, books, "【多件演示】全新笔记本",
                "共 5 本同款同成色笔记本，每件为 1 本，单价 5 元。申请时可选择数量；"
                        + "可演示陈同学申请 2 本、王同学申请 1 本，分别预定后剩余 2 本继续出售。",
                ItemCondition.NEW, 500L, null, Campus.GUIZISHAN, "图书馆入口", 5);
        addSample(items, notebooks, sampleTime);

        // Keep existing outcomes: this scenario is created once and is never reset on restart.
        var bicycle = new MarketSample("multi-buyer-bicycle", 1, sports, "【多人意向演示】校园自行车",
                "日常校园代步，刹车与车铃正常，附车锁。陈、王、周三位同学已提交意向。"
                        + "切换林同学，在「我的流转」分别沟通并选择一人；接受后其他意向结束。交易地点为示例，请双方确认。",
                ItemCondition.GOOD, 26000L, null, Campus.NANHU, "校门口");
        if (hasSample(items, bicycle)) return;
        long item = addSample(items, bicycle, sampleTime);
        ExchangeRepository exchanges = new JdbcExchangeRepository(database);
        long chenRequest = exchanges.insertWithFirstMessage(item, 2,
                "【多人意向演示】想买这辆车，请问可以试骑吗？", now.minusSeconds(10800));
        exchanges.insertMessage(chenRequest, 1, "可以在南湖校区校门口先检查车况，确认后再交接。", now.minusSeconds(10740));
        long wangRequest = exchanges.insertWithFirstMessage(item, wang,
                "【多人意向演示】我也想买，今晚能在南湖校区交接吗？", now.minusSeconds(7200));
        exchanges.insertMessage(wangRequest, 1, "可以沟通时间，目前还未选定交易同学。", now.minusSeconds(7140));
        exchanges.insertWithFirstMessage(item, zhou,
                "【多人意向演示】还在吗？我可以到南湖校区自提。", now.minusSeconds(3600));
    }

    private static long demoStudent(UserRepository users, String username, Instant joinedAt) {
        return users.findByUsername(username).map(user -> {
            if (!isDemo(user, username)) throw new DataAccessException("演示身份名称已被其他账号使用：" + username);
            return user.id();
        }).orElseGet(() -> users.insert(username, DEMO_MARKER, Role.STUDENT, true, joinedAt));
    }

    private static long demoCategory(CategoryRepository categories, String name) {
        return categories.findByName(name).map(category -> category.id())
                .orElseGet(() -> categories.insert(name, true, null, null));
    }

    private static boolean hasSample(ItemRepository items, MarketSample sample) {
        return items.findByOwner(sample.owner()).stream()
                .anyMatch(item -> item.tags().contains("__m2:" + sample.key()));
    }

    private static long addSample(ItemRepository items, MarketSample sample, Instant createdAt) {
        var existing = items.findByOwner(sample.owner()).stream()
                .filter(item -> item.tags().contains("__m2:" + sample.key())).findFirst();
        if (existing.isPresent()) return existing.get().id();
        long id = items.insert(sample.owner(), new ItemDraft(sample.category(), sample.title(), sample.description()
                + "（演示样例，交接地点请双方确认。）", sample.condition(),
                sample.price() == null ? TradeType.SWAP : TradeType.SALE, sample.price(), sample.swapNote(),
                List.of("__m2:" + sample.key()), sample.quantity()), createdAt);
        if (!items.setInitialMeetupLocation(id, new MeetupLocation(sample.campus(), sample.place()), sampleTime(createdAt, 2))) {
            throw new DataAccessException("无法保存样例交易地点。");
        }
        return id;
    }

    private record MarketSample(String key, long owner, long category, String title, String description,
            ItemCondition condition, Long price, String swapNote, Campus campus, String place, int quantity) {
        private MarketSample(String key, long owner, long category, String title, String description,
                ItemCondition condition, Long price, String swapNote, Campus campus, String place) {
            this(key, owner, category, title, description, condition, price, swapNote, campus, place, 1);
        }
    }

    private static Instant sampleTime(Instant now, int days) {
        return now.atZone(java.time.ZoneId.of("Asia/Shanghai")).plusDays(days)
                .withHour(18).withMinute(30).withSecond(0).withNano(0).toInstant();
    }
}
