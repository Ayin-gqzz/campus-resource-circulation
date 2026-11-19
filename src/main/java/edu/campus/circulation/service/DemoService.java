package edu.campus.circulation.service;

import edu.campus.circulation.domain.ExchangeStatus;
import edu.campus.circulation.domain.ExchangeMessage;
import edu.campus.circulation.domain.ItemCondition;
import edu.campus.circulation.domain.ItemDraft;
import edu.campus.circulation.domain.ItemSearchFilter;
import edu.campus.circulation.domain.ItemStatus;
import edu.campus.circulation.domain.Inventory;
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
import edu.campus.circulation.repository.JdbcNegotiationRepository;
import edu.campus.circulation.repository.JdbcNotificationRepository;
import edu.campus.circulation.repository.JdbcNotificationRepository.Notice;
import edu.campus.circulation.repository.JdbcNegotiationRepository.Proposal;
import edu.campus.circulation.repository.JdbcNegotiationRepository.Terms;
import edu.campus.circulation.repository.JdbcReviewRepository;
import edu.campus.circulation.repository.JdbcUserRepository;
import edu.campus.circulation.repository.ReviewRepository;
import edu.campus.circulation.repository.UniqueConstraintException;
import edu.campus.circulation.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class DemoService {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private final DatabaseManager database;
    private final CategoryRepository categories;
    private final ItemRepository items;
    private final UserRepository users;
    private final ExchangeRepository exchanges;
    private final ReviewRepository reviews;
    private final JdbcNegotiationRepository negotiations;
    private final JdbcNotificationRepository notifications;

    public DemoService(DatabaseManager database) {
        this.database = database;
        categories = new JdbcCategoryRepository(database);
        items = new JdbcItemRepository(database);
        users = new JdbcUserRepository(database);
        exchanges = new JdbcExchangeRepository(database);
        reviews = new JdbcReviewRepository(database);
        negotiations = new JdbcNegotiationRepository(database);
        notifications = new JdbcNotificationRepository(database);
    }

    public List<Category> categories() {
        return categories.findAll(true).stream()
                .map(category -> new Category(category.id(), category.name())).toList();
    }

    public List<Profile> profiles() {
        return users.findAllStudents().stream()
                .filter(user -> "DEMO_PROFILE".equals(user.passwordHash()))
                .map(user -> new Profile(user.id(), switch (user.username()) {
                    case "student_lin" -> "林同学";
                    case "student_chen" -> "陈同学";
                    case "student_wang" -> "王同学";
                    case "student_zhou" -> "周同学";
                    default -> "学生 " + user.id();
                })).toList();
    }

    public List<Item> market(String keyword, Long categoryId) {
        Map<Long, String> names = categories.findAll(false).stream().collect(Collectors.toMap(
                edu.campus.circulation.domain.Category::id, edu.campus.circulation.domain.Category::name));
        return items.searchAvailable(new ItemSearchFilter(categoryId, keyword.strip(), null, null)).stream()
                .map(item -> new Item(item.id(), item.ownerId(), item.title(), item.description(),
                        names.get(item.categoryId()), Condition.valueOf(item.condition().name()),
                        TradeType.valueOf(item.tradeType().name()), item.priceCents() == null ? 0 : item.priceCents(),
                        item.swapNote(), item.meetupLocation(), pendingCount(item.id()), item.meetupAt(), item.inventory()))
                .toList();
    }

    public void publish(Profile owner, Draft draft) {
        String title = required(draft.title(), 80, "物品名称");
        String description = required(draft.description(), 1000, "物品描述");
        require(draft.location() != null, "请填写交易校区和具体地点。");
        requireFutureTime(draft.meetupAt());
        require(draft.quantity() >= 1 && draft.quantity() <= 9999, "发布数量须为 1–9999 件。");
        Long price = draft.type() == TradeType.SALE ? draft.priceCents() : null;
        String swap = draft.type() == TradeType.SWAP ? required(draft.swapNote(), 200, "换物说明") : null;
        require(draft.type() != TradeType.SALE || (price != null && price > 0), "请输入大于零的售价。");
        ItemDraft itemDraft = new ItemDraft(draft.categoryId(), title, description,
                ItemCondition.valueOf(draft.condition().name()),
                edu.campus.circulation.domain.TradeType.valueOf(draft.type().name()), price, swap, List.of(), draft.quantity());
        database.inWriteTransaction(() -> {
            active(owner);
            require(categories.findById(draft.categoryId()).filter(category -> category.enabled()).isPresent(),
                    "该分类不可用。");
            long itemId = items.insert(owner.id(), itemDraft, Instant.now());
            require(items.setInitialMeetupLocation(itemId, draft.location(), draft.meetupAt()), "无法保存交易地点与时间。");
        });
    }

    public void fillMeetupLocation(Profile owner, long itemId, MeetupLocation location) {
        fillMeetupLocation(owner, itemId, location, null);
    }

    public void fillMeetupLocation(Profile owner, long itemId, MeetupLocation location, Instant meetupAt) {
        if (meetupAt != null) requireFutureTime(meetupAt);
        database.inWriteTransaction(() -> {
            active(owner);
            var item = items.findById(itemId)
                    .filter(value -> value.ownerId() == owner.id())
                    .orElseThrow(() -> new IllegalArgumentException("只有发布人可以补填交易地点。"));
            require(items.setInitialMeetupLocation(item.id(), location, meetupAt), "当前物品无法补填交易地点，请刷新。");
        });
    }

    public void request(Profile actor, long itemId, String message) {
        request(actor, itemId, message, 1);
    }

    public void request(Profile actor, long itemId, String message, int quantity) {
        String body = required(message, 500, "留言");
        require(quantity >= 1 && quantity <= 9999, "申请数量须为 1–9999 件。");
        database.inWriteTransaction(() -> {
            active(actor);
            var item = items.findById(itemId)
                    .filter(value -> value.status() == ItemStatus.AVAILABLE)
                    .orElseThrow(() -> new IllegalArgumentException("物品已不在架，请刷新列表。"));
            require(users.findById(item.ownerId()).filter(User::enabled).isPresent(), "物品已不在架，请刷新列表。");
            require(item.ownerId() != actor.id(), "不能对自己发布的物品发起意向。");
            require(item.meetupLocation() != null, "请等发布人补填交易地点后再发起意向。");
            require(quantity <= item.inventory().available(), "申请数量超过剩余库存，请刷新后重新选择。");
            try {
                exchanges.insertWithFirstMessage(itemId, actor.id(), body, Instant.now(), quantity);
            } catch (UniqueConstraintException duplicate) {
                throw new IllegalArgumentException("你已经提交过待处理意向。", duplicate);
            }
        });
    }

    public List<Exchange> history(Profile actor) {
        return exchanges.findByParticipant(actor.id()).stream().map(exchange -> {
            var item = items.findById(exchange.itemId()).orElseThrow();
            var messages = exchanges.findMessages(exchange.id());
            String firstMessage = messages.stream()
                    .findFirst().map(message -> message.body()).orElse(null);
            Integer stars = reviews.findByExchange(exchange.id()).stream()
                    .filter(review -> review.reviewerId() == actor.id())
                    .findFirst().map(Review::stars).orElse(null);
            Terms terms = negotiations.terms(exchange.id());
            return new Exchange(exchange.id(), item.id(), item.title(), item.ownerId(), exchange.requesterId(),
                    Status.valueOf(exchange.status().name()), firstMessage, stars, terms.location(),
                    terms.priceCents(), messages, negotiations.proposals(exchange.id()), pendingCount(item.id()),
                    terms.meetupAt(), exchange.createdAt(), exchange.completedAt(), exchange.quantity(), item.inventory());
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
                    requireFutureTime(trade.meetupAt());
                    require(trade.itemStatus() == ItemStatus.AVAILABLE
                                    && trade.quantity() <= trade.inventory().available(), "剩余库存不足，无法接受该数量，请刷新。");
                    moveExchange(exchangeId, trade.status(), ExchangeStatus.ACCEPTED);
                    syncItemState(trade.item());
                    if (items.findById(trade.item()).orElseThrow().inventory().available() == 0) {
                        exchanges.rejectOtherPendingByItem(trade.item(), exchangeId);
                    }
                }
                case COMPLETE -> {
                    require(owner && trade.status() == ExchangeStatus.ACCEPTED, "只有发布人可以确认已接受的流转。");
                    require(trade.location() != null, "请先补填交易地点。");
                    require(trade.meetupAt() != null, "请先与对方协商交易时间。");
                    moveExchange(exchangeId, trade.status(), ExchangeStatus.COMPLETED);
                    syncItemState(trade.item());
                }
                case REJECT -> {
                    require(owner && trade.status() == ExchangeStatus.PENDING, "只有发布人可以拒绝待处理意向。");
                    moveExchange(exchangeId, trade.status(), ExchangeStatus.REJECTED);
                }
                case CANCEL -> {
                    if (trade.status() != ExchangeStatus.ACCEPTED) {
                        require(!owner && trade.status() == ExchangeStatus.PENDING, "当前意向不能撤回。");
                    }
                    moveExchange(exchangeId, trade.status(), ExchangeStatus.CANCELLED);
                    if (trade.status() == ExchangeStatus.ACCEPTED) {
                        syncItemState(trade.item());
                        if (trade.inventory().available() == 0) {
                            notifications.recordReopened(exchangeId, trade.item(), actor.id(), trade.owner(),
                                    items.findById(trade.item()).orElseThrow().title(), Instant.now());
                        }
                    }
                }
            }
            negotiations.expireClosed();
        });
    }

    public void sendMessage(Profile actor, long exchangeId, String message) {
        String text = required(message, 500, "留言");
        database.inWriteTransaction(() -> {
            active(actor);
            requireOpen(trade(exchangeId, actor));
            exchanges.insertMessage(exchangeId, actor.id(), text, Instant.now());
        });
    }

    public List<Notice> notifications(Profile actor) {
        active(actor);
        return notifications.inbox(actor.id());
    }

    public void markNotificationRead(Profile actor, long notificationId) {
        database.inWriteTransaction(() -> {
            active(actor);
            require(notifications.markRead(notificationId, actor.id(), Instant.now()), "消息不存在或你无权查看。");
        });
    }

    public void proposeTerms(Profile actor, long exchangeId, Long priceCents, MeetupLocation location) {
        database.inWriteTransaction(() -> {
            active(actor);
            trade(exchangeId, actor);
            proposeTerms(actor, exchangeId, priceCents, location, negotiations.terms(exchangeId).meetupAt());
        });
    }

    public void proposeTerms(Profile actor, long exchangeId, Long priceCents, MeetupLocation location, Instant meetupAt) {
        require(location != null, "请填写交易校区和具体地点。");
        requireFutureTime(meetupAt);
        database.inWriteTransaction(() -> {
            active(actor);
            Trade trade = trade(exchangeId, actor);
            requireOpen(trade);
            long peer = trade.owner() == actor.id() ? trade.requester() : trade.owner();
            require(users.findById(peer).filter(User::enabled).isPresent(), "对方账号当前不可用。");
            Terms current = negotiations.terms(exchangeId);
            boolean sale = items.findById(trade.item()).orElseThrow().tradeType()
                    == edu.campus.circulation.domain.TradeType.SALE;
            require(sale ? priceCents != null && priceCents > 0 : priceCents == null,
                    sale ? "请输入大于零的售价。" : "换物仅支持协商交易地点和时间。");
            require(!Objects.equals(priceCents, current.priceCents()) || !location.equals(current.location())
                            || !Objects.equals(meetupAt, current.meetupAt()),
                    "请至少修改价格、交易地点或时间中的一项。");
            require(negotiations.pending(exchangeId).isEmpty(), "已有待回复的提案，请先处理。");
            negotiations.propose(exchangeId, actor.id(), new Terms(priceCents, location, current.version(), meetupAt), Instant.now());
        });
    }

    public void respondToProposal(Profile actor, long exchangeId, long proposalId, boolean agree) {
        database.inWriteTransaction(() -> {
            active(actor);
            Trade trade = trade(exchangeId, actor);
            requireOpen(trade);
            Proposal proposal = negotiations.pending(exchangeId)
                    .filter(value -> value.id() == proposalId)
                    .orElseThrow(() -> new IllegalArgumentException("提案已处理，请刷新。"));
            require(proposal.proposerId() != actor.id(), "请由另一方确认提案。");
            require(users.findById(proposal.proposerId()).filter(User::enabled).isPresent(), "提案人账号当前不可用。");
            if (agree) {
                requireFutureTime(proposal.meetupAt());
                require(negotiations.apply(proposal), "交易条件已变化，请重新协商。");
            }
            require(negotiations.resolve(proposalId, actor.id(), agree ? "AGREED" : "DECLINED", Instant.now()),
                    "提案已处理，请刷新。");
        });
    }

    public void withdrawProposal(Profile actor, long exchangeId, long proposalId) {
        database.inWriteTransaction(() -> {
            active(actor);
            requireOpen(trade(exchangeId, actor));
            Proposal proposal = negotiations.pending(exchangeId).filter(value -> value.id() == proposalId)
                    .orElseThrow(() -> new IllegalArgumentException("提案已处理，请刷新。"));
            require(proposal.proposerId() == actor.id(), "只有提案人可以撤回。");
            require(negotiations.resolve(proposalId, actor.id(), "WITHDRAWN", Instant.now()), "提案已处理，请刷新。");
        });
    }

    private static void requireOpen(Trade trade) {
        require(trade.status() == ExchangeStatus.PENDING || trade.status() == ExchangeStatus.ACCEPTED,
                "当前流转已结束，可查看历史沟通记录。");
    }

    private int pendingCount(long itemId) {
        return (int) exchanges.findByItem(itemId).stream()
                .filter(exchange -> exchange.status() == ExchangeStatus.PENDING).count();
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
        return creditFor(actor.id());
    }

    private Credit creditFor(long userId) {
        var summary = reviews.getCreditSummary(userId);
        return new Credit(summary.reviewCount(), summary.averageStars() == null ? 0 : summary.averageStars());
    }

    public PeerProfile peerProfile(Profile actor, long exchangeId) {
        active(actor);
        Trade trade = trade(exchangeId, actor);
        long peerId = actor.id() == trade.owner() ? trade.requester() : trade.owner();
        var peer = users.findById(peerId)
                .orElseThrow(() -> new IllegalArgumentException("对方账号不存在。"));
        return new PeerProfile(peerId, creditFor(peerId), peer.createdAt());
    }

    public Instant joinedAt(Profile actor) {
        return users.findById(actor.id()).orElseThrow().createdAt();
    }

    public List<OwnedItem> myItems(Profile actor) {
        return items.findByOwner(actor.id()).stream()
                .map(item -> new OwnedItem(item.title(), item.status(), item.meetupLocation(), item.meetupAt(), item.inventory()))
                .toList();
    }

    private Trade trade(long exchangeId, Profile actor) {
        var exchange = exchanges.findById(exchangeId)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在或你无权操作。"));
        var item = items.findById(exchange.itemId()).orElseThrow();
        require(item.ownerId() == actor.id() || exchange.requesterId() == actor.id(), "记录不存在或你无权操作。");
        var terms = negotiations.terms(exchangeId);
        return new Trade(item.id(), item.ownerId(), exchange.requesterId(), exchange.status(),
                terms.location(), terms.meetupAt(), exchange.quantity(), item.inventory(), item.status());
    }

    private void active(Profile actor) {
        require(users.findById(actor.id()).filter(User::enabled).isPresent(), "当前演示身份不可用。");
    }

    private void syncItemState(long itemId) {
        var item = items.findById(itemId).orElseThrow();
        require(item.status() != ItemStatus.WITHDRAWN, "物品已下架，请刷新。");
        Inventory stock = item.inventory();
        ItemStatus next = stock.available() > 0 ? ItemStatus.AVAILABLE
                : stock.reserved() > 0 ? ItemStatus.RESERVED : ItemStatus.COMPLETED;
        require(items.updateStatus(itemId, item.status(), next), "物品状态已变化，请刷新。");
    }

    private void moveExchange(long id, ExchangeStatus previous, ExchangeStatus next) {
        Instant completedAt = next == ExchangeStatus.COMPLETED ? Instant.now() : null;
        require(exchanges.transitionStatus(id, previous, next, completedAt), "意向状态或剩余库存已变化，请刷新。");
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

    private static void requireFutureTime(Instant meetupAt) {
        require(meetupAt != null, "请填写交易时间，旧记录可通过协商条件补充。");
        require(meetupAt.isAfter(Instant.now()), "交易时间已过，请协商一个未来的交易时间。");
    }

    private record Trade(long item, long owner, long requester, ExchangeStatus status, MeetupLocation location,
                         Instant meetupAt, int quantity, Inventory inventory, ItemStatus itemStatus) {}

    public record Profile(long id, String label) {
        public static final Profile LIN = new Profile(1, "林同学");
        public static final Profile CHEN = new Profile(2, "陈同学");

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
            TradeType type, Long priceCents, String swapNote, MeetupLocation location, Instant meetupAt, int quantity) {
        public Draft(long categoryId, String title, String description, Condition condition,
                TradeType type, Long priceCents, String swapNote, MeetupLocation location, Instant meetupAt) {
            this(categoryId, title, description, condition, type, priceCents, swapNote, location, meetupAt, 1);
        }
        public Draft(long categoryId, String title, String description, Condition condition,
                TradeType type, Long priceCents, String swapNote, MeetupLocation location) {
            this(categoryId, title, description, condition, type, priceCents, swapNote, location, null);
        }
    }

    public record Item(long id, long ownerId, String title, String description, String category,
            Condition condition, TradeType type, long priceCents, String swapNote, MeetupLocation location,
            int pendingCount, Instant meetupAt, Inventory inventory) {}

    public record Exchange(long id, long itemId, String itemTitle, long ownerId, long requesterId, Status status,
            String message, Integer stars, MeetupLocation location, Long priceCents,
            List<ExchangeMessage> messages, List<Proposal> proposals, int pendingCount, Instant meetupAt,
            Instant createdAt, Instant completedAt, int quantity, Inventory inventory) {}

    public record Credit(int count, double average) {}

    public record PeerProfile(long id, Credit credit, Instant joinedAt) {
        public long daysSinceJoining(Instant now) {
            return Math.max(0, ChronoUnit.DAYS.between(joinedAt.atZone(CAMPUS_ZONE).toLocalDate(),
                    now.atZone(CAMPUS_ZONE).toLocalDate()));
        }
    }

    public record OwnedItem(String title, ItemStatus status, MeetupLocation location, Instant meetupAt, Inventory inventory) {}

}
