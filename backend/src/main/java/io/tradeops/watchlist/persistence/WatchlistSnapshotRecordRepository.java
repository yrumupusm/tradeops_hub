package io.tradeops.watchlist.persistence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface WatchlistSnapshotRecordRepository extends JpaRepository<WatchlistSnapshotRecord, Long> {
    java.util.List<WatchlistSnapshotRecord> findBySnapshotId(Long snapshotId);
    @Query("select r from WatchlistSnapshotRecord r where r.snapshotId = :snapshotId and (:search is null or lower(r.entityName) like lower(concat('%', :search, '%'))) and (:country is null or r.countryCode = :country) and (:status is null or r.status = :status)")
    Page<WatchlistSnapshotRecord> search(@Param("snapshotId") Long snapshotId, @Param("search") String search, @Param("country") String country, @Param("status") String status, Pageable pageable);
}