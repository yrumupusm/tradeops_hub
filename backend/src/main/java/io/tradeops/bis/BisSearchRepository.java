package io.tradeops.bis;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BisSearchRepository {
  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate named;

  public BisSearchRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.named = new NamedParameterJdbcTemplate(jdbc);
  }

  public record Result(List<Map<String, Object>> items, long total) {}

  public Result search(BisSearchService.Query query, List<Long> snapshots, int limit, int offset) {
    return search(query, snapshots, limit, offset, false);
  }

  public Result search(
      BisSearchService.Query query,
      List<Long> snapshots,
      int limit,
      int offset,
      boolean originalFields) {
    if (snapshots.isEmpty()) return new Result(List.of(), 0);
    Map<String, Object> p = new HashMap<>();
    p.put("snapshots", snapshots);
    p.put("country", query.country());
    p.put("source", query.source());
    p.put("mode", query.mode());
    p.put("limit", limit);
    p.put("offset", offset);
    List<String> variants = NameNormalizer.variants(query.q());
    StringJoiner values = new StringJoiner(",");
    for (int i = 0; i < variants.size(); i++) {
      values.add("(:n" + i + ",:c" + i + ",:t" + i + ")");
      p.put("n" + i, variants.get(i));
      p.put("c" + i, NameNormalizer.compact(variants.get(i)));
      p.put("t" + i, NameNormalizer.tokens(variants.get(i)));
    }
    String base =
        "WITH queries(norm,compact,tokens) AS (VALUES "
            + values
            + "), scored AS (SELECT n.record_id,n.name AS matched_name,n.kind,CASE WHEN"
            + " (n.normalized=q.norm OR n.compact=q.compact) THEN CASE WHEN n.kind='NAME' THEN 0"
            + " ELSE 1 END WHEN LENGTH(q.norm)>=2 AND STRPOS(n.normalized,q.norm)>0 THEN 2 ELSE 3"
            + " END AS"
            + " priority,GREATEST(similarity(n.normalized,q.norm),similarity(n.compact,q.compact),similarity(n.tokenized,q.tokens))"
            + " AS score FROM bis_search_names n CROSS JOIN queries q WHERE n.snapshot_id IN"
            + " (:snapshots) AND (n.normalized=q.norm OR n.compact=q.compact OR (LENGTH(q.norm)>=2"
            + " AND n.normalized LIKE '%'||q.norm||'%') OR n.normalized % q.norm OR n.compact %"
            + " q.compact OR n.tokenized % q.tokens)), best AS (SELECT DISTINCT ON (record_id) *"
            + " FROM scored WHERE (:mode<>'BASIC' OR priority<3) AND (:mode<>'SIMILAR' OR"
            + " priority=3) AND (priority<3 OR score>=0.42) ORDER BY record_id,priority,score"
            + " DESC,matched_name), matched AS (SELECT r.*,b.priority,b.score,b.matched_name,b.kind"
            + " FROM bis_records r JOIN best b ON b.record_id=r.id WHERE r.snapshot_id IN"
            + " (:snapshots) AND (:country='' OR r.country=:country) AND (:source='' OR"
            + " r.source_code=:source)), grouped AS (SELECT DISTINCT ON(snapshot_id,raw_hash)"
            + " *,COUNT(*) OVER(PARTITION BY snapshot_id,raw_hash) AS duplicate_count FROM matched"
            + " ORDER BY snapshot_id,raw_hash,id) ";
    if (query.q().isBlank())
      base =
          "WITH grouped AS (SELECT DISTINCT ON(snapshot_id,raw_hash) *,0 AS priority,1.0 AS"
              + " score,name AS matched_name,'NAME' AS kind,COUNT(*) OVER(PARTITION BY"
              + " snapshot_id,raw_hash) AS duplicate_count FROM bis_records WHERE snapshot_id IN"
              + " (:snapshots) AND (:country='' OR country=:country) AND (:source='' OR"
              + " source_code=:source) ORDER BY snapshot_id,raw_hash,id) ";
    String order =
        switch (query.sort()) {
          case "name" -> "name,id";
          case "country" -> "country,name,id";
          default -> "priority,score DESC,name,id";
        };
    Long total = named.queryForObject(base + "SELECT COUNT(*) FROM grouped", p, Long.class);
    List<Map<String, Object>> rows =
        named.queryForList(
            base
                + "SELECT"
                + " id,snapshot_id,run_id,source_code,row_number,name,country,country_label,address,aliases_json,validation,priority,score,matched_name,duplicate_count"
                + (originalFields ? ",raw_json" : "")
                + " FROM grouped ORDER BY "
                + order
                + " LIMIT :limit OFFSET :offset",
            p);
    return new Result(rows, total == null ? 0 : total);
  }

  public Map<String, Object> record(long id) {
    return jdbc.queryForList("SELECT * FROM bis_records WHERE id=?", id).stream()
        .findFirst()
        .orElseThrow(() -> new io.tradeops.error.OperationException("NOT_FOUND", 404));
  }

  public void history(String username, String json, long count, long elapsed) {
    jdbc.update(
        "INSERT INTO search_history(username,query_json,total_results,elapsed_ms) VALUES(?,?,?,?)",
        username,
        json,
        count,
        elapsed);
  }

  public Map<String, Object> history(String username, int page, int size) {
    return Map.of(
        "items",
        jdbc.queryForList(
            "SELECT * FROM search_history WHERE username=? ORDER BY id DESC LIMIT ? OFFSET ?",
            username,
            size,
            page * size),
        "totalElements",
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM search_history WHERE username=?", Long.class, username),
        "page",
        page,
        "size",
        size);
  }

  public void deleteHistory(String username, Long id) {
    if (id == null) jdbc.update("DELETE FROM search_history WHERE username=?", username);
    else if (jdbc.update("DELETE FROM search_history WHERE username=? AND id=?", username, id) == 0)
      throw new io.tradeops.error.OperationException("NOT_FOUND", 404);
  }

  public List<Map<String, Object>> recentHistory(String username) {
    return jdbc.queryForList(
        "SELECT id,q FROM (SELECT DISTINCT ON (LOWER(TRIM(query_json::jsonb->>'q')))"
            + " id,TRIM(query_json::jsonb->>'q') AS q FROM search_history WHERE username=? AND"
            + " TRIM(query_json::jsonb->>'q')<>'' ORDER BY LOWER(TRIM(query_json::jsonb->>'q')),id"
            + " DESC) recent ORDER BY id DESC LIMIT 5",
        username);
  }

  public void deleteRecentHistory(String username, long id) {
    int count =
        jdbc.update(
            "DELETE FROM search_history WHERE username=? AND LOWER(TRIM(query_json::jsonb->>'q'))="
                + " (SELECT LOWER(TRIM(query_json::jsonb->>'q')) FROM search_history WHERE"
                + " username=? AND id=?)",
            username,
            username,
            id);
    if (count == 0) throw new io.tradeops.error.OperationException("NOT_FOUND", 404);
  }

  public List<Map<String, Object>> countries(List<Long> snapshots) {
    if (snapshots.isEmpty()) return List.of();
    return named.queryForList(
        "SELECT country,COUNT(*) AS row_count FROM bis_records WHERE snapshot_id IN (:ids) GROUP BY"
            + " country ORDER BY country",
        Map.of("ids", snapshots));
  }
}
