package io.tradeops.watchlist.persistence;

import io.tradeops.watchlist.domain.WatchlistComparison;
import io.tradeops.watchlist.domain.WatchlistPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "watchlist_source_runs")
public class WatchlistSourceRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100) private String provider;
    @Column(name = "requested_version", nullable = false, length = 80) private String requestedVersion;
    @Enumerated(EnumType.STRING) @Column(name = "payload_format", nullable = false, length = 10) private WatchlistPayload.TransportFormat payloadFormat;
    @Column(name = "trigger_type", nullable = false, length = 20) private String triggerType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "payload_checksum", length = 64) private String payloadChecksum;
    @Column(name = "correlation_id", nullable = false, length = 64) private String correlationId;
    @Column(name = "actor_username", length = 120) private String actorUsername;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "safe_error_code", length = 80) private String safeErrorCode;
    @Column(name = "total_rows", nullable = false) private int totalRows;
    @Column(name = "added_count", nullable = false) private int addedCount;
    @Column(name = "changed_count", nullable = false) private int changedCount;
    @Column(name = "removed_count", nullable = false) private int removedCount;
    @Column(name = "unchanged_count", nullable = false) private int unchangedCount;

    protected WatchlistSourceRun() { }
    public WatchlistSourceRun(String provider, String requestedVersion, WatchlistPayload.TransportFormat payloadFormat, String correlationId, String actorUsername) {
        this.provider = provider; this.requestedVersion = requestedVersion; this.payloadFormat = payloadFormat;
        this.triggerType = "MANUAL"; this.status = Status.RUNNING; this.correlationId = correlationId;
        this.actorUsername = actorUsername; this.startedAt = Instant.now();
    }
    public void complete(String checksum, int totalRows, WatchlistComparison comparison) {
        this.payloadChecksum = checksum; this.status = Status.COMPLETED; this.completedAt = Instant.now(); this.totalRows = totalRows;
        this.addedCount = comparison.addedCount(); this.changedCount = comparison.changedCount();
        this.removedCount = comparison.removedCount(); this.unchangedCount = comparison.unchangedCount();
    }
    public void completeIdempotently(String checksum, WatchlistSnapshot snapshot) {
        this.payloadChecksum = checksum; this.status = Status.COMPLETED; this.completedAt = Instant.now(); this.totalRows = snapshot.getTotalRows();
        this.addedCount = snapshot.getAddedCount(); this.changedCount = snapshot.getChangedCount();
        this.removedCount = snapshot.getRemovedCount(); this.unchangedCount = snapshot.getUnchangedCount();
    }
    public void fail(String safeErrorCode) { this.status = Status.FAILED; this.safeErrorCode = safeErrorCode; this.completedAt = Instant.now(); }
    public Long getId() { return id; } public String getProvider() { return provider; } public String getRequestedVersion() { return requestedVersion; }
    public WatchlistPayload.TransportFormat getPayloadFormat() { return payloadFormat; } public Status getStatus() { return status; }
    public String getCorrelationId() { return correlationId; } public int getTotalRows() { return totalRows; } public int getAddedCount() { return addedCount; }
    public int getChangedCount() { return changedCount; } public int getRemovedCount() { return removedCount; } public int getUnchangedCount() { return unchangedCount; }
    public String getSafeErrorCode() { return safeErrorCode; }
    public enum Status { RUNNING, COMPLETED, FAILED }
}