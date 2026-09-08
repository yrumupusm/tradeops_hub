package io.tradeops.watchlist.service;

import io.tradeops.watchlist.fixture.FictionalWatchlistFixtureGenerator;
import io.tradeops.watchlist.persistence.*;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class WatchlistReadService {
    private final WatchlistSnapshotRepository snapshots;
    private final WatchlistSnapshotRecordRepository records;
    private final WatchlistChangeRecordRepository changes;
    private final WatchlistSourceRunRepository runs;
    public WatchlistReadService(WatchlistSnapshotRepository snapshots, WatchlistSnapshotRecordRepository records, WatchlistChangeRecordRepository changes, WatchlistSourceRunRepository runs) { this.snapshots=snapshots; this.records=records; this.changes=changes; this.runs=runs; }
    public PageResponse<EntityView> entities(String provider, String search, String country, String status, int page, int size) {
        if (provider != null && !provider.isBlank() && !FictionalWatchlistFixtureGenerator.PROVIDER.equals(provider)) return PageResponse.empty(page,size);
        Optional<WatchlistSnapshot> snapshot = snapshots.findFirstByProviderOrderByIdDesc(FictionalWatchlistFixtureGenerator.PROVIDER);
        if (snapshot.isEmpty()) return PageResponse.empty(page,size);
        Page<WatchlistSnapshotRecord> found = records.search(snapshot.get().getId(), clean(search), upper(country), upper(status), pageable(page,size));
        return page(found.map(r -> new EntityView(r.getExternalId(),r.getEntityName(),r.getAliases(),r.getCountryCode(),r.getListingReason(),r.getStatus(),snapshot.get().getSourceVersion())));
    }
    public PageResponse<ChangeView> changes(String type, String search, int page, int size) {
        Optional<WatchlistSnapshot> snapshot = snapshots.findFirstByProviderOrderByIdDesc(FictionalWatchlistFixtureGenerator.PROVIDER);
        if (snapshot.isEmpty()) return PageResponse.empty(page,size);
        Page<WatchlistChangeRecord> found = changes.search(snapshot.get().getId(), upper(type), clean(search), pageable(page,size));
        return page(found.map(c -> new ChangeView(c.getChangeType(),c.getExternalId(),c.getEntityName(),c.getDifferencesJson(),snapshot.get().getSourceVersion())));
    }
    public PageResponse<RunView> runs(int page, int size) { return page(runs.findAll(pageable(page,size)).map(r -> new RunView(r.getId(),r.getRequestedVersion(),r.getPayloadFormat().name(),r.getStatus().name(),r.getTotalRows(),r.getAddedCount(),r.getChangedCount(),r.getRemovedCount(),r.getUnchangedCount(),r.getSafeErrorCode(),r.getCorrelationId()))); }
    private PageRequest pageable(int page,int size) { return PageRequest.of(page,size, Sort.by(Sort.Direction.DESC,"id")); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String upper(String value) { String clean=clean(value); return clean == null ? null : clean.toUpperCase(Locale.ROOT); }
    private <T> PageResponse<T> page(Page<T> source) { return new PageResponse<>(source.getContent(),source.getNumber(),source.getSize(),source.getTotalElements(),source.getTotalPages()); }
    public record PageResponse<T>(List<T> items,int page,int size,long totalElements,int totalPages) { static <T> PageResponse<T> empty(int page,int size) { return new PageResponse<>(List.of(),page,size,0,0); } }
    public record EntityView(String externalId,String entityName,String aliases,String countryCode,String listingReason,String status,String sourceVersion) { }
    public record ChangeView(String type,String externalId,String entityName,String differencesJson,String sourceVersion) { }
    public record RunView(Long runId,String requestedVersion,String format,String status,int totalRows,int addedCount,int changedCount,int removedCount,int unchangedCount,String safeErrorCode,String correlationId) { }
}