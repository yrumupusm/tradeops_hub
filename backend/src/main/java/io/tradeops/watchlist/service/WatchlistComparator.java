package io.tradeops.watchlist.service;

import io.tradeops.watchlist.domain.WatchlistChange;
import io.tradeops.watchlist.domain.WatchlistComparison;
import io.tradeops.watchlist.domain.WatchlistPayload;
import io.tradeops.watchlist.domain.WatchlistPayloadException;
import io.tradeops.watchlist.domain.WatchlistRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public final class WatchlistComparator {

    public WatchlistComparison compare(Optional<WatchlistPayload> previous, WatchlistPayload candidate) {
        Map<String, WatchlistRecord> previousByKey = previous.map(this::byKey).orElseGet(Map::of);
        if (previous.isPresent() && !previous.get().provider().equals(candidate.provider())) {
            throw new WatchlistPayloadException("PROVIDER_MISMATCH", "Snapshots from different providers cannot be compared.");
        }
        Map<String, WatchlistRecord> candidateByKey = byKey(candidate);
        List<WatchlistChange> changes = new ArrayList<>();
        int added = 0;
        int changed = 0;
        int unchanged = 0;

        for (Map.Entry<String, WatchlistRecord> entry : new TreeMap<>(candidateByKey).entrySet()) {
            WatchlistRecord before = previousByKey.get(entry.getKey());
            WatchlistRecord after = entry.getValue();
            if (before == null) {
                changes.add(new WatchlistChange(WatchlistChange.ChangeType.ADDED, after.externalId(), after.entityName(), Map.of()));
                added++;
            } else if (before.canonicalHash().equals(after.canonicalHash())) {
                unchanged++;
            } else {
                changes.add(new WatchlistChange(WatchlistChange.ChangeType.CHANGED, after.externalId(), after.entityName(), differences(before, after)));
                changed++;
            }
        }

        int removed = 0;
        for (Map.Entry<String, WatchlistRecord> entry : new TreeMap<>(previousByKey).entrySet()) {
            if (!candidateByKey.containsKey(entry.getKey())) {
                WatchlistRecord before = entry.getValue();
                changes.add(new WatchlistChange(WatchlistChange.ChangeType.REMOVED, before.externalId(), before.entityName(), Map.of()));
                removed++;
            }
        }
        return new WatchlistComparison(added, changed, removed, unchanged, List.copyOf(changes));
    }

    private Map<String, WatchlistRecord> byKey(WatchlistPayload payload) {
        Map<String, WatchlistRecord> records = new LinkedHashMap<>();
        for (WatchlistRecord record : payload.records()) {
            records.put(record.stableKey(), record);
        }
        return records;
    }

    private Map<String, WatchlistChange.FieldDifference> differences(WatchlistRecord before, WatchlistRecord after) {
        Map<String, WatchlistChange.FieldDifference> values = new LinkedHashMap<>();
        addDifference(values, "entityName", before.entityName(), after.entityName());
        addDifference(values, "aliases", String.join("|", before.aliases()), String.join("|", after.aliases()));
        addDifference(values, "countryCode", before.countryCode(), after.countryCode());
        addDifference(values, "listingReason", before.listingReason(), after.listingReason());
        addDifference(values, "status", before.status(), after.status());
        return Map.copyOf(values);
    }

    private void addDifference(Map<String, WatchlistChange.FieldDifference> values, String field, String before, String after) {
        if (!before.equals(after)) {
            values.put(field, new WatchlistChange.FieldDifference(before, after));
        }
    }
}