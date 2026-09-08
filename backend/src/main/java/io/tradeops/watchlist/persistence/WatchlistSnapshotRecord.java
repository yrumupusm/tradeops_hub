package io.tradeops.watchlist.persistence;

import io.tradeops.watchlist.domain.WatchlistRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

@Entity
@Table(name = "watchlist_snapshot_records")
public class WatchlistSnapshotRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "snapshot_id", nullable = false) private Long snapshotId;
    @Column(name = "external_id", nullable = false, length = 160) private String externalId;
    @Column(name = "entity_name", nullable = false, length = 255) private String entityName;
    @Column(nullable = false, columnDefinition = "TEXT") private String aliases;
    @Column(name = "country_code", length = 2) private String countryCode;
    @Column(name = "listing_reason", nullable = false, length = 40) private String listingReason;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "data_origin", nullable = false, length = 20) private String dataOrigin;
    @Column(name = "canonical_hash", nullable = false, length = 64) private String canonicalHash;
    protected WatchlistSnapshotRecord() { }
    public WatchlistSnapshotRecord(Long snapshotId, WatchlistRecord record) {
        this.snapshotId = snapshotId; this.externalId = record.externalId(); this.entityName = record.entityName();
        this.aliases = String.join("|", record.aliases()); this.countryCode = record.countryCode(); this.listingReason = record.listingReason();
        this.status = record.status(); this.dataOrigin = record.dataOrigin(); this.canonicalHash = record.canonicalHash();
    }
    public String getExternalId() { return externalId; } public String getEntityName() { return entityName; } public String getAliases() { return aliases; }
    public String getCountryCode() { return countryCode; } public String getListingReason() { return listingReason; } public String getStatus() { return status; }
    public WatchlistRecord toDomain(String provider) {
        return new WatchlistRecord(provider, externalId, entityName, aliases.isBlank() ? List.of() : List.of(aliases.split("\\|")), countryCode, listingReason, status, dataOrigin);
    }
}