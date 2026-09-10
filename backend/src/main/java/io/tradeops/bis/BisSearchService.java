package io.tradeops.bis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tradeops.audit.AuditService;
import io.tradeops.error.OperationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BisSearchService {
  public record Query(
      String q,
      String mode,
      String source,
      String country,
      String sort,
      Integer page,
      Integer size,
      Map<String, Long> snapshots,
      Boolean saveHistory) {}

  private final BisSearchRepository repository;
  private final BisRepository bis;
  private final ObjectMapper mapper;
  private final AuditService audit;

  public BisSearchService(
      BisSearchRepository repository, BisRepository bis, ObjectMapper mapper, AuditService audit) {
    this.repository = repository;
    this.bis = bis;
    this.mapper = mapper;
    this.audit = audit;
  }

  public Query prepare(Query request) {
    String q = Objects.toString(request.q(), "").strip(),
        mode = Objects.toString(request.mode(), "HYBRID"),
        source = Objects.toString(request.source(), ""),
        country = Objects.toString(request.country(), "").toUpperCase(Locale.ROOT),
        sort = Objects.toString(request.sort(), "score");
    int page = request.page() == null ? 0 : request.page(),
        size = request.size() == null ? 20 : request.size();
    if (q.length() > 250
        || !Set.of("BASIC", "SIMILAR", "HYBRID").contains(mode)
        || !Set.of("", "DPL", "EL").contains(source)
        || !country.matches("(?:[A-Z]{2})?")
        || !Set.of("score", "name", "country").contains(sort)
        || page < 0
        || page > 100000
        || size < 1
        || size > 100) throw new OperationException("INVALID_REQUEST", 400);
    Map<String, Long> versions = new LinkedHashMap<>();
    if (request.snapshots() != null) {
      if (request.snapshots().size() > 2) throw new OperationException("INVALID_REQUEST", 400);
      for (var e : request.snapshots().entrySet()) {
        if (e.getValue() == null || !Set.of("DPL", "EL").contains(e.getKey()))
          throw new OperationException("INVALID_REQUEST", 400);
        var snap = bis.snapshot(e.getValue());
        if (!e.getKey().equals(snap.get("source_code")))
          throw new OperationException("SNAPSHOT_NOT_FOUND", 404);
        versions.put(e.getKey(), e.getValue());
      }
    } else
      for (var s : bis.sources())
        if (s.get("current_snapshot_id") != null)
          versions.put(
              s.get("code").toString(), ((Number) s.get("current_snapshot_id")).longValue());
    return new Query(q, mode, source, country, sort, page, size, versions, request.saveHistory());
  }

  @Transactional
  public Object search(Query request, String username, String correlation) {
    long started = System.nanoTime();
    var query = prepare(request);
    var result =
        repository.search(
            query,
            new ArrayList<>(query.snapshots().values()),
            query.size(),
            query.page() * query.size());
    long ms = (System.nanoTime() - started) / 1000000;
    if (Boolean.TRUE.equals(query.saveHistory())) {
      repository.history(username, json(query), result.total(), ms);
      audit.record(
          username,
          "SEARCH_EXECUTED",
          "BIS_SEARCH",
          null,
          correlation,
          Map.of("success", true, "rows", result.total(), "snapshots", query.snapshots()));
    }
    return Map.of(
        "items",
        result.items(),
        "totalElements",
        result.total(),
        "page",
        query.page(),
        "size",
        query.size(),
        "snapshots",
        query.snapshots(),
        "elapsedMs",
        ms,
        "variants",
        NameNormalizer.variants(query.q()));
  }

  public Object detail(long id) {
    return repository.record(id);
  }

  public Object countries() {
    var q = prepare(new Query("", "BASIC", "", "", "name", 0, 20, null, false));
    return repository.countries(new ArrayList<>(q.snapshots().values()));
  }

  @Transactional
  public byte[] export(Query request, String actor, String correlation) {
    var query = prepare(request);
    StringWriter writer = new StringWriter();
    long count = 0;
    try (var printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
      var headers = new ArrayList<String>();
      headers.add("출처");
      for (var column : EXPORT_COLUMNS) headers.add(column[0]);
      printer.printRecord(headers);
      for (int offset = 0; ; offset += 1000) {
        var page =
            repository.search(
                query, new ArrayList<>(query.snapshots().values()), 1000, offset, true);
        for (var row : page.items()) {
          var raw = mapper.readTree(row.get("raw_json").toString());
          var values = new ArrayList<String>();
          values.add(safe(row.get("source_code")));
          int key = "DPL".equals(row.get("source_code")) ? 1 : 2;
          for (var column : EXPORT_COLUMNS) values.add(safe(raw.path(column[key]).asText("")));
          printer.printRecord(values);
          count++;
        }
        if (offset + page.items().size() >= page.total()) break;
      }
      printer.flush();
    } catch (IOException e) {
      throw new OperationException("EXPORT_FAILED", 500);
    }
    audit.record(
        actor,
        "EXPORT_GENERATED",
        "BIS_SEARCH",
        null,
        correlation,
        Map.of("success", true, "rows", count, "snapshots", query.snapshots()));
    return ("\uFEFF" + writer).getBytes(StandardCharsets.UTF_8);
  }

  private String safe(Object value) {
    String s = Objects.toString(value, "");
    return s.matches("(?s)^[\\s]*[=+@\\-].*") || s.startsWith("\t") || s.startsWith("\r")
        ? "'" + s
        : s;
  }

  // Label, DPL original key, EL original key. Empty keys represent unavailable fields.
  private static final String[][] EXPORT_COLUMNS = {
    {"이름", "Name", "Name"},
    {"다른 이름", "", "Alternate Name"},
    {"국가", "Country", "Country"},
    {"주소", "Street_Address", "Address"},
    {"도시", "City", "City"},
    {"주·지역", "State", "State/Province"},
    {"우편번호", "Postal_Code", "Postal Code"},
    {"발효일", "Effective_Date", "Effective Date"},
    {"연방 관보", "FR_Citation", "Federal Register Notice"},
    {"표준 명령", "Standard_Order", "Standard Order"},
    {"만료일", "Expiration_Date", ""},
    {"원본 변경일", "Last_Update", ""},
    {"변경 설명", "Action", ""},
    {"출처 목록", "", "Source List"},
    {"원본 번호", "", "Entity Number"},
    {"대상 유형", "", "SDN Type"},
    {"프로그램", "", "Programs"},
    {"직함", "", "Title"},
    {"해제·면제·만료일", "", "Date Lifted/Waived/Expired"},
    {"허가 요건", "", "License Requirement"},
    {"허가 검토 정책", "", "License Policy"},
    {"호출 부호", "", "Call Sign"},
    {"선박 유형", "", "Vessel Type"},
    {"총 톤수", "", "Gross Tonnage"},
    {"등록 총 톤수", "", "Gross Register Tonnage"},
    {"선적 국가", "", "Vessel Flag"},
    {"선박 소유자", "", "Vessel Owner"},
    {"비고", "", "Remarks/Notes"},
    {"주소 번호", "", "Address Number"},
    {"주소 비고", "", "Address Remarks"},
    {"대체 번호", "", "Alternate Number"},
    {"대체 유형", "", "Alternate Type"},
    {"대체 정보 비고", "", "Alternate Remarks"},
    {"원문 링크", "", "Web Link"}
  };

  public Object history(String user, int page, int size) {
    validatePage(page, size);
    return repository.history(user, page, size);
  }

  public Object recentHistory(String user) {
    return repository.recentHistory(user);
  }

  @Transactional
  public void deleteRecentHistory(String user, long id, String correlation) {
    repository.deleteRecentHistory(user, id);
    audit.record(
        user, "SEARCH_HISTORY_DELETED", "SEARCH_HISTORY", id, correlation, Map.of("success", true));
  }

  @Transactional
  public void deleteHistory(String user, Long id, String correlation) {
    repository.deleteHistory(user, id);
    audit.record(
        user, "SEARCH_HISTORY_DELETED", "SEARCH_HISTORY", id, correlation, Map.of("success", true));
  }

  public static void validatePage(int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw new OperationException("INVALID_REQUEST", 400);
  }

  private String json(Object v) {
    try {
      return mapper.writeValueAsString(v);
    } catch (Exception e) {
      throw new IllegalStateException("SERIALIZATION_FAILED");
    }
  }
}
