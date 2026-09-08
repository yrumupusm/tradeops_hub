package io.tradeops.watchlist.fixture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the committed fictional provider payloads used by local demos and tests.
 * These fixtures must never be replaced with real counterparties or external source rows.
 */
public final class FictionalWatchlistFixtureGenerator {

    public static final String PROVIDER = "FICTIONAL_WATCHLIST_PROVIDER";
    public static final String VERSION_A = "2026-01-A";
    public static final String VERSION_B = "2026-02-B";
    private static final List<String> CSV_HEADER = List.of(
            "provider", "version", "externalId", "entityName", "aliases",
            "countryCode", "listingReason", "status", "dataOrigin");

    private FictionalWatchlistFixtureGenerator() {
    }

    public static Map<String, String> generateCsvByVersion() {
        Map<String, String> fixtures = new LinkedHashMap<>();
        for (Map.Entry<String, List<FixtureRow>> version : rowsByVersion().entrySet()) {
            fixtures.put(version.getKey(), toCsv(version.getKey(), version.getValue()));
        }
        return Map.copyOf(fixtures);
    }

    public static Map<String, String> generateXmlByVersion() {
        Map<String, String> fixtures = new LinkedHashMap<>();
        for (Map.Entry<String, List<FixtureRow>> version : rowsByVersion().entrySet()) {
            fixtures.put(version.getKey(), toXml(version.getKey(), version.getValue()));
        }
        return Map.copyOf(fixtures);
    }

    public static void writeTo(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        for (Map.Entry<String, String> fixture : generateCsvByVersion().entrySet()) {
            Files.writeString(outputDirectory.resolve(fixture.getKey() + ".csv"), fixture.getValue(), StandardCharsets.UTF_8);
        }
        for (Map.Entry<String, String> fixture : generateXmlByVersion().entrySet()) {
            Files.writeString(outputDirectory.resolve(fixture.getKey() + ".xml"), fixture.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, List<FixtureRow>> rowsByVersion() {
        Map<String, List<FixtureRow>> rows = new LinkedHashMap<>();
        rows.put(VERSION_A, List.of(
                row("FCP-1001", "EMBER MERIDIAN WORKS", "EMBER MERIDIAN|EMW", "KR", "TRADE_REVIEW", "ACTIVE"),
                row("FCP-1002", "NORTHLINE BEACON LTD", "NORTHLINE BEACON", "SG", "DOCUMENT_REVIEW", "ACTIVE"),
                row("FCP-1003", "ARCLIGHT RELAY COMPANY", "ARCLIGHT RELAY|ARC RELAY", "AE", "OWNERSHIP_REVIEW", "ACTIVE"),
                row("FCP-1004", "PINEWARD DYNAMICS", "PINEWARD", "DE", "TRADE_REVIEW", "ACTIVE")));
        rows.put(VERSION_B, List.of(
                row("FCP-1001", "EMBER MERIDIAN WORKS", "EMBER MERIDIAN|EMW", "KR", "TRADE_REVIEW", "ACTIVE"),
                row("FCP-1002", "NORTHLINE BEACON LTD", "NORTHLINE BEACON|NLB", "MY", "OWNERSHIP_REVIEW", "ACTIVE"),
                row("FCP-1004", "PINEWARD DYNAMICS", "PINEWARD", "DE", "TRADE_REVIEW", "ACTIVE"),
                row("FCP-1005", "HARBOR GLASS PARTNERS", "HARBOR GLASS|HGP", "NL", "DOCUMENT_REVIEW", "ACTIVE")));
        return rows;
    }

    private static FixtureRow row(
            String externalId,
            String entityName,
            String aliases,
            String countryCode,
            String listingReason,
            String status) {
        return new FixtureRow(PROVIDER, externalId, entityName, aliases, countryCode, listingReason, status, "FICTIONAL");
    }

    private static String toCsv(String version, List<FixtureRow> rows) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(String.join(",", CSV_HEADER));
        for (FixtureRow row : rows) {
            lines.add(String.join(",", List.of(
                    row.provider(), version, row.externalId(), row.entityName(), row.aliases(),
                    row.countryCode(), row.listingReason(), row.status(), row.dataOrigin())));
        }
        return String.join("\n", lines);
    }

    private static String toXml(String version, List<FixtureRow> rows) {
        StringBuilder xml = new StringBuilder("<watchlist provider=\"")
                .append(escapeXml(PROVIDER))
                .append("\" version=\"")
                .append(escapeXml(version))
                .append("\" dataOrigin=\"FICTIONAL\">\n");
        for (FixtureRow row : rows) {
            xml.append("  <entity externalId=\"").append(escapeXml(row.externalId()))
                    .append("\" countryCode=\"").append(escapeXml(row.countryCode()))
                    .append("\" listingReason=\"").append(escapeXml(row.listingReason()))
                    .append("\" status=\"").append(escapeXml(row.status())).append("\">\n")
                    .append("    <entityName>").append(escapeXml(row.entityName())).append("</entityName>\n")
                    .append("    <aliases>\n");
            for (String alias : row.aliases().split("\\|")) {
                xml.append("      <alias>").append(escapeXml(alias)).append("</alias>\n");
            }
            xml.append("    </aliases>\n  </entity>");
            if (rows.indexOf(row) < rows.size() - 1) {
                xml.append('\n');
            }
        }
        return xml.append("\n</watchlist>").toString();
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record FixtureRow(
            String provider,
            String externalId,
            String entityName,
            String aliases,
            String countryCode,
            String listingReason,
            String status,
            String dataOrigin) {
    }
}