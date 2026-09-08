package io.tradeops.watchlist.persistence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface WatchlistChangeRecordRepository extends JpaRepository<WatchlistChangeRecord, Long> {
    @Query("select c from WatchlistChangeRecord c where c.snapshotId = :snapshotId and (:type is null or c.changeType = :type) and (:search is null or lower(c.entityName) like lower(concat('%', :search, '%')))")
    Page<WatchlistChangeRecord> search(@Param("snapshotId") Long snapshotId, @Param("type") String type, @Param("search") String search, Pageable pageable);
}