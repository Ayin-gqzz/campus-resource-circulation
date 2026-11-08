package edu.campus.circulation.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import org.sqlite.SQLiteConfig;

public final class DemoDatabase {
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private final String url;

    public DemoDatabase(Path file) throws IOException, SQLException {
        Path path = file.toAbsolutePath();
        Files.createDirectories(path.getParent());
        url = "jdbc:sqlite:" + path;
        write(connection -> {
            int version = query(connection, "PRAGMA user_version", row -> row.getInt(1)).get(0);
            if (version == 0) {
                initialize(connection);
            } else if (version != 1) {
                throw new SQLException("未知的演示数据库版本：" + version);
            }
            return null;
        });
    }

    private Connection connect() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(3000);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return DriverManager.getConnection(url, config.toProperties());
    }

    public <T> T read(Work<T> work) throws SQLException {
        try (Connection connection = connect()) {
            return work.run(connection);
        }
    }

    public <T> T write(Work<T> work) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    private static void initialize(Connection connection) throws SQLException {
        try (var script = DemoDatabase.class.getResourceAsStream("/database/001_initial.sql")) {
            if (script == null) {
                throw new SQLException("缺少数据库初始化脚本。");
            }
            for (String part : new String(script.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                String sql = part.strip();
                // The caller owns the transaction and enables foreign keys on the connection.
                if (!sql.isEmpty() && !List.of("PRAGMA foreign_keys = ON", "BEGIN IMMEDIATE", "COMMIT").contains(sql)) {
                    update(connection, sql);
                }
            }
        } catch (IOException failure) {
            throw new SQLException("无法读取数据库初始化脚本。", failure);
        }

        String now = now();
        update(connection, """
                INSERT INTO users (id, username, password_hash, created_at)
                VALUES (1, 'student_lin', 'DEMO_PROFILE', ?), (2, 'student_chen', 'DEMO_PROFILE', ?)
                """, now, now);
        update(connection, "INSERT INTO categories (id, name) VALUES (1, '教材文具'), (2, '数码配件'), (3, '生活用品')");
        update(connection, """
                INSERT INTO items (owner_id, category_id, title, description, condition,
                                   trade_type, price_cents, swap_note, created_at)
                VALUES (1, 1, '高等数学教材', '封面完好，内页有少量课堂笔记。', 'GOOD', 'SALE', 1500, NULL, ?),
                       (1, 2, 'USB 台灯', '适合宿舍书桌，亮度可调。', 'FAIR', 'SWAP', NULL, '交换一个文件收纳盒', ?),
                       (2, 3, '不锈钢保温杯', '未使用，容量 500 mL。', 'NEW', 'SALE', 2000, NULL, ?)
                """, now, now, now);
    }

    public static String now() {
        return TIME.format(Instant.now());
    }

    public static int update(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, values)) {
            return statement.executeUpdate();
        }
    }

    public static <T> List<T> query(Connection connection, String sql, Mapper<T> mapper, Object... values)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, values);
                ResultSet rows = statement.executeQuery()) {
            List<T> results = new ArrayList<>();
            while (rows.next()) {
                results.add(mapper.map(rows));
            }
            return List.copyOf(results);
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object[] values) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        } catch (SQLException failure) {
            try {
                statement.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface Mapper<T> {
        T map(ResultSet row) throws SQLException;
    }
}
