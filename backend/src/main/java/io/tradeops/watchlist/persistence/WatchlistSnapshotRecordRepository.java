package io.tradeops.watchlist.persistence;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WatchlistSnapshotRecordRepository extends JpaRepository<WatchlistSnapshotRecord, Long>,
        JpaSpecificationExecutor<WatchlistSnapshotRecord> {
    List<WatchlistSnapshotRecord> findBySnapshotId(Long snapshotId);

    default Page<WatchlistSnapshotRecord> search(Long snapshotId, String search, String country,
                                                 String status, Pageable pageable) {
        return findAll((entity, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(entity.get("snapshotId"), snapshotId));
            if (search != null) predicates.add(builder.like(builder.lower(entity.get("entityName")),
                    "%" + search.toLowerCase(Locale.ROOT) + "%"));
            if (country != null) predicates.add(builder.equal(entity.get("countryCode"), country));
            if (status != null) predicates.add(builder.equal(entity.get("status"), status));
            return builder.and(predicates.toArray(Predicate[]::new));
        }, pageable);
    }
}
