package io.tradeops.watchlist.service;

import io.tradeops.watchlist.domain.WatchlistPayload;
import io.tradeops.watchlist.domain.WatchlistPayloadException;
import io.tradeops.watchlist.fixture.FictionalWatchlistFixtureGenerator;
import io.tradeops.watchlist.parser.CsvWatchlistPayloadParser;
import io.tradeops.watchlist.parser.XmlWatchlistPayloadParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class FictionalWatchlistSource {
    public LoadedPayload load(String version, WatchlistPayload.TransportFormat format) {
        String path = "fixtures/watchlist/" + version + "." + format.name().toLowerCase(java.util.Locale.ROOT);
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new WatchlistPayloadException("FIXTURE_NOT_FOUND", "The requested fictional fixture version is not available.");
            String raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            WatchlistPayload payload = format == WatchlistPayload.TransportFormat.XML ? new XmlWatchlistPayloadParser().parse(raw) : new CsvWatchlistPayloadParser().parse(raw);
            if (!payload.provider().equals(FictionalWatchlistFixtureGenerator.PROVIDER) || !payload.version().equals(version)) {
                throw new WatchlistPayloadException("FIXTURE_METADATA_INVALID", "The fictional fixture metadata is invalid.");
            }
            return new LoadedPayload(payload, sha256(raw));
        } catch (IOException exception) {
            throw new WatchlistPayloadException("FIXTURE_READ_FAILED", "The fictional fixture could not be read.");
        }
    }
    private String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 must be available", exception); }
    }
    public record LoadedPayload(WatchlistPayload payload, String checksum) { }
}