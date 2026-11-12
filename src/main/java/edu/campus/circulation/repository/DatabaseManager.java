package edu.campus.circulation.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

public final class DatabaseManager {
    private final String jdbcUrl;
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    public DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    public void initialize() {
        inWriteTransaction(() -> executeWithConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
                    int version = result.next() ? result.getInt(1) : 0;
                    if (version == 1) {
                        return null;
                    }
                    if (version != 0) {
                        throw new DataAccessException("Unsupported database version: " + version);
                    }
                }
                for (String entry : loadInitialSchema().split(";")) {
                    String sql = entry.strip();
                    // The same script also supports standalone execution in the SQLite CLI.
                    if (!sql.isEmpty() && !sql.equals("PRAGMA foreign_keys = ON")
                            && !sql.equals("BEGIN IMMEDIATE") && !sql.equals("COMMIT")) {
                        statement.execute(sql);
                    }
                }
            }
            return null;
        }));
    }

    private String loadInitialSchema() {
        try (InputStream resource = DatabaseManager.class.getResourceAsStream("/database/001_initial.sql")) {
            if (resource != null) {
                return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            }
            return Files.readString(Path.of("database", "001_initial.sql"), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new DataAccessException("Cannot load database/001_initial.sql", exception);
        }
    }

    /** Reuses an active transaction connection, or opens and closes a new connection. */
    public <T> T executeWithConnection(SqlFunction<T> work) {
        Connection active = transactionConnection.get();
        if (active != null) {
            try {
                return work.apply(active);
            } catch (SQLException exception) {
                throw SqlExceptions.translate(exception);
            }
        }
        try (Connection connection = openConnection()) {
            return work.apply(connection);
        } catch (SQLException exception) {
            throw SqlExceptions.translate(exception);
        }
    }

    public <T> T inWriteTransaction(Supplier<T> work) {
        if (transactionConnection.get() != null) {
            return work.get();
        }
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
            transactionConnection.set(connection);
            try {
                T result = work.get();
                statement.execute("COMMIT");
                return result;
            } catch (RuntimeException | Error failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                transactionConnection.remove();
            }
        } catch (SQLException exception) {
            throw SqlExceptions.translate(exception);
        }
    }

    public void inWriteTransaction(Runnable work) {
        inWriteTransaction(() -> {
            work.run();
            return null;
        });
    }

    private static void rollback(Connection connection, Throwable failure) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
