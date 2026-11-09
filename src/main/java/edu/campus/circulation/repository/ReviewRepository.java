package edu.campus.circulation.repository;

import edu.campus.circulation.domain.CategoryStatRow;
import edu.campus.circulation.domain.CreditSummary;
import edu.campus.circulation.domain.Review;
import edu.campus.circulation.domain.StatsFilter;

import java.util.List;

public interface ReviewRepository {
    void insert(Review review);
    List<Review> findByExchange(long exchangeId);
    List<Review> findByReviewee(long revieweeId);
    CreditSummary getCreditSummary(long userId);
    List<CategoryStatRow> summarizeCompletedByCategory(StatsFilter filter);
}
