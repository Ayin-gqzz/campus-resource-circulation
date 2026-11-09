package edu.campus.circulation.repository;

import edu.campus.circulation.domain.Category;
import edu.campus.circulation.domain.Role;
import edu.campus.circulation.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class UserAndCategoryRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-08T08:30:00.123Z");

    @TempDir
    Path directory;

    private String jdbcUrl;
    private DatabaseManager database;
    private UserRepository users;
    private CategoryRepository categories;

    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:sqlite:" + directory.resolve("test.db");
        database = new DatabaseManager(jdbcUrl);
        database.initialize();
        users = new JdbcUserRepository(database);
        categories = new JdbcCategoryRepository(database);
    }

    @Test
    void initializesSchemaFromClasspathAndIsIdempotent() {
        assertNotNull(DatabaseManager.class.getResource("/database/001_initial.sql"));
        assertEquals(1, scalar("PRAGMA user_version"));
        assertEquals(8, scalar("SELECT count(*) FROM sqlite_master WHERE type = 'table'"));
        long id = users.insert("student", "hash", Role.STUDENT, true, NOW);
        database.initialize();
        new DatabaseManager(jdbcUrl).initialize();
        assertTrue(users.findById(id).isPresent());
        assertEquals(0, scalar("SELECT count(*) FROM pragma_foreign_key_check"));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 99})
    void rejectsUnknownSchemaVersion(int version) {
        execute("PRAGMA user_version = " + version);
        DataAccessException failure = assertThrows(DataAccessException.class, database::initialize);
        assertTrue(failure.getMessage().contains(Integer.toString(version)));
        assertEquals(version, scalar("PRAGMA user_version"));
    }

    @Test
    void rollsBackPartialSchemaInitialization() {
        DatabaseManager broken = new DatabaseManager("jdbc:sqlite:" + directory.resolve("broken.db"));
        broken.executeWithConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE categories (id INTEGER PRIMARY KEY)");
            }
            return null;
        });
        assertThrows(DataAccessException.class, broken::initialize);
        broken.executeWithConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                         SELECT (SELECT user_version FROM pragma_user_version),
                                (SELECT count(*) FROM sqlite_master WHERE name = 'users')
                         """)) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
                assertEquals(0, result.getInt(2));
            }
            return null;
        });
    }

    @Test
    void enablesForeignKeysAndBusyTimeoutOnEveryConnection() {
        for (int i = 0; i < 2; i++) {
            assertEquals(1, scalar("PRAGMA foreign_keys"));
            assertEquals(5000, scalar("PRAGMA busy_timeout"));
        }
        database.inWriteTransaction(() -> {
            assertEquals(1, scalar("PRAGMA foreign_keys"));
            assertEquals(5000, scalar("PRAGMA busy_timeout"));
        });
        ForeignKeyException failure = assertThrows(ForeignKeyException.class, () -> execute("""
                INSERT INTO favorites (user_id, item_id, created_at)
                VALUES (999, 999, '2026-11-08T08:30:00.123Z')
                """));
        assertInstanceOf(SQLException.class, failure.getCause());
    }

    @Test
    void userCrudMapsAllFieldsAndFiltersStudents() {
        assertTrue(users.findById(99).isEmpty());
        assertTrue(users.findByUsername("missing").isEmpty());
        assertTrue(users.findAllStudents().isEmpty());
        long studentId = users.insert("Alice_1", "stored-hash", Role.STUDENT, true, NOW);
        long disabledId = users.insert("Bob", "other-hash", Role.STUDENT, false, NOW);
        long adminId = users.insert("Admin", "admin-hash", Role.ADMIN, true, NOW);
        User alice = new User(studentId, "Alice_1", "stored-hash", Role.STUDENT, true, NOW);
        assertEquals(alice, users.findById(studentId).orElseThrow());
        assertEquals(alice, users.findByUsername("ALICE_1").orElseThrow());
        assertEquals(List.of(studentId, disabledId), users.findAllStudents().stream().map(User::id).toList());
        assertEquals(Role.ADMIN, users.findById(adminId).orElseThrow().role());
        assertFalse(users.findById(disabledId).orElseThrow().enabled());
        assertTrue(users.updateEnabled(studentId, false));
        assertFalse(users.findById(studentId).orElseThrow().enabled());
        assertTrue(users.updateEnabled(studentId, true));
        assertTrue(users.findById(studentId).orElseThrow().enabled());
        assertFalse(users.updateEnabled(999, true));
    }

    @Test
    void usernamesAreCaseInsensitiveAndUnique() {
        users.insert("Alice", "hash", Role.STUDENT, true, NOW);
        UniqueConstraintException failure = assertThrows(UniqueConstraintException.class,
                () -> users.insert("aLiCe", "hash", Role.STUDENT, true, NOW));
        assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals(1, users.findAllStudents().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "abcdefghijklmnopqrstuvwxy", "bad name", "bad-name", "学生账号"})
    void usernameCheckRejectsInvalidNames(String username) {
        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> users.insert(username, "hash", Role.STUDENT, true, NOW));
        assertEquals(DataAccessException.class, failure.getClass());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GUEST", "student", ""})
    void roleCheckRejectsInvalidValues(String role) {
        assertThrows(DataAccessException.class, () -> database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO users (username, password_hash, role, created_at) VALUES ('valid', 'hash', ?, ?)
                    """)) {
                statement.setString(1, role);
                statement.setString(2, UtcTimestamps.format(NOW));
                return statement.executeUpdate();
            }
        }));
    }

    @Test
    void categoryCrudMapsNullableFactorsAndEnabledFiltering() {
        assertTrue(categories.findById(99).isEmpty());
        assertTrue(categories.findByName("missing").isEmpty());
        assertTrue(categories.findAll(false).isEmpty());
        long books = categories.insert("Books", true, null, null);
        long bikes = categories.insert("Bikes", false, 5_000_000_000L, "Study 2026");
        assertEquals(new Category(books, "Books", true, null, null), categories.findById(books).orElseThrow());
        assertEquals(new Category(bikes, "Bikes", false, 5_000_000_000L, "Study 2026"),
                categories.findByName("BIKES").orElseThrow());
        assertEquals(List.of(books, bikes), categories.findAll(false).stream().map(Category::id).toList());
        assertEquals(List.of(books), categories.findAll(true).stream().map(Category::id).toList());
        assertTrue(categories.update(books, "Textbooks", false, 0L, "Zero estimate"));
        assertEquals(new Category(books, "Textbooks", false, 0L, "Zero estimate"),
                categories.findById(books).orElseThrow());
        assertTrue(categories.findByName("Books").isEmpty());
        assertTrue(categories.findAll(true).isEmpty());
        assertTrue(categories.update(books, "Books", true, null, null));
        assertNull(categories.findById(books).orElseThrow().avoidedGrams());
        assertNull(categories.findById(books).orElseThrow().factorSource());
        assertFalse(categories.update(999, "Missing", true, null, null));
    }

    @Test
    void categoryNamesAreCaseInsensitiveAndUnique() {
        long books = categories.insert("Books", true, null, null);
        long bikes = categories.insert("Bikes", true, null, null);
        assertThrows(UniqueConstraintException.class, () -> categories.insert("bOoKs", true, null, null));
        assertThrows(UniqueConstraintException.class, () -> categories.update(bikes, "BOOKS", true, null, null));
        assertEquals(books, categories.findByName("books").orElseThrow().id());
    }

    @Test
    void categoryFactorPairChecksApplyToInsertsAndUpdates() {
        assertThrows(DataAccessException.class, () -> categories.insert("Only grams", true, 10L, null));
        assertThrows(DataAccessException.class, () -> categories.insert("Only source", true, null, "Study"));
        assertThrows(DataAccessException.class, () -> categories.insert("Blank source", true, 10L, "   "));
        assertThrows(DataAccessException.class, () -> categories.insert("Negative grams", true, -1L, "Study"));
        long id = categories.insert("Valid", true, 0L, "Study");
        assertThrows(DataAccessException.class, () -> categories.update(id, "Valid", true, 1L, null));
        assertThrows(DataAccessException.class, () -> categories.update(id, "Valid", true, null, "Study"));
        assertThrows(DataAccessException.class, () -> categories.update(id, "Valid", true, 1L, ""));
        assertThrows(DataAccessException.class, () -> categories.update(id, "Valid", true, -1L, "Study"));
        assertEquals(new Category(id, "Valid", true, 0L, "Study"), categories.findById(id).orElseThrow());
    }

    @Test
    void transactionsCommitReuseConnectionAndRollBackRuntimeFailures() {
        long id = database.inWriteTransaction(() -> {
            Connection active = database.executeWithConnection(connection -> connection);
            long userId = users.insert("Committed", "hash", Role.STUDENT, true, NOW);
            database.inWriteTransaction(() -> {
                assertSame(active, database.executeWithConnection(connection -> connection));
                categories.insert("Committed", true, null, null);
            });
            return userId;
        });
        assertTrue(users.findById(id).isPresent());
        assertEquals(1, categories.findAll(false).size());
        RuntimeException failure = new IllegalStateException("abort");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> database.inWriteTransaction(() -> {
            users.insert("RolledBack", "hash", Role.STUDENT, true, NOW);
            database.inWriteTransaction(() -> categories.insert("RolledBack", true, null, null));
            throw failure;
        })));
        assertTrue(users.findByUsername("RolledBack").isEmpty());
        assertTrue(categories.findByName("RolledBack").isEmpty());
    }

    @Test
    void transactionsRollBackErrorsAndTranslatedSqlFailures() {
        AssertionError failure = new AssertionError("abort");
        assertSame(failure, assertThrows(AssertionError.class, () -> database.inWriteTransaction(() -> {
            categories.insert("Error", true, null, null);
            throw failure;
        })));
        assertTrue(categories.findAll(false).isEmpty());
        assertThrows(UniqueConstraintException.class, () -> database.inWriteTransaction(() -> {
            users.insert("Duplicate", "hash", Role.STUDENT, true, NOW);
            users.insert("duplicate", "hash", Role.STUDENT, true, NOW);
        }));
        assertTrue(users.findAllStudents().isEmpty());
        database.inWriteTransaction(() -> categories.insert("After rollback", true, null, null));
        assertEquals(1, categories.findAll(false).size());
    }

    @Test
    void writeTransactionObtainsWriteLockBeforeWork() {
        database.inWriteTransaction(() -> {
            try (Connection other = DriverManager.getConnection(jdbcUrl);
                 Statement statement = other.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 0");
                SQLException locked = assertThrows(SQLException.class,
                        () -> statement.executeUpdate("INSERT INTO categories (name) VALUES ('Blocked')"));
                assertEquals(5, locked.getErrorCode());
            } catch (SQLException exception) {
                throw new AssertionError(exception);
            }
        });
        assertTrue(categories.findAll(false).isEmpty());
    }

    @Test
    void transactionConnectionsAreThreadLocal() {
        var executor = Executors.newSingleThreadExecutor();
        try {
            database.inWriteTransaction(() -> {
                Connection active = database.executeWithConnection(connection -> connection);
                try {
                    executor.submit(() -> database.executeWithConnection(connection -> {
                        assertNotSame(active, connection);
                        return null;
                    })).get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
                assertSame(active, database.executeWithConnection(connection -> connection));
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void utcTimestampsUseFixedMillisecondsAndRoundTrip() {
        assertEquals("2026-11-08T08:30:00.123Z", UtcTimestamps.format(NOW));
        assertEquals(NOW, UtcTimestamps.parse(UtcTimestamps.format(NOW)));
        assertEquals("2026-11-08T08:30:00.000Z", UtcTimestamps.format(Instant.parse("2026-11-08T08:30:00Z")));
        assertEquals("2026-11-08T08:30:00.123Z",
                UtcTimestamps.format(Instant.parse("2026-11-08T08:30:00.123999999Z")));
        assertThrows(DateTimeParseException.class, () -> UtcTimestamps.parse("not-a-timestamp"));
    }

    @Test
    void sqlExceptionTranslationSupportsExtendedCodesAndMessages() {
        assertInstanceOf(UniqueConstraintException.class, SqlExceptions.translate(new SQLException("unique", "", 2067)));
        assertInstanceOf(UniqueConstraintException.class, SqlExceptions.translate(new SQLException("primary key", "", 1555)));
        assertInstanceOf(ForeignKeyException.class, SqlExceptions.translate(new SQLException("foreign key", "", 787)));
        assertInstanceOf(UniqueConstraintException.class,
                SqlExceptions.translate(new SQLException("UNIQUE constraint failed: users.username", "", 19)));
        assertInstanceOf(ForeignKeyException.class,
                SqlExceptions.translate(new SQLException("FOREIGN KEY constraint failed", "", 19)));
        assertEquals(DataAccessException.class,
                SqlExceptions.translate(new SQLException("CHECK constraint failed", "", 19)).getClass());
    }

    private int scalar(String sql) {
        return database.executeWithConnection(connection -> {
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        });
    }

    private void execute(String sql) {
        database.executeWithConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });
    }
}
