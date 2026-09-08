package io.tradeops.transaction;

import static org.junit.jupiter.api.Assertions.*;
import io.tradeops.importer.TransactionImportService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TransactionQueryIntegrationTest {
    @Autowired TransactionImportService importer;
    @Autowired TransactionQueryService query;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from import_row_rejections");
        jdbc.update("delete from trade_transactions");
        jdbc.update("delete from import_runs");
        var result = importer.importCsv("fictional-query.csv", """
                transactionId,transactionDate,counterpartyName,countryCode,amountUsd,currency,dataOrigin
                TQ-001,2025-12-31,EMBER MERIDIAN WORKS,KR,10.25,USD,FICTIONAL
                TQ-002,2026-01-01,HARBOR GLASS PARTNERS,NL,20.50,USD,FICTIONAL
                TQ-003,2026-01-31,EMBER MERIDIAN WORKS,KR,5.25,USD,FICTIONAL
                TQ-004,2027-01-01,NORTHLINE BEACON LTD,MY,3.00,USD,FICTIONAL
                """.getBytes(StandardCharsets.UTF_8), "operator@tradeops.test", "query-test-0001");
        assertNotNull(result.runId());
        assertEquals(4, result.acceptedCount());
    }

    @Test
    void monthlyTotalsKeepYearsSeparateAndSortChronologically() {
        List<TransactionQueryService.Month> result = query.monthly();
        assertEquals(List.of("2025-12", "2026-01", "2027-01"), result.stream().map(TransactionQueryService.Month::month).toList());
        assertEquals(2, result.get(1).transactionCount());
        assertEquals(0, new BigDecimal("25.75").compareTo(result.get(1).totalAmountUsd()));
    }

    @Test
    void optionalFiltersSupportBlankAndCombinedQueries() {
        assertEquals(4, query.search(null, null).size());
        assertEquals(4, query.search(" ", " ").size());
        assertEquals("TQ-004", query.search(null, null).get(0).transactionId());
        assertEquals(List.of("TQ-003", "TQ-001"), query.search(" ember ", " kr ").stream().map(TransactionQueryService.Row::transactionId).toList());
        assertEquals(1, query.search("tq-002", null).size());
        assertEquals(0, query.search("ember", "NL").size());
    }

    @Test
    void emptyDatabaseReturnsEmptyCollections() {
        jdbc.update("delete from trade_transactions");
        assertTrue(query.monthly().isEmpty());
        assertTrue(query.search(null, null).isEmpty());
    }
}
