package io.tradeops.watchlist.domain;

public class WatchlistPayloadException extends RuntimeException {
    private final String code;

    public WatchlistPayloadException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}