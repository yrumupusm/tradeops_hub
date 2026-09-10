package io.tradeops.bis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tradeops.error.OperationException;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BisRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final io.tradeops.audit.AuditService audit;

  public BisRepository(
      JdbcTemplate jdbc, ObjectMapper mapper, io.tradeops.audit.AuditService audit) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.audit = audit;
  }

  public List<Map<String, Object>> sources() {
    return jdbc.queryForList(
        "SELECT s.*,COALESCE(p.total_rows,0) AS total_rows,p.created_at AS data_updated_at FROM bis_sources s LEFT JOIN"
            + " bis_snapshots p ON p.id=s.current_snapshot_id ORDER BY s.code");
  }

  public Map<String, Object> source(String code) {
    return jdbc.queryForList("SELECT * FROM bis_sources WHERE code=?", code).stream()
        .findFirst()
        .orElseThrow(() -> new OperationException("SOURCE_INVALID", 400));
  }

  public Map<String, Object> run(long id) {
    return jdbc.queryForList("SELECT * FROM bis_runs WHERE id=?", id).stream()
        .findFirst()
        .orElseThrow(() -> new OperationException("NOT_FOUND", 404));
  }

  public long insertId(String sql, Object... args) {
    KeyHolder key = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          PreparedStatement s = con.prepareStatement(sql, new String[] {"id"});
          for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
          return s;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  @Transactional
  public long start(String code, String actor, String correlation, String trigger) {
    var source =
        jdbc.queryForList("SELECT * FROM bis_sources WHERE code=? FOR UPDATE", code).stream()
            .findFirst()
            .orElseThrow(() -> new OperationException("SOURCE_INVALID", 400));
    if (source.get("active_run_id") != null)
      throw new OperationException("COLLECTION_ALREADY_RUNNING", 409);
    long id =
        insertId(
            "INSERT INTO"
                + " bis_runs(source_code,trigger_type,actor,correlation_id,status,stage,previous_snapshot_id,parser_version)"
                + " VALUES(?,?,?,?,'RUNNING','DISCOVERING',?,?)",
            code,
            trigger,
            actor,
            correlation,
            source.get("current_snapshot_id"),
            BisParser.VERSION);
    jdbc.update("UPDATE bis_sources SET active_run_id=? WHERE code=?", id, code);
    return id;
  }

  public void stage(long id, String stage) {
    jdbc.update("UPDATE bis_runs SET stage=? WHERE id=? AND status='RUNNING'", stage, id);
  }

  public void discovery(long id, String guide, String found, String previous, String hash) {
    jdbc.update(
        "UPDATE bis_runs SET guide_url=?,download_url=?,previous_url=?,page_hash=? WHERE id=?",
        guide,
        found,
        previous,
        hash,
        id);
  }

  public void file(long id, BisHttpClient.Download d, String path, String hash) {
    jdbc.update(
        "UPDATE bis_runs SET final_url=?,etag=?,last_modified=?,file_path=?,file_hash=?,file_size=?"
            + " WHERE id=?",
        d.url(),
        d.etag(),
        d.lastModified(),
        path,
        hash,
        d.bytes().length,
        id);
  }

  @Transactional
  public void failure(long id, String code) {
    var r = run(id);
    jdbc.update(
        "UPDATE bis_runs SET"
            + " status='FAILED',stage='FINISHED',error_code=?,completed_at=CURRENT_TIMESTAMP WHERE"
            + " id=? AND status='RUNNING'",
        code,
        id);
    jdbc.update(
        "UPDATE bis_sources SET active_run_id=NULL,last_checked=CURRENT_TIMESTAMP,alert_code=?"
            + " WHERE code=? AND active_run_id=?",
        code,
        r.get("source_code"),
        id);
    audit.record(
        (String) r.get("actor"),
        "COLLECTION_FAILED",
        "BIS_RUN",
        id,
        r.get("correlation_id").toString(),
        Map.of("success", false, "code", code));
  }

  @Transactional
  public void unchanged(long id, Map<String, Object> prior) {
    var old = run(((Number) prior.get("run_id")).longValue());
    jdbc.update(
        "UPDATE bis_runs SET"
            + " file_hash=?,file_path=?,file_size=?,snapshot_id=?,total_rows=?,unchanged_rows=?,idempotent=TRUE"
            + " WHERE id=?",
        old.get("file_hash"),
        old.get("file_path"),
        old.get("file_size"),
        prior.get("id"),
        prior.get("total_rows"),
        prior.get("total_rows"),
        id);
    complete(id, ((Number) prior.get("id")).longValue());
  }

  public Map<String, Object> snapshot(long id) {
    return jdbc.queryForList("SELECT * FROM bis_snapshots WHERE id=?", id).stream()
        .findFirst()
        .orElseThrow(() -> new OperationException("SNAPSHOT_NOT_FOUND", 404));
  }

  public List<Map<String, Object>> issues(long id) {
    run(id);
    return jdbc.queryForList(
        "SELECT row_number,code,severity FROM bis_row_issues WHERE run_id=? ORDER BY row_number,id",
        id);
  }

  @Transactional
  public void saveParsed(long id, BisParser.Parsed parsed) {
    var r = run(id);
    String source = r.get("source_code").toString();
    jdbc.batchUpdate(
        "INSERT INTO bis_row_issues(run_id,row_number,code,severity) VALUES(?,?,?,?)",
        parsed.issues(),
        500,
        (s, v) -> {
          s.setLong(1, id);
          s.setInt(2, v.row());
          s.setString(3, v.code());
          s.setString(4, v.severity());
        });
    long rejected = parsed.issues().stream().filter(v -> v.severity().equals("ERROR")).count();
    jdbc.update(
        "UPDATE bis_runs SET total_rows=?,warnings=?,rejected_rows=? WHERE id=?",
        parsed.totalRows(),
        parsed.issues().stream().filter(v -> v.severity().equals("WARNING")).count(),
        rejected,
        id);
    if (rejected > 0) {
      failure(id, "ROW_VALIDATION_FAILED");
      return;
    }
    long snapshot =
        insertId(
            "INSERT INTO bis_snapshots(source_code,run_id,file_hash,total_rows) VALUES(?,?,?,?)",
            source,
            id,
            r.get("file_hash"),
            parsed.totalRows());
    jdbc.batchUpdate(
        "INSERT INTO"
            + " bis_records(snapshot_id,run_id,source_code,row_number,occurrence,raw_hash,raw_json,name,country,country_label,address,aliases_json,dates_json,validation)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        parsed.rows(),
        500,
        (s, v) -> {
          s.setLong(1, snapshot);
          s.setLong(2, id);
          s.setString(3, source);
          s.setInt(4, v.number());
          s.setInt(5, v.occurrence());
          s.setString(6, v.hash());
          s.setString(7, json(v.raw()));
          s.setString(8, v.name());
          s.setString(9, v.country());
          s.setString(10, v.countryLabel());
          s.setString(11, v.address());
          s.setString(12, json(v.aliases()));
          s.setString(13, json(v.dates()));
          s.setString(14, v.warning() ? "WARNING" : "VALID");
        });
    Map<Integer, Long> ids = new HashMap<>();
    jdbc.query(
        "SELECT row_number,id FROM bis_records WHERE snapshot_id=?",
        rs -> {
          ids.put(rs.getInt(1), rs.getLong(2));
        },
        snapshot);
    List<Object[]> names = new ArrayList<>();
    for (var row : parsed.rows()) {
      long recordId = ids.get(row.number());
      names.add(nameArgs(recordId, snapshot, row.name(), "NAME"));
      for (String alias : row.aliases()) names.add(nameArgs(recordId, snapshot, alias, "ALIAS"));
    }
    jdbc.batchUpdate(
        "INSERT INTO bis_search_names(record_id,snapshot_id,name,normalized,compact,tokenized,kind)"
            + " VALUES(?,?,?,?,?,?,?)",
        names);
    Object current = source(source).get("current_snapshot_id");
    int previous = 0, unchanged = 0;
    if (current != null) {
      previous = ((Number) snapshot(((Number) current).longValue()).get("total_rows")).intValue();
      unchanged =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM bis_records n JOIN bis_records p ON p.snapshot_id=? AND"
                  + " p.raw_hash=n.raw_hash AND p.occurrence=n.occurrence WHERE n.snapshot_id=?",
              Integer.class,
              current,
              snapshot);
    }
    jdbc.update(
        "UPDATE bis_runs SET snapshot_id=?,added_rows=?,removed_rows=?,unchanged_rows=? WHERE id=?",
        snapshot,
        parsed.totalRows() - unchanged,
        previous - unchanged,
        unchanged,
        id);
    if (previous > 0 && parsed.totalRows() < previous * 0.7) {
      jdbc.update(
          "UPDATE bis_runs SET"
              + " status='HELD',stage='FINISHED',error_code='ROW_COUNT_DROP',completed_at=CURRENT_TIMESTAMP"
              + " WHERE id=?",
          id);
      jdbc.update(
          "UPDATE bis_sources SET"
              + " active_run_id=NULL,last_checked=CURRENT_TIMESTAMP,alert_code='ROW_COUNT_DROP'"
              + " WHERE code=?",
          source);
      audit.record(
          (String) r.get("actor"),
          "COLLECTION_HELD",
          "BIS_RUN",
          id,
          r.get("correlation_id").toString(),
          Map.of("status", "HELD", "rows", parsed.totalRows()));
    } else complete(id, snapshot);
    // Make the new bulk-loaded version visible to the planner immediately; waiting for
    // autovacuum can select a small-table nested-loop plan for a much larger source.
    jdbc.execute("ANALYZE bis_records, bis_search_names");
  }

  private Object[] nameArgs(long id, long snapshot, String name, String kind) {
    return new Object[] {
      id,
      snapshot,
      name,
      NameNormalizer.normalize(name),
      NameNormalizer.compact(name),
      NameNormalizer.tokens(name),
      kind
    };
  }

  @Transactional
  public void complete(long id, long snapshot) {
    var run = run(id);
    String code = run.get("source_code").toString();
    jdbc.update(
        "UPDATE bis_runs SET status='COMPLETED',stage='FINISHED',completed_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        id);
    boolean moved =
        run.get("previous_url") != null
            && !Objects.equals(run.get("download_url"), run.get("previous_url"));
    jdbc.update(
        "UPDATE bis_sources SET"
            + " current_snapshot_id=?,active_run_id=NULL,download_url=?,final_url=?,etag=?,last_modified=?,last_checked=CURRENT_TIMESTAMP,last_success=CURRENT_TIMESTAMP,alert_code=?"
            + " WHERE code=?",
        snapshot,
        run.get("download_url"),
        run.get("final_url"),
        run.get("etag"),
        run.get("last_modified"),
        moved ? "SOURCE_URL_CHANGED" : null,
        code);
    audit.record(
        (String) run.get("actor"),
        "COLLECTION_FINISHED",
        "BIS_RUN",
        id,
        run.get("correlation_id").toString(),
        Map.of("success", true, "rows", run.get("total_rows")));
  }

  @Transactional
  public void approve(long id, String actor) {
    var r =
        jdbc.queryForList("SELECT * FROM bis_runs WHERE id=? FOR UPDATE", id).stream()
            .findFirst()
            .orElseThrow(() -> new OperationException("NOT_FOUND", 404));
    if (!"HELD".equals(r.get("status"))) throw new OperationException("RUN_NOT_HELD", 409);
    var source =
        jdbc.queryForList("SELECT * FROM bis_sources WHERE code=? FOR UPDATE", r.get("source_code"))
            .get(0);
    if (source.get("active_run_id") != null)
      throw new OperationException("COLLECTION_ALREADY_RUNNING", 409);
    if (source.get("current_snapshot_id") != null
        && ((Number) source.get("current_snapshot_id")).longValue()
            > ((Number) r.get("snapshot_id")).longValue())
      throw new OperationException("RUN_SUPERSEDED", 409);
    jdbc.update(
        "UPDATE bis_runs SET approved_by=?,approved_at=CURRENT_TIMESTAMP,error_code=NULL WHERE"
            + " id=?",
        actor,
        id);
    complete(id, ((Number) r.get("snapshot_id")).longValue());
  }

  public Map<String, Object> runs(int page, int size, boolean files) {
    String where = files ? " WHERE file_path IS NOT NULL" : "";
    return Map.of(
        "items",
        jdbc.queryForList(
            "SELECT * FROM bis_runs" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
            size,
            page * size),
        "totalElements",
        jdbc.queryForObject("SELECT COUNT(*) FROM bis_runs" + where, Long.class),
        "page",
        page,
        "size",
        size);
  }

  public List<Map<String, Object>> changes(long runId, int page, int size) {
    var r = run(runId);
    if (r.get("snapshot_id") == null) return List.of();
    long snap = ((Number) r.get("snapshot_id")).longValue();
    long previous =
        r.get("previous_snapshot_id") == null
            ? -1
            : ((Number) r.get("previous_snapshot_id")).longValue();
    return jdbc.queryForList(
        "SELECT n.id,n.name,n.country,n.row_number,'ADDED' AS change_type FROM bis_records n WHERE"
            + " n.snapshot_id=? AND NOT EXISTS(SELECT 1 FROM bis_records p WHERE p.snapshot_id=?"
            + " AND p.raw_hash=n.raw_hash AND p.occurrence=n.occurrence) UNION ALL SELECT"
            + " p.id,p.name,p.country,p.row_number,'REMOVED' AS change_type FROM bis_records p"
            + " WHERE p.snapshot_id=? AND NOT EXISTS(SELECT 1 FROM bis_records n WHERE"
            + " n.snapshot_id=? AND n.raw_hash=p.raw_hash AND n.occurrence=p.occurrence) ORDER BY"
            + " change_type,id LIMIT ? OFFSET ?",
        snap,
        previous,
        previous,
        snap,
        size,
        page * size);
  }

  public void recover() {
    jdbc.update(
        "UPDATE bis_runs SET"
            + " status='FAILED',stage='FINISHED',error_code='PROCESS_INTERRUPTED',completed_at=CURRENT_TIMESTAMP"
            + " WHERE status='RUNNING'");
    jdbc.update("UPDATE bis_sources SET active_run_id=NULL WHERE active_run_id IS NOT NULL");
  }

  public void schedule(String source, Instant next) {
    jdbc.update(
        "UPDATE bis_sources SET next_scheduled=? WHERE code=?", Timestamp.from(next), source);
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("SERIALIZATION_FAILED");
    }
  }
}
