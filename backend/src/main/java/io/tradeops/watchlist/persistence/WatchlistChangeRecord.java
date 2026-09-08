package io.tradeops.watchlist.persistence;
import io.tradeops.watchlist.domain.WatchlistChange;
import jakarta.persistence.*;
@Entity @Table(name = "watchlist_changes")
public class WatchlistChangeRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "source_run_id", nullable = false) private Long sourceRunId;
    @Column(name = "snapshot_id", nullable = false) private Long snapshotId;
    @Column(name = "previous_snapshot_id") private Long previousSnapshotId;
    @Column(name = "change_type", nullable = false, length = 20) private String changeType;
    @Column(name = "external_id", nullable = false, length = 160) private String externalId;
    @Column(name = "entity_name", nullable = false, length = 255) private String entityName;
    @Column(name = "differences_json", nullable = false, columnDefinition = "TEXT") private String differencesJson;
    protected WatchlistChangeRecord() { }
    public WatchlistChangeRecord(Long runId, Long snapshotId, Long previousSnapshotId, WatchlistChange change, String differencesJson) { this.sourceRunId=runId;this.snapshotId=snapshotId;this.previousSnapshotId=previousSnapshotId;this.changeType=change.type().name();this.externalId=change.externalId();this.entityName=change.entityName();this.differencesJson=differencesJson; }
    public String getChangeType() { return changeType; } public String getExternalId() { return externalId; } public String getEntityName() { return entityName; } public String getDifferencesJson() { return differencesJson; }
}