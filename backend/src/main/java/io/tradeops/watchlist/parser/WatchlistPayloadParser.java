package io.tradeops.watchlist.parser;

import io.tradeops.watchlist.domain.WatchlistPayload;

public interface WatchlistPayloadParser {
    WatchlistPayload parse(String rawPayload);
}