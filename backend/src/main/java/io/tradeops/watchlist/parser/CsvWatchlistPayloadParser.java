package io.tradeops.watchlist.parser;

import io.tradeops.watchlist.domain.WatchlistPayload;
import io.tradeops.watchlist.domain.WatchlistPayloadException;
import io.tradeops.watchlist.domain.WatchlistRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CsvWatchlistPayloadParser implements WatchlistPayloadParser {
    private static final List<String> HEADER = List.of(
            "provider", "version", "externalId", "entityName", "aliases",
            "countryCode", "listingReason", "status", "dataOrigin");

    @Override
    public WatchlistPayload parse(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new WatchlistPayloadException("PAYLOAD_EMPTY", "The CSV payload is empty.");
        }
        List<String> lines = rawPayload.lines().filter(line -> !line.isBlank()).toList();
        if (lines.size() < 2) {
            throw new WatchlistPayloadException("PAYLOAD_EMPTY", "The CSV payload contains no records.");
        }
        List<String> header = split(lines.get(0));
        if (!HEADER.equals(header)) {
            throw new WatchlistPayloadException("CSV_HEADER_INVALID", "The CSV header does not match the documented format.");
        }
        List<WatchlistRecord> records = new ArrayList<>();
        String provider = null;
        String version = null;
        for (int index = 1; index < lines.size(); index++) {
            List<String> values = split(lines.get(index));
            if (values.size() != HEADER.size()) {
                throw new WatchlistPayloadException("CSV_ROW_INVALID", "A CSV row has an invalid column count.");
            }
            Map<String, String> row = java.util.stream.IntStream.range(0, HEADER.size()).boxed()
                    .collect(Collectors.toMap(HEADER::get, values::get));
            provider = provider == null ? row.get("provider") : provider;
            version = version == null ? row.get("version") : version;
            if (!provider.equals(row.get("provider")) || !version.equals(row.get("version"))) {
                throw new WatchlistPayloadException("PAYLOAD_METADATA_MISMATCH", "Every CSV row must use one provider and version.");
            }
            records.add(new WatchlistRecord(row.get("provider"), row.get("externalId"), row.get("entityName"),
                    splitAliases(row.get("aliases")), row.get("countryCode"), row.get("listingReason"),
                    row.get("status"), row.get("dataOrigin")));
        }
        return new WatchlistPayload(provider, version, WatchlistPayload.TransportFormat.CSV, records);
    }

    private List<String> split(String line) {
        if (line.contains("\"")) {
            throw new WatchlistPayloadException("CSV_QUOTING_UNSUPPORTED", "Quoted CSV values are not supported by this fixture format.");
        }
        return List.of(line.split(",", -1));
    }

    private List<String> splitAliases(String aliases) {
        return aliases == null || aliases.isBlank() ? List.of() : List.of(aliases.split("\\|", -1));
    }
}