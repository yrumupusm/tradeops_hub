package io.tradeops.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TransactionQueryService {
    private final TransactionQueryRepository repository;

    public TransactionQueryService(TransactionQueryRepository repository) {
        this.repository = repository;
    }

    public List<Row> search(String term, String country) {
        String search = term == null || term.isBlank() ? null : term.trim();
        String countryCode = country == null || country.isBlank() ? null : country.trim().toUpperCase(Locale.ROOT);
        return repository.search(search, countryCode);
    }

    public List<Month> monthly() {
        return repository.monthly().stream()
                .map(row -> new Month(YearMonth.of(row.year(), row.month()).toString(), row.count(), row.amount()))
                .toList();
    }

    public record Row(String transactionId, LocalDate transactionDate, String counterpartyName,
                      String countryCode, BigDecimal amountUsd, String currency) { }
    public record Month(String month, int transactionCount, BigDecimal totalAmountUsd) { }
}
