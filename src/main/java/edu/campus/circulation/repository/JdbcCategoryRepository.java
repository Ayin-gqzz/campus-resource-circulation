package edu.campus.circulation.repository;

import edu.campus.circulation.domain.Category;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcCategoryRepository implements CategoryRepository {
    private final DatabaseManager database;

    public JdbcCategoryRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public long insert(String name, boolean enabled, Long avoidedGrams, String factorSource) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO categories (name, enabled, avoided_grams, factor_source)
                    VALUES (?, ?, ?, ?) RETURNING id
                    """)) {
                bindFields(statement, name, enabled, avoidedGrams, factorSource);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getLong(1);
                }
            }
        });
    }

    @Override
    public boolean update(long id, String name, boolean enabled, Long avoidedGrams, String factorSource) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE categories SET name = ?, enabled = ?, avoided_grams = ?, factor_source = ? WHERE id = ?
                    """)) {
                bindFields(statement, name, enabled, avoidedGrams, factorSource);
                statement.setLong(5, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    @Override
    public Optional<Category> findById(long id) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM categories WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(mapCategory(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<Category> findByName(String name) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM categories WHERE name = ?")) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(mapCategory(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Category> findAll(boolean onlyEnabled) {
        return database.executeWithConnection(connection -> {
            String sql = "SELECT * FROM categories" + (onlyEnabled ? " WHERE enabled = 1" : "") + " ORDER BY id";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {
                List<Category> categories = new ArrayList<>();
                while (result.next()) {
                    categories.add(mapCategory(result));
                }
                return categories;
            }
        });
    }

    private static void bindFields(PreparedStatement statement, String name, boolean enabled,
                                   Long avoidedGrams, String factorSource) throws SQLException {
        statement.setString(1, name);
        statement.setBoolean(2, enabled);
        if (avoidedGrams == null) {
            statement.setNull(3, Types.BIGINT);
        } else {
            statement.setLong(3, avoidedGrams);
        }
        statement.setString(4, factorSource);
    }

    private static Category mapCategory(ResultSet result) throws SQLException {
        long grams = result.getLong("avoided_grams");
        Long avoidedGrams = result.wasNull() ? null : grams;
        return new Category(result.getLong("id"), result.getString("name"), result.getBoolean("enabled"),
                avoidedGrams, result.getString("factor_source"));
    }
}
