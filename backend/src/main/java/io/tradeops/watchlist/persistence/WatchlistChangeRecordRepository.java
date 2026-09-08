package io.tradeops.watchlist.persistence;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WatchlistChangeRecordRepository extends JpaRepository<WatchlistChangeRecord, Long>,
        JpaSpecificationExecutor<WatchlistChangeRecord> {
    default Page<WatchlistChangeRecord> search(Long snapshotId, String type, String search, Pageable pageable) {
        return findAll((entity, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(entity.get("snapshotId"), snapshotId));
            if (type != null) predicates.add(builder.equal(entity.get("changeType"), type));
            if (search != null) predicates.add(builder.like(builder.lower(entity.get("entityName")),
                    "%" + search.toLowerCase(Locale.ROOT) + "%"));
            return builder.and(predicates.toArray(Predicate[]::new));
        }, pageable);
    }
}
