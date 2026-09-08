package io.tradeops.watchlist.domain;

import java.util.Map;

public record WatchlistChange(
        ChangeType type,
        String externalId,
        String entityName,
        Map<String, FieldDifference> differences) {

    public enum ChangeType {
        ADDED,
        CHANGED,
        REMOVED
    }

    public record FieldDifference(String previousValue, String currentValue) {
    }
}