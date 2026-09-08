package io.tradeops.watchlist.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tradeops.watchlist.domain.WatchlistChange;
import io.tradeops.watchlist.domain.WatchlistComparison;
import io.tradeops.watchlist.domain.WatchlistPayload;
import io.tradeops.watchlist.domain.WatchlistPayloadException;
import io.tradeops.watchlist.domain.WatchlistRecord;
import io.tradeops.watchlist.fixture.FictionalWatchlistFixtureGenerator;
import io.tradeops.watchlist.persistence.*;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistUpdateService {
    private final FictionalWatchlistSource source;
    private final WatchlistComparator comparator;
    private final WatchlistSourceRunRepository runs;
    private final WatchlistSnapshotRepository snapshots;
    private final WatchlistSnapshotRecordRepository records;
    private final WatchlistChangeRecordRepository changes;
    private final ObjectMapper objectMapper;
    public WatchlistUpdateService(FictionalWatchlistSource source, WatchlistComparator comparator, WatchlistSourceRunRepository runs,
            WatchlistSnapshotRepository snapshots, WatchlistSnapshotRecordRepository records, WatchlistChangeRecordRepository changes, ObjectMapper objectMapper) {
        this.source = source; this.comparator = comparator; this.runs = runs; this.snapshots = snapshots; this.records = records; this.changes = changes; this.objectMapper = objectMapper;
    }
    @Transactional
    public RunSummary run(String version, WatchlistPayload.TransportFormat format, String actor, String correlationId) {
        WatchlistSourceRun run = runs.save(new WatchlistSourceRun(FictionalWatchlistFixtureGenerator.PROVIDER, version, format, correlationId, actor));
        try {
            FictionalWatchlistSource.LoadedPayload loaded = source.load(version, format);
            Optional<WatchlistSnapshot> same = snapshots.findByProviderAndSourceVersionAndPayloadChecksum(loaded.payload().provider(), loaded.payload().version(), loaded.checksum());
            if (same.isPresent()) { run.completeIdempotently(loaded.checksum(), same.get()); return RunSummary.from(runs.save(run), same.get().getId(), true); }
            Optional<WatchlistSnapshot> previous = snapshots.findFirstByProviderOrderByIdDesc(loaded.payload().provider());
            WatchlistComparison comparison = comparator.compare(previous.map(this::toPayload), loaded.payload());
            run.complete(loaded.checksum(), loaded.payload().records().size(), comparison); runs.saveAndFlush(run);
            WatchlistSnapshot snapshot = new WatchlistSnapshot(run);
            snapshot.complete(loaded.checksum(), loaded.payload().records().size(), comparison.addedCount(), comparison.changedCount(), comparison.removedCount(), comparison.unchangedCount());
            snapshot = snapshots.saveAndFlush(snapshot);
            Long snapshotId = snapshot.getId();
            records.saveAll(loaded.payload().records().stream().map(record -> new WatchlistSnapshotRecord(snapshotId, record)).toList());
            Long previousId = previous.map(WatchlistSnapshot::getId).orElse(null);
            changes.saveAll(comparison.changes().stream().map(change -> new WatchlistChangeRecord(run.getId(), snapshotId, previousId, change, differencesJson(change))).toList());
            return RunSummary.from(run, snapshotId, false);
        } catch (WatchlistPayloadException exception) { run.fail(exception.code()); return RunSummary.from(runs.save(run), null, false); }
    }
    private WatchlistPayload toPayload(WatchlistSnapshot snapshot) {
        List<WatchlistRecord> list = records.findBySnapshotId(snapshot.getId()).stream().map(record -> record.toDomain(snapshot.getProvider())).toList();
        return new WatchlistPayload(snapshot.getProvider(), snapshot.getSourceVersion(), WatchlistPayload.TransportFormat.XML, list);
    }
    private String differencesJson(WatchlistChange change) {
        try { return objectMapper.writeValueAsString(change.differences()); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Watchlist differences cannot be serialized", exception); }
    }
    public record RunSummary(Long runId, Long snapshotId, String status, boolean idempotent, int totalRows, int addedCount, int changedCount, int removedCount, int unchangedCount, String safeErrorCode, String correlationId) {
        static RunSummary from(WatchlistSourceRun run, Long snapshotId, boolean idempotent) { return new RunSummary(run.getId(), snapshotId, run.getStatus().name(), idempotent, run.getTotalRows(), run.getAddedCount(), run.getChangedCount(), run.getRemovedCount(), run.getUnchangedCount(), run.getSafeErrorCode(), run.getCorrelationId()); }
    }
}