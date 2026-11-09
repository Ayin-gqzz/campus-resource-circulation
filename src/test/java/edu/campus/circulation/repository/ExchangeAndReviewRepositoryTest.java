package edu.campus.circulation.repository;

import edu.campus.circulation.domain.CategoryStatRow;
import edu.campus.circulation.domain.CreditSummary;
import edu.campus.circulation.domain.Exchange;
import edu.campus.circulation.domain.ExchangeMessage;
import edu.campus.circulation.domain.ExchangeStatus;
import edu.campus.circulation.domain.ItemCondition;
import edu.campus.circulation.domain.ItemDraft;
import edu.campus.circulation.domain.ItemStatus;
import edu.campus.circulation.domain.Review;
import edu.campus.circulation.domain.Role;
import edu.campus.circulation.domain.StatsFilter;
import edu.campus.circulation.domain.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeAndReviewRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-08T08:30:00.123Z");

    @TempDir
    Path directory;

    private DatabaseManager database;
    private UserRepository users;
    private CategoryRepository categories;
    private ItemRepository items;
    private ExchangeRepository exchanges;
    private ReviewRepository reviews;

    @BeforeEach
    void setUp() {
        database = new DatabaseManager("jdbc:sqlite:" + directory.resolve("test.db"));
        database.initialize();
        users = new JdbcUserRepository(database);
        categories = new JdbcCategoryRepository(database);
        items = new JdbcItemRepository(database);
        exchanges = new JdbcExchangeRepository(database);
        reviews = new JdbcReviewRepository(database);
    }

    @Test
    void requestAndFirstMessageAreAtomicAndPendingRequestsAreUnique() {
        long owner = student("owner");
        long requester = student("requester");
        long item = item(owner, category("Books"));

        assertCheckFailure(() -> exchanges.insertWithFirstMessage(item, requester, "   ", NOW));
        assertCheckFailure(() -> exchanges.insertWithFirstMessage(item, requester, "x".repeat(501), NOW));
        assertEquals(List.of(), exchanges.findByItem(item));
        assertEquals(0, messageCount());

        long exchange = exchanges.insertWithFirstMessage(item, requester, "Interested", NOW);
        assertEquals(new Exchange(exchange, item, requester, ExchangeStatus.PENDING, NOW, null),
                exchanges.findById(exchange).orElseThrow());
        List<ExchangeMessage> messages = exchanges.findMessages(exchange);
        assertEquals(1, messages.size());
        assertEquals(new ExchangeMessage(messages.get(0).id(), exchange, requester, "Interested", NOW),
                messages.get(0));

        assertThrows(UniqueConstraintException.class,
                () -> exchanges.insertWithFirstMessage(item, requester, "Duplicate", NOW.plusSeconds(1)));
        assertEquals(List.of(exchange), ids(exchanges.findByItem(item)));
        assertEquals(messages, exchanges.findMessages(exchange));
        assertEquals(1, messageCount());

        assertTrue(exchanges.transitionStatus(exchange, ExchangeStatus.PENDING, ExchangeStatus.CANCELLED, null));
        long replacement = exchanges.insertWithFirstMessage(item, requester, "Try again", NOW.plusSeconds(2));
        assertEquals(List.of(replacement, exchange), ids(exchanges.findByItem(item)));
    }

    @Test
    void requestsParticipateInAnOuterWriteTransactionWithoutCommittingIt() {
        long owner = student("owner");
        long requester = student("requester");
        long item = item(owner, category("Books"));

        assertThrows(IllegalStateException.class, () -> database.inWriteTransaction(() -> {
            exchanges.insertWithFirstMessage(item, requester, "Interested", NOW);
            assertEquals(1, exchanges.findByItem(item).size());
            assertEquals(1, messageCount());
            throw new IllegalStateException("Abort outer transaction");
        }));

        assertEquals(List.of(), exchanges.findByItem(item));
        assertEquals(0, messageCount());
    }

    @Test
    void messagesAreOrderedByIdNotSentAtAndRequireExistingReferences() {
        long owner = student("owner");
        long requester = student("requester");
        long item = item(owner, category("Books"));
        long exchange = exchanges.insertWithFirstMessage(item, requester, "First", NOW);
        ExchangeMessage first = exchanges.findMessages(exchange).get(0);
        long second = exchanges.insertMessage(exchange, owner, "Reply", NOW.plusSeconds(5));
        long third = exchanges.insertMessage(exchange, requester, "Earlier timestamp", NOW.minusSeconds(1));

        assertEquals(List.of(first, new ExchangeMessage(second, exchange, owner, "Reply", NOW.plusSeconds(5)),
                        new ExchangeMessage(third, exchange, requester, "Earlier timestamp", NOW.minusSeconds(1))),
                exchanges.findMessages(exchange));
        assertEquals(List.of(), exchanges.findMessages(9999));
        assertCheckFailure(() -> exchanges.insertMessage(exchange, owner, "", NOW));
        assertThrows(ForeignKeyException.class, () -> exchanges.insertMessage(9999, owner, "Missing", NOW));
        assertThrows(ForeignKeyException.class, () -> exchanges.insertMessage(exchange, 9999, "Missing", NOW));
        assertEquals(3, messageCount());
    }

    @Test
    void historiesIncludeOwnerAndRequesterAndOrderByTimeThenIdDescending() {
        long owner = student("owner");
        long requester = student("requester");
        long other = student("other");
        long books = category("Books");
        long firstItem = item(owner, books);
        long secondItem = item(other, books);
        long first = exchanges.insertWithFirstMessage(firstItem, requester, "First", NOW);
        long second = exchanges.insertWithFirstMessage(firstItem, other, "Later", NOW.plusSeconds(1));
        long third = exchanges.insertWithFirstMessage(secondItem, owner, "Same timestamp", NOW.plusSeconds(1));
        long fourth = exchanges.insertWithFirstMessage(secondItem, requester, "Older timestamp", NOW);

        assertEquals(List.of(second, first), ids(exchanges.findByItem(firstItem)));
        assertEquals(List.of(third, fourth), ids(exchanges.findByItem(secondItem)));
        assertEquals(List.of(third, second, first), ids(exchanges.findByParticipant(owner)));
        assertEquals(List.of(fourth, first), ids(exchanges.findByParticipant(requester)));
        assertEquals(List.of(third, second, fourth), ids(exchanges.findByParticipant(other)));
        assertEquals(List.of(), exchanges.findByParticipant(9999));
        assertEquals(List.of(), exchanges.findByItem(9999));
        assertTrue(exchanges.findById(9999).isEmpty());
    }

    @Test
    void acceptingRejectsOtherPendingRequestsAndReservesItemInOneTransaction() {
        long owner = student("owner");
        long requester = student("requester");
        long other = student("other");
        long books = category("Books");
        long item = item(owner, books);
        long selected = exchanges.insertWithFirstMessage(item, requester, "Selected", NOW);
        long rejected = exchanges.insertWithFirstMessage(item, other, "Other", NOW);
        long unrelated = exchanges.insertWithFirstMessage(item(owner, books), other, "Unrelated", NOW);
        assertFalse(exchanges.hasAcceptedExchangeForUser(owner));
        assertFalse(exchanges.hasAcceptedExchangeForUser(requester));

        accept(selected, item, ItemStatus.AVAILABLE);

        assertStatus(selected, ExchangeStatus.ACCEPTED);
        assertStatus(rejected, ExchangeStatus.REJECTED);
        assertStatus(unrelated, ExchangeStatus.PENDING);
        assertEquals(ItemStatus.RESERVED, items.findById(item).orElseThrow().status());
        assertTrue(exchanges.hasAcceptedExchangeForUser(owner));
        assertTrue(exchanges.hasAcceptedExchangeForUser(requester));
        assertFalse(exchanges.hasAcceptedExchangeForUser(other));
        assertFalse(exchanges.hasAcceptedExchangeForUser(9999));
        assertEquals(0, exchanges.rejectOtherPendingByItem(item, selected));
    }

    @Test
    void failedItemStepRollsBackAcceptedExchangeAndRejections() {
        long owner = student("owner");
        long requester = student("requester");
        long other = student("other");
        long item = item(owner, category("Books"));
        long selected = exchanges.insertWithFirstMessage(item, requester, "Selected", NOW);
        long competing = exchanges.insertWithFirstMessage(item, other, "Other", NOW);
        List<Exchange> before = exchanges.findByItem(item);

        assertThrows(IllegalStateException.class, () -> accept(selected, item, ItemStatus.RESERVED));

        assertEquals(before, exchanges.findByItem(item));
        assertStatus(competing, ExchangeStatus.PENDING);
        assertEquals(ItemStatus.AVAILABLE, items.findById(item).orElseThrow().status());
        assertFalse(exchanges.hasAcceptedExchangeForUser(owner));
        assertEquals(2, messageCount());
    }

    @Test
    void onlyOneAcceptedOrCompletedExchangeIsAllowedPerItem() {
        long owner = student("owner");
        long requester = student("requester");
        long other = student("other");
        long item = item(owner, category("Books"));
        long selected = exchanges.insertWithFirstMessage(item, requester, "Selected", NOW);
        long competing = exchanges.insertWithFirstMessage(item, other, "Other", NOW);
        assertTrue(exchanges.transitionStatus(selected, ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, null));
        assertThrows(UniqueConstraintException.class, () -> exchanges.transitionStatus(
                competing, ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, null));
        assertStatus(competing, ExchangeStatus.PENDING);
        assertTrue(exchanges.transitionStatus(selected, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETED,
                NOW.plusSeconds(1)));
        assertThrows(UniqueConstraintException.class, () -> exchanges.transitionStatus(
                competing, ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, null));
    }

    @Test
    void completingUpdatesExchangeTimestampAndItemAndRequiresExpectedStatus() {
        long owner = student("owner");
        long requester = student("requester");
        long item = item(owner, category("Books"));
        long exchange = exchanges.insertWithFirstMessage(item, requester, "Interested", NOW);
        accept(exchange, item, ItemStatus.AVAILABLE);
        Instant completedAt = NOW.plusSeconds(60);

        assertFalse(exchanges.transitionStatus(exchange, ExchangeStatus.PENDING, ExchangeStatus.COMPLETED, completedAt));
        assertFalse(exchanges.transitionStatus(9999, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETED, completedAt));
        assertCheckFailure(() -> exchanges.transitionStatus(exchange, ExchangeStatus.ACCEPTED,
                ExchangeStatus.COMPLETED, null));
        assertCheckFailure(() -> exchanges.transitionStatus(exchange, ExchangeStatus.ACCEPTED,
                ExchangeStatus.COMPLETED, NOW.minusMillis(1)));
        assertCheckFailure(() -> exchanges.transitionStatus(exchange, ExchangeStatus.ACCEPTED,
                ExchangeStatus.CANCELLED, completedAt));
        assertStatus(exchange, ExchangeStatus.ACCEPTED);

        database.inWriteTransaction(() -> {
            assertTrue(exchanges.transitionStatus(exchange, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETED, completedAt));
            assertTrue(items.updateStatus(item, ItemStatus.RESERVED, ItemStatus.COMPLETED));
        });

        assertEquals(new Exchange(exchange, item, requester, ExchangeStatus.COMPLETED, NOW, completedAt),
                exchanges.findById(exchange).orElseThrow());
        assertEquals(ItemStatus.COMPLETED, items.findById(item).orElseThrow().status());
        assertFalse(exchanges.hasAcceptedExchangeForUser(owner));
        assertFalse(exchanges.hasAcceptedExchangeForUser(requester));
    }

    @Test
    void bulkCancellationTouchesOnlyPendingRequestsForTheUserOrItem() {
        long owner = student("owner");
        long requester = student("requester");
        long other = student("other");
        long books = category("Books");
        long ownedItem = item(owner, books);
        long otherItem = item(other, books);
        long owned = exchanges.insertWithFirstMessage(ownedItem, requester, "Owned", NOW);
        long requested = exchanges.insertWithFirstMessage(otherItem, owner, "Requested", NOW);
        long unrelated = exchanges.insertWithFirstMessage(otherItem, requester, "Unrelated", NOW);
        long acceptedItem = item(owner, books);
        long accepted = exchanges.insertWithFirstMessage(acceptedItem, requester, "Accepted", NOW);
        accept(accepted, acceptedItem, ItemStatus.AVAILABLE);
        long rejected = exchanges.insertWithFirstMessage(ownedItem, other, "Rejected", NOW);
        assertTrue(exchanges.transitionStatus(rejected, ExchangeStatus.PENDING, ExchangeStatus.REJECTED, null));

        assertEquals(2, exchanges.cancelPendingByUser(owner));
        assertStatus(owned, ExchangeStatus.CANCELLED);
        assertStatus(requested, ExchangeStatus.CANCELLED);
        assertStatus(unrelated, ExchangeStatus.PENDING);
        assertStatus(accepted, ExchangeStatus.ACCEPTED);
        assertStatus(rejected, ExchangeStatus.REJECTED);
        assertEquals(0, exchanges.cancelPendingByUser(owner));
        assertEquals(0, exchanges.cancelPendingByUser(9999));

        long extra = exchanges.insertWithFirstMessage(otherItem, owner, "Again", NOW.plusSeconds(1));
        assertEquals(2, exchanges.cancelPendingByItem(otherItem));
        assertStatus(unrelated, ExchangeStatus.CANCELLED);
        assertStatus(extra, ExchangeStatus.CANCELLED);
        assertStatus(accepted, ExchangeStatus.ACCEPTED);
        assertStatus(rejected, ExchangeStatus.REJECTED);
        assertEquals(0, exchanges.cancelPendingByItem(otherItem));
        assertEquals(0, exchanges.cancelPendingByItem(9999));
    }

    @Test
    void mutualReviewsAreUniquePerReviewerAndCreditIsRoundedWithNullForNoReviews() {
        long owner = student("owner");
        long requester = student("requester");
        long unreviewed = student("unreviewed");
        long books = category("Books");
        long first = completedExchange(owner, requester, books, NOW.plusSeconds(10));
        Instant reviewedAt = NOW.plusSeconds(100);
        Review ownerReview = new Review(first, owner, requester, 4, "Reliable", reviewedAt);
        Review requesterReview = new Review(first, requester, owner, 5, "Helpful", reviewedAt);
        reviews.insert(requesterReview);
        reviews.insert(ownerReview);

        assertEquals(List.of(ownerReview, requesterReview), reviews.findByExchange(first));
        assertThrows(UniqueConstraintException.class,
                () -> reviews.insert(new Review(first, requester, owner, 1, "Duplicate", reviewedAt)));
        assertEquals(new CreditSummary(owner, 1, 5.0), reviews.getCreditSummary(owner));
        assertEquals(new CreditSummary(requester, 1, 4.0), reviews.getCreditSummary(requester));
        assertEquals(new CreditSummary(unreviewed, 0, null), reviews.getCreditSummary(unreviewed));
        assertEquals(new CreditSummary(9999, 0, null), reviews.getCreditSummary(9999));

        long second = completedExchange(owner, requester, books, NOW.plusSeconds(11));
        long third = completedExchange(owner, requester, books, NOW.plusSeconds(12));
        Review secondReview = new Review(second, requester, owner, 5, "", reviewedAt.plusSeconds(1));
        Review thirdReview = new Review(third, requester, owner, 4, "Good", reviewedAt);
        reviews.insert(secondReview);
        reviews.insert(thirdReview);

        assertEquals(new CreditSummary(owner, 3, 4.7), reviews.getCreditSummary(owner));
        assertEquals(List.of(secondReview, thirdReview, requesterReview), reviews.findByReviewee(owner));
        assertEquals(List.of(), reviews.findByExchange(9999));
        assertEquals(List.of(), reviews.findByReviewee(unreviewed));
    }

    @Test
    void reviewsOrderByCreationBeforeReviewerAndTranslateCheckAndForeignKeyFailures() {
        long owner = student("owner");
        long requester = student("requester");
        long exchange = completedExchange(owner, requester, category("Books"), NOW.plusSeconds(10));
        Instant reviewedAt = NOW.plusSeconds(100);
        assertCheckFailure(() -> reviews.insert(new Review(exchange, requester, owner, 0, "", reviewedAt)));
        assertCheckFailure(() -> reviews.insert(new Review(exchange, requester, owner, 6, "", reviewedAt)));
        assertCheckFailure(() -> reviews.insert(new Review(exchange, requester, owner, 5, "x".repeat(501), reviewedAt)));
        assertCheckFailure(() -> reviews.insert(new Review(exchange, owner, owner, 5, "Self", reviewedAt)));
        assertThrows(ForeignKeyException.class,
                () -> reviews.insert(new Review(9999, requester, owner, 5, "", reviewedAt)));
        assertThrows(ForeignKeyException.class,
                () -> reviews.insert(new Review(exchange, 9999, owner, 5, "", reviewedAt)));
        assertThrows(ForeignKeyException.class,
                () -> reviews.insert(new Review(exchange, requester, 9999, 5, "", reviewedAt)));
        assertEquals(List.of(), reviews.findByExchange(exchange));

        Review later = new Review(exchange, owner, requester, 3, "Later", reviewedAt.plusSeconds(1));
        Review earlier = new Review(exchange, requester, owner, 4, "Earlier", reviewedAt);
        reviews.insert(later);
        reviews.insert(earlier);
        assertEquals(List.of(earlier, later), reviews.findByExchange(exchange));
    }

    @Test
    void categoryStatisticsUseHalfOpenCompletionRangeParticipantsAndNullableFactors() {
        long alice = student("alice");
        long bob = student("bob");
        long carol = student("carol");
        long books = categories.insert("Books", true, 1250L, "Book study v1");
        long unknown = category("Unknown");
        long zero = categories.insert("Zero", true, 0L, "Zero estimate v1");
        category("Unused");
        Instant from = NOW.plusSeconds(10);
        Instant to = NOW.plusSeconds(20);
        completedExchange(alice, bob, books, from.minusMillis(1));
        completedExchange(alice, bob, books, from);
        completedExchange(carol, bob, books, from.plusMillis(1));
        completedExchange(bob, carol, unknown, to.minusMillis(1));
        completedExchange(carol, alice, zero, from.plusSeconds(1));
        completedExchange(alice, bob, books, to);
        completedExchange(alice, bob, books, to.plusMillis(1));

        long pendingItem = item(alice, books);
        long pending = exchanges.insertWithFirstMessage(pendingItem, bob, "Pending", NOW);
        long rejected = exchanges.insertWithFirstMessage(pendingItem, carol, "Rejected", NOW);
        assertTrue(exchanges.transitionStatus(rejected, ExchangeStatus.PENDING, ExchangeStatus.REJECTED, null));
        long cancelled = exchanges.insertWithFirstMessage(item(alice, unknown), bob, "Cancelled", NOW);
        assertTrue(exchanges.transitionStatus(cancelled, ExchangeStatus.PENDING, ExchangeStatus.CANCELLED, null));
        long acceptedItem = item(alice, books);
        long accepted = exchanges.insertWithFirstMessage(acceptedItem, bob, "Accepted", NOW);
        accept(accepted, acceptedItem, ItemStatus.AVAILABLE);
        assertStatus(pending, ExchangeStatus.PENDING);
        assertTrue(categories.update(books, "Books", false, 1250L, "Book study v1"));

        CategoryStatRow twoBooks = new CategoryStatRow(books, "Books", 2, 1250L, "Book study v1");
        CategoryStatRow oneUnknown = new CategoryStatRow(unknown, "Unknown", 1, null, null);
        CategoryStatRow oneZero = new CategoryStatRow(zero, "Zero", 1, 0L, "Zero estimate v1");
        assertEquals(List.of(twoBooks, oneUnknown, oneZero),
                reviews.summarizeCompletedByCategory(new StatsFilter(from, to, null, null)));
        assertEquals(List.of(twoBooks, oneUnknown),
                reviews.summarizeCompletedByCategory(new StatsFilter(from, to, null, bob)));
        assertEquals(List.of(new CategoryStatRow(books, "Books", 1, 1250L, "Book study v1"), oneZero),
                reviews.summarizeCompletedByCategory(new StatsFilter(from, to, null, alice)));
        assertEquals(List.of(twoBooks),
                reviews.summarizeCompletedByCategory(new StatsFilter(from, to, books, bob)));
        assertEquals(List.of(), reviews.summarizeCompletedByCategory(new StatsFilter(from, to, unknown, alice)));
        assertEquals(List.of(), reviews.summarizeCompletedByCategory(new StatsFilter(from, to, null, 9999L)));
        assertEquals(List.of(), reviews.summarizeCompletedByCategory(new StatsFilter(to, to, null, null)));
        assertEquals(List.of(new CategoryStatRow(books, "Books", 5, 1250L, "Book study v1"), oneUnknown, oneZero),
                reviews.summarizeCompletedByCategory(new StatsFilter(null, null, null, null)));
        assertEquals(List.of(new CategoryStatRow(books, "Books", 3, 1250L, "Book study v1"), oneUnknown, oneZero),
                reviews.summarizeCompletedByCategory(new StatsFilter(null, to, null, null)));
        assertEquals(List.of(new CategoryStatRow(books, "Books", 2, 1250L, "Book study v1")),
                reviews.summarizeCompletedByCategory(new StatsFilter(to, null, null, null)));
    }

    private void accept(long exchangeId, long itemId, ItemStatus expectedItemStatus) {
        database.inWriteTransaction(() -> {
            if (!exchanges.transitionStatus(exchangeId, ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, null)) {
                throw new IllegalStateException("Exchange status changed");
            }
            exchanges.rejectOtherPendingByItem(itemId, exchangeId);
            if (!items.updateStatus(itemId, expectedItemStatus, ItemStatus.RESERVED)) {
                throw new IllegalStateException("Item status changed");
            }
        });
    }

    private long completedExchange(long owner, long requester, long categoryId, Instant completedAt) {
        long item = item(owner, categoryId);
        long exchange = exchanges.insertWithFirstMessage(item, requester, "Interested", NOW);
        accept(exchange, item, ItemStatus.AVAILABLE);
        database.inWriteTransaction(() -> {
            assertTrue(exchanges.transitionStatus(exchange, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETED, completedAt));
            assertTrue(items.updateStatus(item, ItemStatus.RESERVED, ItemStatus.COMPLETED));
        });
        return exchange;
    }

    private long item(long owner, long categoryId) {
        return items.insert(owner, new ItemDraft(categoryId, "Book", "Used textbook", ItemCondition.GOOD,
                TradeType.SALE, 1000L, null, List.of()), NOW);
    }

    private long student(String username) {
        return users.insert(username, "hash", Role.STUDENT, true, NOW);
    }

    private long category(String name) {
        return categories.insert(name, true, null, null);
    }

    private void assertStatus(long exchangeId, ExchangeStatus status) {
        assertEquals(status, exchanges.findById(exchangeId).orElseThrow().status());
    }

    private static List<Long> ids(List<Exchange> exchanges) {
        return exchanges.stream().map(Exchange::id).toList();
    }

    private static void assertCheckFailure(Executable executable) {
        DataAccessException failure = assertThrows(DataAccessException.class, executable);
        assertEquals(DataAccessException.class, failure.getClass());
    }

    private int messageCount() {
        return database.executeWithConnection(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM exchange_messages")) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        });
    }
}
