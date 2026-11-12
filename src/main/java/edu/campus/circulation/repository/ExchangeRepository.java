package edu.campus.circulation.repository;

import edu.campus.circulation.domain.CategoryStatRow;
import edu.campus.circulation.domain.Exchange;
import edu.campus.circulation.domain.ExchangeMessage;
import edu.campus.circulation.domain.ExchangeStatus;
import edu.campus.circulation.domain.StatsFilter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExchangeRepository {
    long insertWithFirstMessage(long itemId, long requesterId, String firstMessageBody, Instant now);
    long insertMessage(long exchangeId, long senderId, String body, Instant sentAt);
    Optional<Exchange> findById(long id);
    List<ExchangeMessage> findMessages(long exchangeId);
    List<Exchange> findByParticipant(long userId);
    List<Exchange> findByItem(long itemId);
    boolean hasAcceptedExchangeForUser(long userId);
    boolean transitionStatus(long exchangeId, ExchangeStatus expectedOld, ExchangeStatus newStatus, Instant completedAt);
    int rejectOtherPendingByItem(long itemId, long excludedExchangeId);
    int cancelPendingByUser(long userId);
    int cancelPendingByItem(long itemId);
    List<CategoryStatRow> summarizeCompletedByCategory(StatsFilter filter);
}
