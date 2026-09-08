package io.tradeops.watchlist.domain;

import java.util.List;

public record WatchlistPayload(
        String provider,
        String version,
        TransportFormat format,
        List<WatchlistRecord> records) {

    public WatchlistPayload {
        String normalizedProvider = WatchlistRecord.normalizeText(provider);
        String normalizedVersion = WatchlistRecord.normalizeText(version);
        provider = normalizedProvider;
        version = normalizedVersion;
        records = List.copyOf(records);
        if (normalizedProvider.isBlank() || normalizedVersion.isBlank()) {
            throw new WatchlistPayloadException("PAYLOAD_METADATA_MISSING", "provider and version are required.");
        }
        if (records.isEmpty()) {
            throw new WatchlistPayloadException("PAYLOAD_EMPTY", "The payload contains no records.");
        }
        if (records.stream().anyMatch(record -> !record.provider().equals(normalizedProvider))) {
            throw new WatchlistPayloadException("PROVIDER_MISMATCH", "Every record must match the payload provider.");
        }
        long uniqueKeys = records.stream().map(WatchlistRecord::stableKey).distinct().count();
        if (uniqueKeys != records.size()) {
            throw new WatchlistPayloadException("DUPLICATE_EXTERNAL_ID", "externalId must be unique within a provider payload.");
        }
    }

    public enum TransportFormat {
        CSV,
        XML
    }
}