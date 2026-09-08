package io.tradeops.watchlist.persistence;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WatchlistSnapshotRepository extends JpaRepository<WatchlistSnapshot, Long> {
    Optional<WatchlistSnapshot> findFirstByProviderOrderByIdDesc(String provider);
    Optional<WatchlistSnapshot> findByProviderAndSourceVersionAndPayloadChecksum(String provider, String sourceVersion, String payloadChecksum);
}