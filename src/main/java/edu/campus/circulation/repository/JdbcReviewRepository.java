package edu.campus.circulation.repository;

import edu.campus.circulation.domain.CategoryStatRow;
import edu.campus.circulation.domain.CreditSummary;
import edu.campus.circulation.domain.Review;
import edu.campus.circulation.domain.StatsFilter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcReviewRepository implements ReviewRepository {
    private static final String REVIEW_COLUMNS = """
            SELECT exchange_id, reviewer_id, reviewee_id, stars, comment, created_at FROM reviews""";

    private final DatabaseManager database;

    public JdbcReviewRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public void insert(Review review) {
        database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO reviews (exchange_id, reviewer_id, reviewee_id, stars, comment, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setLong(1, review.exchangeId());
                statement.setLong(2, review.reviewerId());
                statement.setLong(3, review.revieweeId());
                statement.setInt(4, review.stars());
                statement.setString(5, review.comment());
                statement.setString(6, UtcTimestamps.format(review.createdAt()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<Review> findByExchange(long exchangeId) {
        return database.executeWithConnection(connection -> readReviews(connection, REVIEW_COLUMNS
                + " WHERE exchange_id = ? ORDER BY created_at ASC, reviewer_id ASC", exchangeId));
    }

    @Override
    public List<Review> findByReviewee(long revieweeId) {
        return database.executeWithConnection(connection -> readReviews(connection, REVIEW_COLUMNS
                + " WHERE reviewee_id = ? ORDER BY created_at DESC, exchange_id DESC", revieweeId));
    }

    @Override
    public CreditSummary getCreditSummary(long userId) {
        return database.executeWithConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*), AVG(stars) FROM reviews WHERE reviewee_id = ?")) {
                statement.setLong(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    int count = result.getInt(1);
                    Double average = count == 0 ? null : Math.round(result.getDouble(2) * 10.0) / 10.0;
                    return new CreditSummary(userId, count, average);
                }
            }
        });
    }

    @Override
    public List<CategoryStatRow> summarizeCompletedByCategory(StatsFilter filter) {
        return database.executeWithConnection(connection -> {
            StringBuilder sql = new StringBuilder("""
                    SELECT c.id, c.name, COUNT(*) AS completed_count, c.avoided_grams, c.factor_source
                    FROM exchanges e JOIN items i ON i.id = e.item_id JOIN categories c ON c.id = i.category_id
                    WHERE e.status = 'COMPLETED'""");
            List<Object> parameters = new ArrayList<>();
            if (filter.fromInclusive() != null) {
                sql.append(" AND e.completed_at >= ?");
                parameters.add(UtcTimestamps.format(filter.fromInclusive()));
            }
            if (filter.toExclusive() != null) {
                sql.append(" AND e.completed_at < ?");
                parameters.add(UtcTimestamps.format(filter.toExclusive()));
            }
            if (filter.categoryId() != null) {
                sql.append(" AND i.category_id = ?");
                parameters.add(filter.categoryId());
            }
            if (filter.participantUserId() != null) {
                sql.append(" AND (e.requester_id = ? OR i.owner_id = ?)");
                parameters.add(filter.participantUserId());
                parameters.add(filter.participantUserId());
            }
            sql.append(" GROUP BY c.id, c.name, c.avoided_grams, c.factor_source ORDER BY c.id ASC");
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                for (int index = 0; index < parameters.size(); index++) {
                    Object value = parameters.get(index);
                    if (value instanceof Long longValue) {
                        statement.setLong(index + 1, longValue);
                    } else {
                        statement.setString(index + 1, (String) value);
                    }
                }
                List<CategoryStatRow> rows = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        long grams = result.getLong("avoided_grams");
                        Long avoidedGrams = result.wasNull() ? null : grams;
                        rows.add(new CategoryStatRow(result.getLong("id"), result.getString("name"),
                                result.getLong("completed_count"), avoidedGrams, result.getString("factor_source")));
                    }
                }
                return List.copyOf(rows);
            }
        });
    }

    private static List<Review> readReviews(Connection connection, String sql, long parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parameter);
            List<Review> reviews = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    reviews.add(new Review(result.getLong("exchange_id"), result.getLong("reviewer_id"),
                            result.getLong("reviewee_id"), result.getInt("stars"), result.getString("comment"),
                            UtcTimestamps.parse(result.getString("created_at"))));
                }
            }
            return List.copyOf(reviews);
        }
    }
}
