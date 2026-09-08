package io.tradeops.screening;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ScreeningReviewRepository {
    private final JdbcTemplate jdbc;

    public ScreeningReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String transactionId, String watchlistExternalId, BigDecimal score,
                       String disposition, String actor, String correlationId) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into screening_reviews
                    (transaction_id, watchlist_external_id, match_score, disposition, actor_username, correlation_id)
                    values (?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, transactionId);
            statement.setString(2, watchlistExternalId);
            statement.setBigDecimal(3, score);
            statement.setString(4, disposition);
            statement.setString(5, actor);
            statement.setString(6, correlationId);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    public void recordAudit(long reviewId, String actor, String correlationId) {
        jdbc.update("""
                insert into audit_events(correlation_id, actor_username, event_type, entity_type, entity_id, detail)
                values (?, ?, 'SCREENING_REVIEW_RECORDED', 'SCREENING_REVIEW', ?, null)
                """, correlationId, actor, Long.toString(reviewId));
    }
}
