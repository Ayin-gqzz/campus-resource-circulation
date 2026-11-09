package edu.campus.circulation.repository;

import edu.campus.circulation.domain.Item;
import edu.campus.circulation.domain.ItemCondition;
import edu.campus.circulation.domain.ItemDraft;
import edu.campus.circulation.domain.ItemSearchFilter;
import edu.campus.circulation.domain.ItemStatus;
import edu.campus.circulation.domain.Role;
import edu.campus.circulation.domain.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-08T08:30:00.123Z");

    @TempDir
    Path directory;

    private DatabaseManager database;
    private UserRepository users;
    private CategoryRepository categories;
    private ItemRepository items;

    @BeforeEach
    void setUp() {
        database = new DatabaseManager("jdbc:sqlite:" + directory.resolve("test.db"));
        database.initialize();
        users = new JdbcUserRepository(database);
        categories = new JdbcCategoryRepository(database);
        items = new JdbcItemRepository(database);
    }

    @Test
    void publishesSaleAndSwapItemsWithAvailableStatusAndNormalizedTags() {
        long owner = student("owner");
        long books = category("Books");
        long saleId = items.insert(owner, new ItemDraft(books, "Java Book", "Used textbook", ItemCondition.GOOD,
                TradeType.SALE, 4_500L, null, List.of(" Textbooks ", "textbooks", "JAVA")), NOW);
        assertEquals(new Item(saleId, owner, books, "Java Book", "Used textbook", ItemCondition.GOOD,
                        TradeType.SALE, 4_500L, null, ItemStatus.AVAILABLE, List.of("JAVA", "Textbooks"), NOW),
                items.findById(saleId).orElseThrow());

        long swapId = items.insert(owner, new ItemDraft(books, "Old Lamp", "Broken shade", ItemCondition.FAIR,
                TradeType.SWAP, null, "Any working lamp", List.of()), NOW.plusSeconds(60));
        assertEquals(new Item(swapId, owner, books, "Old Lamp", "Broken shade", ItemCondition.FAIR,
                        TradeType.SWAP, null, "Any working lamp", ItemStatus.AVAILABLE, List.of(),
                        NOW.plusSeconds(60)),
                items.findById(swapId).orElseThrow());

        assertEquals(List.of(swapId, saleId), ids(items.findByOwner(owner)));
        assertEquals(List.of(), ids(items.findByOwner(9999)));
        assertTrue(items.findById(9999).isEmpty());
    }

    @Test
    void checkConstraintsRejectMismatchedTradeFieldsAndInvalidTags() {
        long owner = student("owner");
        long books = category("Books");
        assertCheckFailure(() -> items.insert(owner,
                new ItemDraft(books, "Sale with note", "Description", ItemCondition.GOOD, TradeType.SALE,
                        100L, "Note", List.of()), NOW));
        assertCheckFailure(() -> items.insert(owner,
                new ItemDraft(books, "Sale without price", "Description", ItemCondition.GOOD, TradeType.SALE,
                        null, null, List.of()), NOW));
        assertCheckFailure(() -> items.insert(owner,
                new ItemDraft(books, "Sale at zero", "Description", ItemCondition.GOOD, TradeType.SALE,
                        0L, null, List.of()), NOW));
        assertCheckFailure(() -> items.insert(owner,
                new ItemDraft(books, "Swap with price", "Description", ItemCondition.GOOD, TradeType.SWAP,
                        100L, "Note", List.of()), NOW));
        assertCheckFailure(() -> items.insert(owner,
                new ItemDraft(books, "Swap without note", "Description", ItemCondition.GOOD, TradeType.SWAP,
                        null, null, List.of()), NOW));
        assertCheckFailure(() -> items.insert(owner,
                new ItemDraft(books, "Swap with blank note", "Description", ItemCondition.GOOD, TradeType.SWAP,
                        null, "   ", List.of()), NOW));
        assertCheckFailure(() -> items.insert(owner, saleDraft(books, "Tag too long", 100L,
                List.of("valid", "x".repeat(25))), NOW));
        assertCheckFailure(() -> items.insert(owner, saleDraft(books, "Blank tag", 100L, List.of("ok", " ")), NOW));

        assertEquals(List.of(), ids(items.findByOwner(owner)));
    }

    @Test
    void updatesEditableFieldsAndReplacesTagsAtomically() {
        long owner = student("owner");
        long books = category("Books");
        long electronics = category("Electronics");
        long itemId = items.insert(owner, saleDraft(books, "Desk", 9_900L, List.of("wood", "Desk")), NOW);

        ItemDraft edited = new ItemDraft(electronics, "Desk Lamp", "Bright lamp", ItemCondition.NEW,
                TradeType.SWAP, null, "A chair", List.of("lamp", "LAMP", "Bright"));
        assertTrue(items.updateEditableFields(itemId, ItemStatus.AVAILABLE, edited));
        Item updated = items.findById(itemId).orElseThrow();
        assertEquals(new Item(itemId, owner, electronics, "Desk Lamp", "Bright lamp", ItemCondition.NEW,
                TradeType.SWAP, null, "A chair", ItemStatus.AVAILABLE, List.of("Bright", "lamp"), NOW), updated);

        assertFalse(items.updateEditableFields(itemId, ItemStatus.RESERVED,
                saleDraft(books, "Ignored", 100L, List.of("ignored"))));
        assertEquals(updated, items.findById(itemId).orElseThrow());

        assertFalse(items.updateEditableFields(9_999L, ItemStatus.AVAILABLE, edited));
        assertEquals(1, ids(items.findByOwner(owner)).size());
    }

    @Test
    void updateRollsBackItemRowWhenReplacementTagIsInvalid() {
        long owner = student("owner");
        long books = category("Books");
        long itemId = items.insert(owner, saleDraft(books, "Original", 1_000L, List.of("keep")), NOW);
        Item before = items.findById(itemId).orElseThrow();

        ItemDraft invalid = saleDraft(books, "Changed", 2_000L, List.of("keep", "y".repeat(25)));
        assertCheckFailure(() -> items.updateEditableFields(itemId, ItemStatus.AVAILABLE, invalid));

        assertEquals(before, items.findById(itemId).orElseThrow());
    }

    @Test
    void updateStatusRequiresExpectedCurrentStatus() {
        long owner = student("owner");
        long itemId = items.insert(owner, saleDraft(category("Books"), "Lamp", 1_000L, List.of()), NOW);

        assertTrue(items.updateStatus(itemId, ItemStatus.AVAILABLE, ItemStatus.RESERVED));
        assertEquals(ItemStatus.RESERVED, items.findById(itemId).orElseThrow().status());
        assertFalse(items.updateStatus(itemId, ItemStatus.AVAILABLE, ItemStatus.WITHDRAWN));
        assertEquals(ItemStatus.RESERVED, items.findById(itemId).orElseThrow().status());
        assertFalse(items.updateStatus(9_999L, ItemStatus.AVAILABLE, ItemStatus.WITHDRAWN));
    }

    @Test
    void searchAvailableFiltersByCategoryKeywordAndPriceRange() {
        SearchFixture fixture = createSearchFixture();

        assertEquals(List.of(fixture.swap(), fixture.underscore(), fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(null, null, null, null))));
        assertEquals(List.of(fixture.swap(), fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(fixture.books(), null, null, null))));
        assertEquals(List.of(fixture.underscore()),
                ids(items.searchAvailable(new ItemSearchFilter(fixture.electronics(), null, null, null))));
        assertEquals(List.of(),
                ids(items.searchAvailable(new ItemSearchFilter(null, "missing", null, null))));
    }

    @Test
    void searchAvailableMatchesKeywordCaseInsensitivelyAndEscapesWildcards() {
        SearchFixture fixture = createSearchFixture();

        assertEquals(List.of(fixture.swap(), fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(null, "java", null, null))));
        assertEquals(List.of(fixture.swap(), fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(null, "JAVA", null, null))));
        assertEquals(List.of(fixture.underscore()),
                ids(items.searchAvailable(new ItemSearchFilter(null, "adapter", null, null))));
        assertEquals(List.of(fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(null, "%", null, null))));
        assertEquals(List.of(fixture.underscore()),
                ids(items.searchAvailable(new ItemSearchFilter(null, "_", null, null))));
        assertEquals(List.of(fixture.swap(), fixture.underscore(), fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(null, "   ", null, null))));
        assertEquals(List.of(fixture.swap(), fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(fixture.books(), "java", null, null))));
    }

    @Test
    void searchAvailableMatchesBackslashLiterally() {
        long owner = student("owner");
        long books = category("Books");
        long backslashId = items.insert(owner, new ItemDraft(books, "Path\\To\\Notes", "Description",
                ItemCondition.GOOD, TradeType.SALE, 100L, null, List.of()), NOW);

        assertEquals(List.of(backslashId),
                ids(items.searchAvailable(new ItemSearchFilter(null, "\\", null, null))));
        assertEquals(List.of(backslashId),
                ids(items.searchAvailable(new ItemSearchFilter(null, "path\\to", null, null))));
    }

    @Test
    void searchAvailableAppliesInclusivePriceBoundsToSaleItemsOnly() {
        SearchFixture fixture = createSearchFixture();

        assertEquals(List.of(fixture.underscore(), fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(null, null, 2_000L, 5_000L))));
        assertEquals(List.of(fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(null, null, 5_000L, null))));
        assertEquals(List.of(fixture.underscore()),
                ids(items.searchAvailable(new ItemSearchFilter(null, null, null, 2_000L))));
        assertEquals(List.of(fixture.underscore(), fixture.percent()),
                ids(items.searchAvailable(new ItemSearchFilter(null, null, 0L, 10_000L))));
        assertEquals(List.of(),
                ids(items.searchAvailable(new ItemSearchFilter(null, null, 1_000_000L, null))));
    }

    @Test
    void searchAvailableHidesDisabledOwnersAndUnavailableItems() {
        long owner = student("owner");
        long disabledOwner = student("disabled");
        assertTrue(users.updateEnabled(disabledOwner, false));
        long books = category("Books");

        long visibleId = items.insert(owner, saleDraft(books, "Visible", 1_000L, List.of()), NOW);
        long hiddenId = items.insert(disabledOwner, saleDraft(books, "Hidden owner", 1_000L, List.of()), NOW);
        long withdrawnId = items.insert(owner, saleDraft(books, "Withdrawn", 1_000L, List.of()), NOW);
        assertTrue(items.updateStatus(withdrawnId, ItemStatus.AVAILABLE, ItemStatus.WITHDRAWN));

        assertEquals(List.of(visibleId), ids(items.searchAvailable(noFilter())));
        assertTrue(items.findById(hiddenId).isPresent());

        assertTrue(users.updateEnabled(disabledOwner, true));
        assertEquals(List.of(hiddenId, visibleId), ids(items.searchAvailable(noFilter())));
    }

    @Test
    void favoritesAreIdempotentAndRemovable() {
        long student = student("student");
        long other = student("other");
        long books = category("Books");
        long first = items.insert(student, saleDraft(books, "First", 1_000L, List.of("alpha")), NOW);
        long second = items.insert(student, saleDraft(books, "Second", 2_000L, List.of()), NOW.plusSeconds(60));

        assertFalse(items.isFavorite(student, first));
        assertEquals(List.of(), ids(items.findFavoriteItems(student)));

        items.addFavorite(student, first, NOW);
        items.addFavorite(student, first, NOW.plusSeconds(30));
        assertTrue(items.isFavorite(student, first));
        assertEquals(1, favoriteCount());
        assertEquals(List.of(first), ids(items.findFavoriteItems(student)));

        items.addFavorite(student, second, NOW.plusSeconds(120));
        items.addFavorite(other, second, NOW);
        assertEquals(List.of(second, first), ids(items.findFavoriteItems(student)));
        assertEquals(List.of(second), ids(items.findFavoriteItems(other)));
        assertEquals(List.of("alpha"), items.findFavoriteItems(student).get(1).tags());
        assertEquals(3, favoriteCount());

        assertTrue(items.removeFavorite(student, first));
        assertFalse(items.removeFavorite(student, first));
        assertFalse(items.isFavorite(student, first));
        assertEquals(List.of(second), ids(items.findFavoriteItems(student)));
        assertEquals(List.of(second), ids(items.findFavoriteItems(other)));
    }

    @Test
    void favoritesRequireExistingUserAndItem() {
        long student = student("student");
        long itemId = items.insert(student, saleDraft(category("Books"), "Lamp", 1_000L, List.of()), NOW);

        assertThrows(ForeignKeyException.class, () -> items.addFavorite(student, 9_999L, NOW));
        assertThrows(ForeignKeyException.class, () -> items.addFavorite(9_999L, itemId, NOW));
        assertEquals(0, favoriteCount());
    }

    private SearchFixture createSearchFixture() {
        long owner = student("owner");
        long otherOwner = student("other");
        long disabledOwner = student("disabled");
        assertTrue(users.updateEnabled(disabledOwner, false));
        long books = category("Books");
        long electronics = category("Electronics");

        long percent = items.insert(owner, new ItemDraft(books, "Java Book 100%", "Complete notes and CD",
                ItemCondition.GOOD, TradeType.SALE, 5_000L, null, List.of("java")), NOW);
        long underscore = items.insert(owner, new ItemDraft(electronics, "Desk_lamp", "Working AC adapter",
                ItemCondition.GOOD, TradeType.SALE, 2_000L, null, List.of("lamp")), NOW.plusSeconds(1));
        long swap = items.insert(otherOwner, new ItemDraft(books, "JAVA notes", "Handwritten summary",
                ItemCondition.FAIR, TradeType.SWAP, null, "Any pen", List.of("java")), NOW.plusSeconds(2));
        items.insert(disabledOwner, new ItemDraft(books, "Hidden sale", "Owner is disabled",
                ItemCondition.GOOD, TradeType.SALE, 1_000L, null, List.of()), NOW.plusSeconds(3));
        long reserved = items.insert(owner, new ItemDraft(electronics, "Reserved sale", "Locked item",
                ItemCondition.GOOD, TradeType.SALE, 3_000L, null, List.of()), NOW.plusSeconds(4));
        assertTrue(items.updateStatus(reserved, ItemStatus.AVAILABLE, ItemStatus.RESERVED));
        return new SearchFixture(books, electronics, percent, underscore, swap);
    }

    private record SearchFixture(long books, long electronics, long percent, long underscore, long swap) {
    }

    private static ItemSearchFilter noFilter() {
        return new ItemSearchFilter(null, null, null, null);
    }

    private ItemDraft saleDraft(long categoryId, String title, long priceCents, List<String> tags) {
        return new ItemDraft(categoryId, title, "Description of " + title, ItemCondition.GOOD,
                TradeType.SALE, priceCents, null, tags);
    }

    private long student(String username) {
        return users.insert(username, "hash", Role.STUDENT, true, NOW);
    }

    private long category(String name) {
        return categories.insert(name, true, null, null);
    }

    private static List<Long> ids(List<Item> items) {
        return items.stream().map(Item::id).toList();
    }

    private static void assertCheckFailure(Executable executable) {
        DataAccessException failure = assertThrows(DataAccessException.class, executable);
        assertEquals(DataAccessException.class, failure.getClass());
    }

    private int favoriteCount() {
        return database.executeWithConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT count(*) FROM favorites")) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        });
    }
}
