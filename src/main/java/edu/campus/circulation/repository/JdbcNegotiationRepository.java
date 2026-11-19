package edu.campus.circulation.repository;

import edu.campus.circulation.domain.MeetupLocation;
import edu.campus.circulation.domain.MeetupLocation.Campus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcNegotiationRepository {
    private final DatabaseManager database;

    public JdbcNegotiationRepository(DatabaseManager database) {
        this.database = database;
    }

    public Terms terms(long exchangeId) {
        return database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement("SELECT * FROM exchange_terms WHERE exchange_id = ?")) {
                statement.setLong(1, exchangeId);
                try (var row = statement.executeQuery()) {
                    if (!row.next()) {
                        throw new DataAccessException("Missing exchange terms: " + exchangeId);
                    }
                    return new Terms(nullablePrice(row), location(row), row.getInt("version"), meetupAt(row));
                }
            }
        });
    }

    public List<Proposal> proposals(long exchangeId) {
        return database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT * FROM exchange_proposals WHERE exchange_id = ? ORDER BY id")) {
                statement.setLong(1, exchangeId);
                List<Proposal> values = new ArrayList<>();
                try (var row = statement.executeQuery()) {
                    while (row.next()) {
                        String resolved = row.getString("resolved_at");
                        values.add(new Proposal(row.getLong("id"), exchangeId, row.getLong("proposer_id"),
                                row.getInt("base_version"), nullablePrice(row), location(row), row.getString("status"),
                                UtcTimestamps.parse(row.getString("created_at")),
                                resolved == null ? null : UtcTimestamps.parse(resolved), meetupAt(row)));
                    }
                }
                return List.copyOf(values);
            }
        });
    }

    public Optional<Proposal> pending(long exchangeId) {
        return proposals(exchangeId).stream().filter(proposal -> proposal.status().equals("PENDING")).findFirst();
    }

    public void propose(long exchangeId, long proposer, Terms next, Instant now) {
        database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO exchange_proposals
                    (exchange_id, proposer_id, base_version, price_cents, campus, location, created_at, meetup_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setLong(1, exchangeId);
                statement.setLong(2, proposer);
                statement.setInt(3, next.version());
                statement.setObject(4, next.priceCents());
                statement.setString(5, next.location().campus().name());
                statement.setString(6, next.location().place());
                statement.setString(7, UtcTimestamps.format(now));
                statement.setString(8, next.meetupAt() == null ? null : UtcTimestamps.format(next.meetupAt()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    public boolean apply(Proposal proposal) {
        return database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE exchange_terms SET price_cents = ?, campus = ?, location = ?, meetup_at = ?, version = version + 1
                    WHERE exchange_id = ? AND version = ?
                    """)) {
                statement.setObject(1, proposal.priceCents());
                statement.setString(2, proposal.location().campus().name());
                statement.setString(3, proposal.location().place());
                statement.setString(4, proposal.meetupAt() == null ? null : UtcTimestamps.format(proposal.meetupAt()));
                statement.setLong(5, proposal.exchangeId());
                statement.setInt(6, proposal.baseVersion());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public boolean resolve(long id, long actor, String status, Instant now) {
        return database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE exchange_proposals SET status = ?, resolved_at = ?, responder_id = ?
                    WHERE id = ? AND status = 'PENDING'
                    """)) {
                statement.setString(1, status);
                statement.setString(2, UtcTimestamps.format(now));
                statement.setLong(3, actor);
                statement.setLong(4, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public void expireClosed() {
        database.executeWithConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    UPDATE exchange_proposals SET status = 'EXPIRED', resolved_at = ?
                    WHERE status = 'PENDING' AND exchange_id IN
                        (SELECT id FROM exchanges WHERE status NOT IN ('PENDING', 'ACCEPTED'))
                    """)) {
                statement.setString(1, UtcTimestamps.format(Instant.now()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static Long nullablePrice(ResultSet row) throws SQLException {
        long value = row.getLong("price_cents");
        return row.wasNull() ? null : value;
    }

    private static MeetupLocation location(ResultSet row) throws SQLException {
        String campus = row.getString("campus");
        return campus == null ? null : new MeetupLocation(Campus.valueOf(campus), row.getString("location"));
    }

    private static Instant meetupAt(ResultSet row) throws SQLException {
        String value = row.getString("meetup_at");
        return value == null ? null : UtcTimestamps.parse(value);
    }

    public record Terms(Long priceCents, MeetupLocation location, int version, Instant meetupAt) {}
    public record Proposal(long id, long exchangeId, long proposerId, int baseVersion, Long priceCents,
            MeetupLocation location, String status, Instant createdAt, Instant resolvedAt, Instant meetupAt) {}
}
