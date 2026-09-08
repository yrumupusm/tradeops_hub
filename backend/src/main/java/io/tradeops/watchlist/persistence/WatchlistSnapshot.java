package io.tradeops.watchlist.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "watchlist_snapshots")
public class WatchlistSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String provider;
    @Column(name = "source_version", nullable = false, length = 80) private String sourceVersion;
    @Column(name = "payload_checksum", nullable = false, length = 64) private String payloadChecksum;
    @Column(name = "source_run_id", nullable = false, unique = true) private Long sourceRunId;
    @Column(name = "collected_at", nullable = false) private Instant collectedAt;
    @Column(name = "total_rows", nullable = false) private int totalRows;
    @Column(name = "added_count", nullable = false) private int addedCount;
    @Column(name = "changed_count", nullable = false) private int changedCount;
    @Column(name = "removed_count", nullable = false) private int removedCount;
    @Column(name = "unchanged_count", nullable = false) private int unchangedCount;
    protected WatchlistSnapshot() { }
    public WatchlistSnapshot(WatchlistSourceRun run) {
        this.provider = run.getProvider(); this.sourceVersion = run.getRequestedVersion(); this.payloadChecksum = "";
        this.sourceRunId = run.getId(); this.collectedAt = Instant.now();
    }
    public void complete(String checksum, int totalRows, int added, int changed, int removed, int unchanged) {
        this.payloadChecksum = checksum; this.totalRows = totalRows; this.addedCount = added; this.changedCount = changed;
        this.removedCount = removed; this.unchangedCount = unchanged;
    }
    public Long getId() { return id; } public String getProvider() { return provider; } public String getSourceVersion() { return sourceVersion; }
    public int getTotalRows() { return totalRows; } public int getAddedCount() { return addedCount; } public int getChangedCount() { return changedCount; }
    public int getRemovedCount() { return removedCount; } public int getUnchangedCount() { return unchangedCount; }
}