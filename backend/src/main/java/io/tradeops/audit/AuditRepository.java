package io.tradeops.audit;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {
  private final JdbcTemplate jdbc;

  public AuditRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void append(
      String actor, String event, String kind, String id, String correlation, String details) {
    jdbc.update(
        "INSERT INTO"
            + " audit_events(actor_username,event_type,entity_type,entity_id,correlation_id,detail)"
            + " VALUES(?,?,?,?,?,CAST(? AS JSONB))",
        actor,
        event,
        kind,
        id,
        correlation,
        details);
  }

  public Map<String, Object> list(
      String actor, String event, String from, String to, int page, int size) {
    String where =
        " WHERE (?='' OR actor_username=?) AND (?='' OR event_type=?) AND (?='' OR"
            + " occurred_at>=CAST(NULLIF(?,'') AS TIMESTAMP WITH TIME ZONE)) AND (?='' OR"
            + " occurred_at<CAST(NULLIF(?,'') AS TIMESTAMP WITH TIME ZONE))";
    Object[] args = {actor, actor, event, event, from, from, to, to};
    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events" + where, Long.class, args);
    List<Object> params = new ArrayList<>(Arrays.asList(args));
    params.add(size);
    params.add(page * size);
    return Map.of(
        "items",
        jdbc.queryForList(
            "SELECT id,actor_username,event_type,entity_type,entity_id,correlation_id,CAST(detail"
                + " AS TEXT) AS detail,occurred_at FROM audit_events"
                + where
                + " ORDER BY id DESC LIMIT ? OFFSET ?",
            params.toArray()),
        "totalElements",
        total,
        "page",
        page,
        "size",
        size);
  }
}
