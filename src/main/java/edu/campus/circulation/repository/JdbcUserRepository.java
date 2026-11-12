package edu.campus.circulation.repository;

import edu.campus.circulation.domain.Role;
import edu.campus.circulation.domain.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcUserRepository implements UserRepository {
    private final DatabaseManager database;

    public JdbcUserRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public long insert(String username, String passwordHash, Role role, boolean enabled, Instant createdAt) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO users (username, password_hash, role, enabled, created_at)
                    VALUES (?, ?, ?, ?, ?) RETURNING id
                    """)) {
                statement.setString(1, username);
                statement.setString(2, passwordHash);
                statement.setString(3, role.name());
                statement.setBoolean(4, enabled);
                statement.setString(5, UtcTimestamps.format(createdAt));
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
    }

    @Override
    public Optional<User> findById(long id) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(mapUser(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE username = ?")) {
                statement.setString(1, username);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(mapUser(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<User> findAllStudents() {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM users WHERE role = 'STUDENT' ORDER BY id");
                 ResultSet result = statement.executeQuery()) {
                List<User> users = new ArrayList<>();
                while (result.next()) {
                    users.add(mapUser(result));
                }
                return users;
            }
        });
    }

    @Override
    public boolean updateEnabled(long userId, boolean enabled) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET enabled = ? WHERE id = ?")) {
                statement.setBoolean(1, enabled);
                statement.setLong(2, userId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private static User mapUser(ResultSet result) throws SQLException {
        return new User(result.getLong("id"), result.getString("username"), result.getString("password_hash"),
                Role.valueOf(result.getString("role")), result.getBoolean("enabled"),
                UtcTimestamps.parse(result.getString("created_at")));
    }
}
