package io.tradeops.screening;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreeningReviewService {
    private final ScreeningReviewRepository repository;

    public ScreeningReviewService(ScreeningReviewRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String transactionId, String watchlistExternalId, BigDecimal score,
                       String disposition, String actor, String correlationId) {
        long reviewId = repository.insert(transactionId, watchlistExternalId, score, disposition, actor, correlationId);
        repository.recordAudit(reviewId, actor, correlationId);
    }
}
