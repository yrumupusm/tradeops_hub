package io.tradeops.watchlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.tradeops.watchlist.domain.WatchlistChange;
import io.tradeops.watchlist.domain.WatchlistComparison;
import io.tradeops.watchlist.domain.WatchlistPayload;
import io.tradeops.watchlist.domain.WatchlistPayloadException;
import io.tradeops.watchlist.parser.CsvWatchlistPayloadParser;
import io.tradeops.watchlist.parser.XmlWatchlistPayloadParser;
import io.tradeops.watchlist.service.WatchlistComparator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WatchlistPayloadAndComparisonTest {
    private final CsvWatchlistPayloadParser csvParser = new CsvWatchlistPayloadParser();
    private final XmlWatchlistPayloadParser xmlParser = new XmlWatchlistPayloadParser();
    private final WatchlistComparator comparator = new WatchlistComparator();

    @Test
    void csvAndXmlForTheSameVersionNormalizeToTheSameRecords() throws IOException {
        WatchlistPayload csv = csvParser.parse(resource("fixtures/watchlist/2026-02-B.csv"));
        WatchlistPayload xml = xmlParser.parse(resource("fixtures/watchlist/2026-02-B.xml"));

        assertEquals(WatchlistPayload.TransportFormat.CSV, csv.format());
        assertEquals(WatchlistPayload.TransportFormat.XML, xml.format());
        assertEquals(csv.provider(), xml.provider());
        assertEquals(csv.version(), xml.version());
        assertEquals(csv.records(), xml.records());
    }

    @Test
    void comparisonProducesTheExpectedOperationalChangeSet() throws IOException {
        WatchlistPayload previous = xmlParser.parse(resource("fixtures/watchlist/2026-01-A.xml"));
        WatchlistPayload candidate = xmlParser.parse(resource("fixtures/watchlist/2026-02-B.xml"));

        WatchlistComparison comparison = comparator.compare(Optional.of(previous), candidate);

        assertEquals(1, comparison.addedCount());
        assertEquals(1, comparison.changedCount());
        assertEquals(1, comparison.removedCount());
        assertEquals(2, comparison.unchangedCount());
        WatchlistChange changed = comparison.changes().stream()
                .filter(change -> change.type() == WatchlistChange.ChangeType.CHANGED)
                .findFirst().orElseThrow();
        assertEquals("FCP-1002", changed.externalId());
        assertEquals(java.util.Set.of("aliases", "countryCode", "listingReason"), changed.differences().keySet());
    }

    @Test
    void duplicateExternalIdsAreRejectedBeforeComparison() throws IOException {
        String duplicate = resource("fixtures/watchlist/2026-01-A.csv")
                + "\nFICTIONAL_WATCHLIST_PROVIDER,2026-01-A,FCP-1001,DUPLICATE,,KR,TRADE_REVIEW,ACTIVE,FICTIONAL";

        WatchlistPayloadException exception = assertThrows(WatchlistPayloadException.class, () -> csvParser.parse(duplicate));

        assertEquals("DUPLICATE_EXTERNAL_ID", exception.code());
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing fixture resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}