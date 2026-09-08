package io.tradeops.watchlist.domain;

import java.util.List;

public record WatchlistComparison(
        int addedCount,
        int changedCount,
        int removedCount,
        int unchangedCount,
        List<WatchlistChange> changes) {
}