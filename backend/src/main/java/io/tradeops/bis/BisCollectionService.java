package io.tradeops.bis;

import io.tradeops.account.AccountService;
import io.tradeops.audit.AuditService;
import io.tradeops.error.OperationException;
import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class BisCollectionService {
  private final BisRepository repository;
  private final BisHttpClient http;
  private final BisDiscovery discovery;
  private final BisParser parser;
  private final AuditService audit;
  private final AccountService accounts;
  private final String guide;
  private final Path storage;
  private final boolean scheduled;
  private final ZoneId zone;
  private final CronExpression cron;
  private final ExecutorService executor = Executors.newFixedThreadPool(2);
  private volatile boolean ready;

  public BisCollectionService(
      BisRepository repository,
      BisHttpClient http,
      BisDiscovery discovery,
      BisParser parser,
      AuditService audit,
      AccountService accounts,
      @Value(
              "${tradeops.bis.guide-url:https://www.bis.gov/licensing/guidance-on-end-user-and-end-use-controls-and-us-person-controls}")
          String guide,
      @Value("${tradeops.bis.storage:./storage/bis}") String storage,
      @Value("${tradeops.bis.schedule-enabled:true}") boolean scheduled,
      @Value("${tradeops.bis.cron:0 0 9 * * MON}") String cron,
      @Value("${tradeops.bis.zone:Asia/Seoul}") String zone) {
    this.repository = repository;
    this.http = http;
    this.discovery = discovery;
    this.parser = parser;
    this.audit = audit;
    this.accounts = accounts;
    this.guide = guide;
    this.storage = Path.of(storage).toAbsolutePath().normalize();
    this.scheduled = scheduled;
    this.cron = CronExpression.parse(cron);
    this.zone = ZoneId.of(zone);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initialize() {
    repository.recover();
    ready = true;
    tick();
  }

  @Scheduled(fixedDelay = 30000)
  public void tick() {
    if (!ready || !scheduled) return;
    Instant now = Instant.now();
    List<String> due = new ArrayList<>();
    for (var s : repository.sources()) {
      Object next = s.get("next_scheduled");
      if (next == null) {
        repository.schedule(s.get("code").toString(), next(now));
        continue;
      }
      Instant when =
          next instanceof java.sql.Timestamp t
              ? t.toInstant()
              : ((OffsetDateTime) next).toInstant();
      if (!when.isAfter(now) && s.get("active_run_id") == null) due.add(s.get("code").toString());
    }
    if (!due.isEmpty())
      try {
        launch(due, null, UUID.randomUUID().toString(), "SCHEDULED");
        for (String code : due) repository.schedule(code, next(now));
      } catch (OperationException ignored) {
      }
  }

  private Instant next(Instant after) {
    return Objects.requireNonNull(cron.next(after.atZone(zone))).toInstant();
  }

  public Object start(String source, String actor, String correlation) {
    if (source == null || !Set.of("DPL", "EL", "ALL").contains(source))
      throw new OperationException("SOURCE_INVALID", 400);
    return launch(
        source.equals("ALL") ? List.of("DPL", "EL") : List.of(source),
        actor,
        correlation,
        "MANUAL");
  }

  private Object launch(List<String> codes, String actor, String correlation, String trigger) {
    Map<String, Long> runs = new LinkedHashMap<>();
    try {
      for (String code : codes) runs.put(code, repository.start(code, actor, correlation, trigger));
    } catch (RuntimeException e) {
      for (long id : runs.values()) repository.failure(id, "GROUP_START_CANCELLED");
      throw e;
    }
    try {
      for (var run : runs.entrySet())
        repository.discovery(
            run.getValue(),
            guide,
            null,
            (String) repository.source(run.getKey()).get("download_url"),
            null);
      audit.record(
          actor,
          "COLLECTION_REQUESTED",
          "BIS_RUN",
          null,
          correlation,
          Map.of("success", true, "runIds", runs.values()));
    } catch (RuntimeException e) {
      for (long id : runs.values()) repository.failure(id, "AUDIT_UNAVAILABLE");
      throw e;
    }
    try {
      executor.submit(
          () -> {
            BisHttpClient.Download page;
            try {
              page = http.get(guide, null, null);
            } catch (OperationException e) {
              for (long id : runs.values()) repository.failure(id, e.code());
              return;
            }
            for (var run : runs.entrySet()) collect(run.getKey(), run.getValue(), page);
          });
    } catch (RejectedExecutionException e) {
      for (long id : runs.values()) repository.failure(id, "COLLECTION_INTERRUPTED");
      throw new OperationException("COLLECTION_INTERRUPTED", 503);
    }
    return Map.of("runIds", runs, "status", "RUNNING");
  }

  private void collect(String code, long id, BisHttpClient.Download page) {
    try {
      var state = repository.source(code);
      String url =
          discovery.discover(
              new String(page.bytes(), java.nio.charset.StandardCharsets.UTF_8), page.url(), code);
      http.allowed(url);
      repository.discovery(
          id, page.url(), url, (String) state.get("download_url"), BisParser.sha(page.bytes()));
      repository.stage(id, "DOWNLOADING");
      Map<String, Object> prior =
          state.get("current_snapshot_id") == null
              ? null
              : repository.snapshot(((Number) state.get("current_snapshot_id")).longValue());
      boolean compatible =
          prior != null
              && BisParser.VERSION.equals(
                  repository.run(((Number) prior.get("run_id")).longValue()).get("parser_version"));
      boolean same = compatible && Objects.equals(url, state.get("download_url"));
      var download =
          http.get(
              url,
              same ? (String) state.get("etag") : null,
              same ? (String) state.get("last_modified") : null);
      if (download.status() == 304) {
        if (compatible) {
          var previous = repository.run(((Number) prior.get("run_id")).longValue());
          if (previous.get("file_path") != null
              && Files.isRegularFile(resolve(previous.get("file_path").toString()))) {
            repository.file(
                id,
                new BisHttpClient.Download(
                    304,
                    download.url(),
                    new byte[0],
                    download.etag() == null ? (String) state.get("etag") : download.etag(),
                    download.lastModified() == null
                        ? (String) state.get("last_modified")
                        : download.lastModified()),
                previous.get("file_path").toString(),
                previous.get("file_hash").toString());
            repository.unchanged(id, prior);
            return;
          }
        }
        download = http.get(url, null, null);
      }
      Files.createDirectories(storage);
      String hash = BisParser.sha(download.bytes());
      String filename =
          code.toLowerCase(Locale.ROOT)
              + "-"
              + id
              + "-"
              + hash.substring(0, 12)
              + (code.equals("DPL") ? ".txt" : ".csv");
      Path temp = Files.createTempFile(storage, "download-", ".part");
      try {
        Files.write(temp, download.bytes());
        Files.move(temp, resolve(filename), StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temp);
      }
      repository.file(id, download, filename, hash);
      if (compatible && hash.equals(prior.get("file_hash"))) {
        repository.unchanged(id, prior);
        return;
      }
      repository.stage(id, "VALIDATING");
      var parsed = parser.parse(code, download.bytes());
      repository.stage(id, "SAVING");
      repository.saveParsed(id, parsed);
    } catch (OperationException e) {
      repository.failure(id, e.code());
    } catch (Exception e) {
      repository.failure(id, "COLLECTION_STORAGE_FAILED");
    }
  }

  @org.springframework.transaction.annotation.Transactional
  public void approve(long id, String actor, String correlation) {
    accounts.requireOwner(actor);
    repository.approve(id, actor);
    audit.record(actor, "COLLECTION_APPROVED", "BIS_RUN", id, correlation, Map.of("success", true));
  }

  public Path file(long runId, String actor, String correlation) {
    var r = repository.run(runId);
    if (r.get("file_path") == null) throw new OperationException("FILE_NOT_AVAILABLE", 404);
    Path p = resolve(r.get("file_path").toString());
    if (!Files.isRegularFile(p)) throw new OperationException("FILE_NOT_AVAILABLE", 404);
    audit.record(
        actor, "SOURCE_FILE_DOWNLOAD", "BIS_RUN", runId, correlation, Map.of("success", true));
    return p;
  }

  private Path resolve(String filename) {
    Path p = storage.resolve(filename).normalize();
    if (!p.startsWith(storage)) throw new OperationException("FILE_NOT_AVAILABLE", 404);
    return p;
  }

  public Map<String, Object> settings() {
    return Map.of(
        "guideUrl",
        guide,
        "scheduleEnabled",
        scheduled,
        "cron",
        cron.toString(),
        "zone",
        zone.toString());
  }

  @PreDestroy
  public void close() {
    ready = false;
    executor.shutdownNow();
  }
}
