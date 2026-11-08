package edu.campus.circulation.service;

import static edu.campus.circulation.repository.DemoDatabase.now;
import static edu.campus.circulation.repository.DemoDatabase.query;
import static edu.campus.circulation.repository.DemoDatabase.update;

import edu.campus.circulation.repository.DemoDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class DemoService {
    private final DemoDatabase database;

    public DemoService(DemoDatabase database) {
        this.database = database;
    }

    public List<Category> categories() throws SQLException {
        return database.read(connection -> query(connection,
                "SELECT id, name FROM categories WHERE enabled = 1 ORDER BY id",
                row -> new Category(row.getLong("id"), row.getString("name"))));
    }

    public List<Item> market(String keyword, Long categoryId) throws SQLException {
        return database.read(connection -> query(connection, """
                SELECT i.id, i.owner_id, i.title, i.description, c.name, i.condition,
                       i.trade_type, i.price_cents, i.swap_note
                FROM items i JOIN categories c ON c.id = i.category_id
                JOIN users u ON u.id = i.owner_id
                WHERE i.status = 'AVAILABLE' AND u.enabled = 1
                  AND (? IS NULL OR i.category_id = ?)
                  AND (instr(lower(i.title), lower(?)) > 0 OR instr(lower(i.description), lower(?)) > 0
                       OR ? = '')
                ORDER BY i.id DESC
                """, row -> new Item(row.getLong("id"), row.getLong("owner_id"), row.getString("title"),
                row.getString("description"), row.getString("name"), Condition.valueOf(row.getString("condition")),
                TradeType.valueOf(row.getString("trade_type")), row.getLong("price_cents"),
                row.getString("swap_note")), categoryId, categoryId, keyword.strip(), keyword.strip(), keyword.strip()));
    }

    public void publish(Profile owner, Draft draft) throws SQLException {
        String title = required(draft.title(), 80, "物品名称");
        String description = required(draft.description(), 1000, "物品描述");
        Long price = draft.type() == TradeType.SALE ? draft.priceCents() : null;
        String swap = draft.type() == TradeType.SWAP ? required(draft.swapNote(), 200, "换物说明") : null;
        require(draft.type() != TradeType.SALE || (price != null && price > 0), "请输入大于零的售价。");
        database.write(connection -> {
            active(connection, owner);
            require(!query(connection, "SELECT id FROM categories WHERE id = ? AND enabled = 1",
                    row -> row.getLong(1), draft.categoryId()).isEmpty(), "该分类不可用。");
            update(connection, """
                    INSERT INTO items (owner_id, category_id, title, description, condition,
                                       trade_type, price_cents, swap_note, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, owner.id(), draft.categoryId(), title, description, draft.condition().name(),
                    draft.type().name(), price, swap, now());
            return null;
        });
    }

    public void request(Profile actor, long itemId, String message) throws SQLException {
        String body = required(message, 500, "留言");
        database.write(connection -> {
            active(connection, actor);
            long owner = only(query(connection, """
                    SELECT i.owner_id FROM items i JOIN users u ON u.id = i.owner_id
                    WHERE i.id = ? AND i.status = 'AVAILABLE' AND u.enabled = 1
                    """, row -> row.getLong(1), itemId), "物品已不在架，请刷新列表。");
            require(owner != actor.id(), "不能对自己发布的物品发起意向。");
            require(query(connection, """
                    SELECT id FROM exchanges WHERE item_id = ? AND requester_id = ? AND status = 'PENDING'
                    """, row -> row.getLong(1), itemId, actor.id()).isEmpty(), "你已经提交过待处理意向。");
            String timestamp = now();
            long exchangeId = query(connection, """
                    INSERT INTO exchanges (item_id, requester_id, created_at) VALUES (?, ?, ?) RETURNING id
                    """, row -> row.getLong(1), itemId, actor.id(), timestamp).get(0);
            update(connection, "INSERT INTO exchange_messages (exchange_id, sender_id, body, sent_at) VALUES (?, ?, ?, ?)",
                    exchangeId, actor.id(), body, timestamp);
            return null;
        });
    }

    public List<Exchange> history(Profile actor) throws SQLException {
        return database.read(connection -> query(connection, """
                SELECT e.id, i.title, i.owner_id, e.requester_id, e.status, r.stars,
                       (SELECT m.body FROM exchange_messages m WHERE m.exchange_id = e.id ORDER BY m.id LIMIT 1) message
                FROM exchanges e JOIN items i ON i.id = e.item_id
                LEFT JOIN reviews r ON r.exchange_id = e.id AND r.reviewer_id = ?
                WHERE e.requester_id = ? OR i.owner_id = ?
                ORDER BY e.id DESC
                """, row -> new Exchange(row.getLong("id"), row.getString("title"),
                row.getLong("owner_id"), row.getLong("requester_id"), Status.valueOf(row.getString("status")),
                row.getString("message"), row.getObject("stars", Integer.class)),
                actor.id(), actor.id(), actor.id()));
    }

    public void transition(Profile actor, long exchangeId, Action action) throws SQLException {
        database.write(connection -> {
            active(connection, actor);
            Trade trade = trade(connection, exchangeId, actor);
            boolean owner = actor.id() == trade.owner();
            switch (action) {
                case ACCEPT -> {
                    require(owner && trade.status() == Status.PENDING, "只有发布人可以接受待处理意向。");
                    moveItem(connection, trade.item(), "AVAILABLE", "RESERVED");
                    moveExchange(connection, exchangeId, trade.status(), Status.ACCEPTED);
                    update(connection, "UPDATE exchanges SET status = 'REJECTED' WHERE item_id = ? AND status = 'PENDING'",
                            trade.item());
                }
                case COMPLETE -> {
                    require(owner && trade.status() == Status.ACCEPTED, "只有发布人可以确认已接受的流转。");
                    moveItem(connection, trade.item(), "RESERVED", "COMPLETED");
                    moveExchange(connection, exchangeId, trade.status(), Status.COMPLETED);
                }
                case REJECT -> {
                    require(owner && trade.status() == Status.PENDING, "只有发布人可以拒绝待处理意向。");
                    moveExchange(connection, exchangeId, trade.status(), Status.REJECTED);
                }
                case CANCEL -> {
                    if (trade.status() == Status.ACCEPTED) {
                        moveItem(connection, trade.item(), "RESERVED", "AVAILABLE");
                    } else {
                        require(!owner && trade.status() == Status.PENDING, "当前意向不能撤回。");
                    }
                    moveExchange(connection, exchangeId, trade.status(), Status.CANCELLED);
                }
            }
            return null;
        });
    }

    public void review(Profile actor, long exchangeId, int stars, String comment) throws SQLException {
        require(stars >= 1 && stars <= 5, "星级须在 1 到 5 之间。");
        String text = comment.strip();
        require(text.codePointCount(0, text.length()) <= 500, "短评最多 500 字。");
        database.write(connection -> {
            active(connection, actor);
            Trade trade = trade(connection, exchangeId, actor);
            require(trade.status() == Status.COMPLETED, "完成流转后才可以评价。");
            long peer = actor.id() == trade.owner() ? trade.requester() : trade.owner();
            require(update(connection, """
                    INSERT INTO reviews (exchange_id, reviewer_id, reviewee_id, stars, comment, created_at)
                    VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (exchange_id, reviewer_id) DO NOTHING
                    """, exchangeId, actor.id(), peer, stars, text, now()) == 1, "你已评价过本次流转。");
            return null;
        });
    }

    public Credit credit(Profile actor) throws SQLException {
        return database.read(connection -> query(connection, """
                SELECT count(*), avg(stars) FROM reviews WHERE reviewee_id = ?
                """, row -> new Credit(row.getInt(1), row.getDouble(2)), actor.id()).get(0));
    }

    private static Trade trade(Connection connection, long exchangeId, Profile actor) throws SQLException {
        return only(query(connection, """
                SELECT i.id, i.owner_id, e.requester_id, e.status
                FROM exchanges e JOIN items i ON i.id = e.item_id
                WHERE e.id = ? AND (i.owner_id = ? OR e.requester_id = ?)
                """, row -> new Trade(row.getLong(1), row.getLong(2), row.getLong(3), Status.valueOf(row.getString(4))),
                exchangeId, actor.id(), actor.id()), "记录不存在或你无权操作。");
    }

    private static void active(Connection connection, Profile actor) throws SQLException {
        require(!query(connection, "SELECT id FROM users WHERE id = ? AND enabled = 1",
                row -> row.getLong(1), actor.id()).isEmpty(), "当前演示身份不可用。");
    }

    private static void moveItem(Connection connection, long itemId, String previous, String next)
            throws SQLException {
        require(update(connection, "UPDATE items SET status = ? WHERE id = ? AND status = ?",
                next, itemId, previous) == 1, "物品状态已变化，请刷新。");
    }

    private static void moveExchange(Connection connection, long id, Status previous, Status next)
            throws SQLException {
        require(update(connection, "UPDATE exchanges SET status = ?, completed_at = ? WHERE id = ? AND status = ?",
                next.name(), next == Status.COMPLETED ? now() : null, id, previous.name()) == 1,
                "意向状态已变化，请刷新。");
    }

    private static String required(String value, int max, String field) {
        String text = value.strip();
        require(!text.isEmpty() && text.codePointCount(0, text.length()) <= max,
                field + "不能为空，最多 " + max + " 字。");
        return text;
    }

    private static <T> T only(List<T> rows, String message) {
        require(!rows.isEmpty(), message);
        return rows.get(0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

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
            TradeType type, Long priceCents, String swapNote) {}

    public record Item(long id, long ownerId, String title, String description, String category,
            Condition condition, TradeType type, long priceCents, String swapNote) {}

    public record Exchange(long id, String itemTitle, long ownerId, long requesterId, Status status,
            String message, Integer stars) {}

    public record Credit(int count, double average) {}

    private record Trade(long item, long owner, long requester, Status status) {}
}
