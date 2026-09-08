package io.tradeops.watchlist.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record WatchlistRecord(
        String provider,
        String externalId,
        String entityName,
        List<String> aliases,
        String countryCode,
        String listingReason,
        String status,
        String dataOrigin) {

    public WatchlistRecord {
        provider = required(provider, "provider").toUpperCase(Locale.ROOT);
        externalId = required(externalId, "externalId");
        entityName = required(entityName, "entityName");
        countryCode = optionalUppercase(countryCode, "countryCode");
        listingReason = required(listingReason, "listingReason").toUpperCase(Locale.ROOT);
        status = required(status, "status").toUpperCase(Locale.ROOT);
        dataOrigin = required(dataOrigin, "dataOrigin").toUpperCase(Locale.ROOT);
        aliases = aliases == null ? List.of() : aliases.stream()
                .filter(Objects::nonNull)
                .map(WatchlistRecord::normalizeText)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (!dataOrigin.equals("FICTIONAL")) {
            throw new WatchlistPayloadException("UNSUPPORTED_DATA_ORIGIN", "Only fictional data is supported by this portfolio demo.");
        }
        if (!countryCode.isBlank() && !countryCode.matches("[A-Z]{2}")) {
            throw new WatchlistPayloadException("INVALID_COUNTRY_CODE", "countryCode must be a two-letter code.");
        }
        if (!List.of("TRADE_REVIEW", "DOCUMENT_REVIEW", "OWNERSHIP_REVIEW").contains(listingReason)) {
            throw new WatchlistPayloadException("INVALID_LISTING_REASON", "listingReason is not supported.");
        }
        if (!List.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new WatchlistPayloadException("INVALID_STATUS", "status is not supported.");
        }
    }

    public String stableKey() {
        return provider + ":" + externalId;
    }

    public String canonicalHash() {
        String canonical = String.join("\u001F",
                normalizeText(entityName),
                String.join("|", aliases),
                countryCode,
                listingReason,
                status);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    public static String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) {
            throw new WatchlistPayloadException("REQUIRED_FIELD_MISSING", field + " is required.");
        }
        return normalized;
    }

    private static String optionalUppercase(String value, String field) {
        return normalizeText(value);
    }
}