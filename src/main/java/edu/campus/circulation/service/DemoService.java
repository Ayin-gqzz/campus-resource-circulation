package edu.campus.circulation.service;

import edu.campus.circulation.domain.ExchangeStatus;
import edu.campus.circulation.domain.ItemCondition;
import edu.campus.circulation.domain.ItemDraft;
import edu.campus.circulation.domain.ItemSearchFilter;
import edu.campus.circulation.domain.ItemStatus;
import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.Review;
import edu.campus.circulation.domain.User;
import edu.campus.circulation.repository.CategoryRepository;
import edu.campus.circulation.repository.DatabaseManager;
import edu.campus.circulation.repository.ExchangeRepository;
import edu.campus.circulation.repository.ItemRepository;
import edu.campus.circulation.repository.JdbcCategoryRepository;
import edu.campus.circulation.repository.JdbcExchangeRepository;
import edu.campus.circulation.repository.JdbcItemRepository;
import edu.campus.circulation.repository.JdbcReviewRepository;
import edu.campus.circulation.repository.JdbcUserRepository;
import edu.campus.circulation.repository.ReviewRepository;
import edu.campus.circulation.repository.UniqueConstraintException;
import edu.campus.circulation.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DemoService {
    private final DatabaseManager database;
    private final CategoryRepository categories;
    private final ItemRepository items;
    private final UserRepository users;
    private final ExchangeRepository exchanges;
    private final ReviewRepository reviews;

    public DemoService(DatabaseManager database) {
        this.database = database;
        categories = new JdbcCategoryRepository(database);
        items = new JdbcItemRepository(database);
        users = new JdbcUserRepository(database);
        exchanges = new JdbcExchangeRepository(database);
        reviews = new JdbcReviewRepository(database);
    }

    public List<Category> categories() {
        return categories.findAll(true).stream()
                .map(category -> new Category(category.id(), category.name())).toList();
    }

    public List<Item> market(String keyword, Long categoryId) {
        Map<Long, String> names = categories.findAll(false).stream().collect(Collectors.toMap(
                edu.campus.circulation.domain.Category::id, edu.campus.circulation.domain.Category::name));
        return items.searchAvailable(new ItemSearchFilter(categoryId, keyword.strip(), null, null)).stream()
                .map(item -> new Item(item.id(), item.ownerId(), item.title(), item.description(),
                        names.get(item.categoryId()), Condition.valueOf(item.condition().name()),
                        TradeType.valueOf(item.tradeType().name()), item.priceCents() == null ? 0 : item.priceCents(),
                        item.swapNote(), item.meetupLocation()))
                .toList();
    }

    public void publish(Profile owner, Draft draft) {
        String title = required(draft.title(), 80, "物品名称");
        String description = required(draft.description(), 1000, "物品描述");
        require(draft.location() != null, "请填写交易校区和具体地点。");
        Long price = draft.type() == TradeType.SALE ? draft.priceCents() : null;
        String swap = draft.type() == TradeType.SWAP ? required(draft.swapNote(), 200, "换物说明") : null;
        require(draft.type() != TradeType.SALE || (price != null && price > 0), "请输入大于零的售价。");
        ItemDraft itemDraft = new ItemDraft(draft.categoryId(), title, description,
                ItemCondition.valueOf(draft.condition().name()),
                edu.campus.circulation.domain.TradeType.valueOf(draft.type().name()), price, swap, List.of());
        database.inWriteTransaction(() -> {
            active(owner);
            require(categories.findById(draft.categoryId()).filter(category -> category.enabled()).isPresent(),
                    "该分类不可用。");
            long itemId = items.insert(owner.id(), itemDraft, Instant.now());
            require(items.setInitialMeetupLocation(itemId, draft.location()), "无法保存交易地点。");
        });
    }

    public void fillMeetupLocation(Profile owner, long itemId, MeetupLocation location) {
        database.inWriteTransaction(() -> {
            active(owner);
            var item = items.findById(itemId)
                    .filter(value -> value.ownerId() == owner.id())
                    .orElseThrow(() -> new IllegalArgumentException("只有发布人可以补填交易地点。"));
            require(items.setInitialMeetupLocation(item.id(), location), "当前物品无法补填交易地点，请刷新。");
        });
    }

    public void request(Profile actor, long itemId, String message) {
        String body = required(message, 500, "留言");
        database.inWriteTransaction(() -> {
            active(actor);
            var item = items.findById(itemId)
                    .filter(value -> value.status() == ItemStatus.AVAILABLE)
                    .orElseThrow(() -> new IllegalArgumentException("物品已不在架，请刷新列表。"));
            require(users.findById(item.ownerId()).filter(User::enabled).isPresent(), "物品已不在架，请刷新列表。");
            require(item.ownerId() != actor.id(), "不能对自己发布的物品发起意向。");
            require(item.meetupLocation() != null, "请等发布人补填交易地点后再发起意向。");
            try {
                exchanges.insertWithFirstMessage(itemId, actor.id(), body, Instant.now());
            } catch (UniqueConstraintException duplicate) {
                throw new IllegalArgumentException("你已经提交过待处理意向。", duplicate);
            }
        });
    }

    public List<Exchange> history(Profile actor) {
        return exchanges.findByParticipant(actor.id()).stream().map(exchange -> {
            var item = items.findById(exchange.itemId()).orElseThrow();
            String firstMessage = exchanges.findMessages(exchange.id()).stream()
                    .findFirst().map(message -> message.body()).orElse(null);
            Integer stars = reviews.findByExchange(exchange.id()).stream()
                    .filter(review -> review.reviewerId() == actor.id())
                    .findFirst().map(Review::stars).orElse(null);
            return new Exchange(exchange.id(), item.id(), item.title(), item.ownerId(), exchange.requesterId(),
                    Status.valueOf(exchange.status().name()), firstMessage, stars, item.meetupLocation());
        }).toList();
    }

    public void transition(Profile actor, long exchangeId, Action action) {
        database.inWriteTransaction(() -> {
            active(actor);
            Trade trade = trade(exchangeId, actor);
            boolean owner = actor.id() == trade.owner();
            switch (action) {
                case ACCEPT -> {
                    require(owner && trade.status() == ExchangeStatus.PENDING, "只有发布人可以接受待处理意向。");
                    require(trade.location() != null, "请先补填交易地点。");
                    moveItem(trade.item(), ItemStatus.AVAILABLE, ItemStatus.RESERVED);
                    moveExchange(exchangeId, trade.status(), ExchangeStatus.ACCEPTED);
                    exchanges.rejectOtherPendingByItem(trade.item(), exchangeId);
                }
                case COMPLETE -> {
                    require(owner && trade.status() == ExchangeStatus.ACCEPTED, "只有发布人可以确认已接受的流转。");
                    require(trade.location() != null, "请先补填交易地点。");
                    moveItem(trade.item(), ItemStatus.RESERVED, ItemStatus.COMPLETED);
                    moveExchange(exchangeId, trade.status(), ExchangeStatus.COMPLETED);
                }
                case REJECT -> {
                    require(owner && trade.status() == ExchangeStatus.PENDING, "只有发布人可以拒绝待处理意向。");
                    moveExchange(exchangeId, trade.status(), ExchangeStatus.REJECTED);
                }
                case CANCEL -> {
                    if (trade.status() == ExchangeStatus.ACCEPTED) {
                        moveItem(trade.item(), ItemStatus.RESERVED, ItemStatus.AVAILABLE);
                    } else {
                        require(!owner && trade.status() == ExchangeStatus.PENDING, "当前意向不能撤回。");
                    }
                    moveExchange(exchangeId, trade.status(), ExchangeStatus.CANCELLED);
                }
            }
        });
    }

    public void review(Profile actor, long exchangeId, int stars, String comment) {
        require(stars >= 1 && stars <= 5, "星级须在 1 到 5 之间。");
        String text = comment.strip();
        require(text.codePointCount(0, text.length()) <= 500, "短评最多 500 字。");
        database.inWriteTransaction(() -> {
            active(actor);
            Trade trade = trade(exchangeId, actor);
            require(trade.status() == ExchangeStatus.COMPLETED, "完成流转后才可以评价。");
            long peer = actor.id() == trade.owner() ? trade.requester() : trade.owner();
            try {
                reviews.insert(new Review(exchangeId, actor.id(), peer, stars, text, Instant.now()));
            } catch (UniqueConstraintException duplicate) {
                throw new IllegalArgumentException("你已评价过本次流转。", duplicate);
            }
        });
    }

    public Credit credit(Profile actor) {
        var summary = reviews.getCreditSummary(actor.id());
        return new Credit(summary.reviewCount(), summary.averageStars() == null ? 0 : summary.averageStars());
    }

    private Trade trade(long exchangeId, Profile actor) {
        var exchange = exchanges.findById(exchangeId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在或你无权操作。"));
        var item = items.findById(exchange.itemId()).orElseThrow();
        require(item.ownerId() == actor.id() || exchange.requesterId() == actor.id(), "记录不存在或你无权操作。");
        return new Trade(item.id(), item.ownerId(), exchange.requesterId(), exchange.status(), item.meetupLocation());
    }

    private void active(Profile actor) {
        require(users.findById(actor.id()).filter(User::enabled).isPresent(), "当前演示身份不可用。");
    }

    private void moveItem(long itemId, ItemStatus previous, ItemStatus next) {
        require(items.updateStatus(itemId, previous, next), "物品状态已变化，请刷新。");
    }

    private void moveExchange(long id, ExchangeStatus previous, ExchangeStatus next) {
        Instant completedAt = next == ExchangeStatus.COMPLETED ? Instant.now() : null;
        require(exchanges.transitionStatus(id, previous, next, completedAt), "意向状态已变化，请刷新。");
    }

    private static String required(String value, int max, String field) {
        String text = value.strip();
        require(!text.isEmpty() && text.codePointCount(0, text.length()) <= max,
                field + "不能为空，最多 " + max + " 字。");
        return text;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record Trade(long item, long owner, long requester, ExchangeStatus status, MeetupLocation location) {}

    public enum Profile {
        LIN(1, "林同学"), CHEN(2, "陈同学");

        private final long id;
        private final String label;

        Profile(long id, String label) {
            this.id = id;
            this.label = label;
        }

        public long id() {
            return id;
        }

        public static String nameOf(long id) {
            for (Profile profile : values()) {
                if (profile.id == id) {
                    return profile.label;
                }
            }
            return "学生 " + id;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum TradeType {
        SALE("售卖"), SWAP("换物");

        private final String label;

        TradeType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Condition {
        NEW("全新"), GOOD("良好"), FAIR("有使用痕迹");

        private final String label;

        Condition(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Status {
        PENDING("待处理"), ACCEPTED("已接受"), REJECTED("已拒绝"), CANCELLED("已取消"), COMPLETED("已完成");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Action {
        ACCEPT, REJECT, COMPLETE, CANCEL
    }

    public record Category(long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record Draft(long categoryId, String title, String description, Condition condition,
            TradeType type, Long priceCents, String swapNote, MeetupLocation location) {}

    public record Item(long id, long ownerId, String title, String description, String category,
            Condition condition, TradeType type, long priceCents, String swapNote, MeetupLocation location) {}

    public record Exchange(long id, long itemId, String itemTitle, long ownerId, long requesterId, Status status,
            String message, Integer stars, MeetupLocation location) {}

    public record Credit(int count, double average) {}

}
