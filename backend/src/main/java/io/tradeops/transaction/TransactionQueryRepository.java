package io.tradeops.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionQueryRepository {
    private final JdbcTemplate jdbc;

    public TransactionQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TransactionQueryService.Row> search(String term, String country) {
        StringBuilder sql = new StringBuilder("""
                select transaction_id, transaction_date, counterparty_name, country_code, amount_usd, currency
                from trade_transactions where 1=1
                """);
        List<Object> parameters = new ArrayList<>();
        if (term != null) {
            sql.append(" and (lower(transaction_id) like lower(?) or lower(counterparty_name) like lower(?))");
            parameters.add("%" + term + "%");
            parameters.add("%" + term + "%");
        }
        if (country != null) {
            sql.append(" and country_code=?");
            parameters.add(country);
        }
        sql.append(" order by transaction_date desc, id desc limit 100");
        return jdbc.query(sql.toString(), (rs, row) -> new TransactionQueryService.Row(
                rs.getString("transaction_id"), rs.getDate("transaction_date").toLocalDate(),
                rs.getString("counterparty_name"), rs.getString("country_code"),
                rs.getBigDecimal("amount_usd"), rs.getString("currency")), parameters.toArray());
    }

    public List<MonthAggregate> monthly() {
        return jdbc.query("""
                select extract(year from transaction_date) as transaction_year,
                       extract(month from transaction_date) as transaction_month,
                       count(*) as transaction_count, sum(amount_usd) as total_amount
                from trade_transactions
                group by extract(year from transaction_date), extract(month from transaction_date)
                order by transaction_year, transaction_month
                """, (rs, row) -> new MonthAggregate(rs.getInt("transaction_year"),
                rs.getInt("transaction_month"), rs.getInt("transaction_count"), rs.getBigDecimal("total_amount")));
    }

    public record MonthAggregate(int year, int month, int count, BigDecimal amount) { }
}
