package edu.campus.circulation.repository;

import edu.campus.circulation.domain.CreditSummary;
import edu.campus.circulation.domain.Review;

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
