package io.tradeops.bis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.tradeops.account.AccountService;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "spring.flyway.locations=classpath:db/migration,classpath:db/{vendor}",
      "tradeops.bis.schedule-enabled=false",
      "tradeops.bis.storage=../artifacts/bis-fixture-storage",
      "tradeops.owner.username=owner@tradeops.test",
      "tradeops.owner.password=Fixture-owner-pass"
    })
@EnabledIfEnvironmentVariable(named = "TRADEOPS_TEST_PG", matches = "true")
class BisPostgresIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired BisRepository repository;
  @Autowired BisParser parser;
  @Autowired BisCollectionService collection;
  @Autowired BisSearchService search;
  @Autowired AccountService accounts;
  @MockBean BisHttpClient http;
  @Autowired io.tradeops.audit.AuditService audit;
  String guide =
      "<h4>Denied Persons List</h4><p><a href='/release.txt'>TXT</a></p><h4>Entity List</h4><p><a"
          + " href='/release.csv'>CSV</a></p>";

  @BeforeEach
  void setup() throws Exception {
    try (var connection = jdbc.getDataSource().getConnection()) {
      assertThat(connection.getMetaData().getURL()).endsWith("/tradeops_fixtures");
    }
    jdbc.execute(
        "TRUNCATE bis_search_names,bis_records,bis_snapshots,bis_row_issues,bis_runs,search_history"
            + " RESTART IDENTITY CASCADE");
    jdbc.update(
        "UPDATE bis_sources SET"
            + " current_snapshot_id=NULL,active_run_id=NULL,download_url=NULL,final_url=NULL,etag=NULL,last_modified=NULL,alert_code=NULL");
    reset(http);
    when(http.allowed(anyString())).thenAnswer(i -> URI.create(i.getArgument(0)));
    when(http.get(contains("guidance"), any(), any()))
        .thenReturn(
            download(guide.getBytes(StandardCharsets.UTF_8), "https://www.bis.gov/guidance"));
  }

  BisHttpClient.Download download(byte[] bytes, String url) {
    return new BisHttpClient.Download(200, url, bytes, null, null);
  }

  byte[] fixture(int count) throws Exception {
    StringWriter out = new StringWriter();
    try (var csv = new CSVPrinter(out, CSVFormat.DEFAULT)) {
      csv.printRecord((Object[]) BisParsingTest.DPL);
      for (int i = 0; i < count; i++)
        csv.printRecord(
            i == 0 ? "NORTHLINE BEACON" : "FICTIONAL MERIDIAN " + String.format("%05d", i),
            "1 Fictional Road",
            "Example City",
            "",
            "KR",
            "",
            "1/1/2026",
            "",
            "Y",
            "1/2/2026",
            "",
            "FICTIONAL");
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  long save(int count) throws Exception {
    long id =
        repository.start("DPL", "owner@tradeops.test", UUID.randomUUID().toString(), "MANUAL");
    byte[] bytes = fixture(count);
    repository.file(
        id,
        download(bytes, "https://media.bis.gov/release.txt"),
        "fixture.txt",
        BisParser.sha(bytes));
    repository.saveParsed(id, parser.parse("DPL", bytes));
    return id;
  }

  @Test
  void sourceChangeIsAtomicAndDeclinesRequireApproval() throws Exception {
    long first = save(10);
    long old = ((Number) repository.run(first).get("snapshot_id")).longValue();
    long held = save(2);
    assertThat(repository.run(held).get("status")).isEqualTo("HELD");
    assertThat(((Number) repository.source("DPL").get("current_snapshot_id")).longValue())
        .isEqualTo(old);
    assertThatThrownBy(
            () -> collection.approve(held, "someone@tradeops.test", UUID.randomUUID().toString()))
        .hasMessage("ACCESS_DENIED");
    collection.approve(held, "owner@tradeops.test", UUID.randomUUID().toString());
    assertThat(repository.run(held).get("status")).isEqualTo("COMPLETED");
    assertThat(repository.changes(held, 0, 20)).hasSize(8);
  }

  @Test
  void invalidRowsKeepCurrentSnapshotAndTheirIssue() throws Exception {
    save(4);
    Object prior = repository.source("DPL").get("current_snapshot_id");
    byte[] bytes = fixture(4);
    String broken = new String(bytes, StandardCharsets.UTF_8).replace("NORTHLINE BEACON", "");
    long id =
        repository.start("DPL", "owner@tradeops.test", UUID.randomUUID().toString(), "MANUAL");
    repository.file(
        id, download(bytes, "https://media.bis.gov/release.txt"), "fixture.txt", "hash");
    repository.saveParsed(id, parser.parse("DPL", broken.getBytes(StandardCharsets.UTF_8)));
    assertThat(repository.run(id).get("status")).isEqualTo("FAILED");
    assertThat(repository.source("DPL").get("current_snapshot_id")).isEqualTo(prior);
    assertThat(repository.issues(id)).anyMatch(r -> r.get("code").equals("NAME_REQUIRED"));
  }

  @Test
  void duplicateStartAndRestartRecoveryPreservePreviousData() throws Exception {
    save(3);
    Object prior = repository.source("DPL").get("current_snapshot_id");
    long id =
        repository.start("DPL", "owner@tradeops.test", UUID.randomUUID().toString(), "MANUAL");
    assertThatThrownBy(
            () ->
                repository.start(
                    "DPL", "owner@tradeops.test", UUID.randomUUID().toString(), "MANUAL"))
        .hasMessage("COLLECTION_ALREADY_RUNNING");
    repository.recover();
    assertThat(repository.run(id).get("error_code")).isEqualTo("PROCESS_INTERRUPTED");
    assertThat(repository.source("DPL").get("current_snapshot_id")).isEqualTo(prior);
  }

  @Test
  void pipelineDiscoversAndStoresFileWithoutExternalNetwork() throws Exception {
    when(http.get(endsWith("release.txt"), any(), any()))
        .thenReturn(download(fixture(4), "https://media.bis.gov/release.txt"));
    collection.start("DPL", "owner@tradeops.test", UUID.randomUUID().toString());
    await(1);
    assertThat(repository.run(1).get("status")).isEqualTo("COMPLETED");
    assertThat(repository.run(1).get("total_rows")).isEqualTo(4);
    collection.start("DPL", "owner@tradeops.test", UUID.randomUUID().toString());
    await(2);
    assertThat(repository.run(2).get("idempotent")).isEqualTo(true);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bis_snapshots", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void missingLinkPreservesSourceAndRecordsFailure() throws Exception {
    save(4);
    Object prior = repository.source("DPL").get("current_snapshot_id");
    when(http.get(contains("guidance"), any(), any()))
        .thenReturn(
            download(
                "<h4>Removed</h4>".getBytes(StandardCharsets.UTF_8),
                "https://www.bis.gov/guidance"));
    collection.start("DPL", "owner@tradeops.test", UUID.randomUUID().toString());
    await(2);
    assertThat(repository.run(2).get("error_code")).isEqualTo("DOWNLOAD_LINK_MISSING");
    assertThat(repository.source("DPL").get("current_snapshot_id")).isEqualTo(prior);
  }

  @Test
  void officialLinkChangeIsAcceptedAndRecorded() throws Exception {
    when(http.get(endsWith("release.txt"), any(), any()))
        .thenReturn(download(fixture(4), "https://media.bis.gov/release.txt"));
    collection.start("DPL", "owner@tradeops.test", UUID.randomUUID().toString());
    await(1);
    when(http.get(contains("guidance"), any(), any()))
        .thenReturn(
            download(
                guide.replace("release.txt", "new.txt").getBytes(StandardCharsets.UTF_8),
                "https://www.bis.gov/guidance"));
    when(http.get(endsWith("new.txt"), any(), any()))
        .thenReturn(download(fixture(4), "https://media.bis.gov/new.txt"));
    collection.start("DPL", "owner@tradeops.test", UUID.randomUUID().toString());
    await(2);
    assertThat(repository.run(2).get("previous_url")).isEqualTo("https://www.bis.gov/release.txt");
    assertThat(repository.source("DPL").get("alert_code")).isEqualTo("SOURCE_URL_CHANGED");
    assertThat(repository.run(2).get("idempotent")).isEqualTo(true);
  }

  @Test
  void weeklyScheduleInitializesAndCatchesUpOnceWithIndependentSourceFailure() throws Exception {
    jdbc.update("UPDATE bis_sources SET next_scheduled=NULL");
    when(http.get(endsWith("release.txt"), any(), any()))
        .thenReturn(download(fixture(3), "https://media.bis.gov/release.txt"));
    when(http.get(endsWith("release.csv"), any(), any()))
        .thenThrow(new io.tradeops.error.OperationException("SOURCE_TEMPORARILY_UNAVAILABLE", 503));
    var scheduled =
        new BisCollectionService(
            repository,
            http,
            new BisDiscovery(),
            parser,
            audit,
            accounts,
            "https://www.bis.gov/guidance",
            "../artifacts/bis-fixture-storage",
            true,
            "0 0 9 * * MON",
            "Asia/Seoul");
    try {
      scheduled.initialize();
      var next =
          jdbc.queryForObject(
                  "SELECT next_scheduled FROM bis_sources WHERE code='DPL'",
                  java.time.OffsetDateTime.class)
              .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"));
      assertThat(next.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
      assertThat(next.getHour()).isEqualTo(9);
      jdbc.update("UPDATE bis_sources SET next_scheduled=CURRENT_TIMESTAMP-INTERVAL '20 days'");
      scheduled.tick();
      await(1);
      await(2);
      scheduled.tick();
      assertThat(repository.run(1).get("status")).isEqualTo("COMPLETED");
      assertThat(repository.run(2).get("status")).isEqualTo("FAILED");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bis_runs", Integer.class)).isEqualTo(2);
    } finally {
      scheduled.close();
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  void searchUsesPinnedVersionsAndPrivateHistory() throws Exception {
    save(5);
    var result = (Map<String, Object>) searchResult(query("노스라인 비콘", null, true), "reader-a");
    assertThat((List<?>) result.get("items")).isNotEmpty();
    Map<String, Long> versions = (Map<String, Long>) result.get("snapshots");
    assertThat(((Map<?, ?>) search.history("reader-b", 0, 20)).get("totalElements")).isEqualTo(0L);
    assertThatThrownBy(() -> search.deleteHistory("reader-b", 1L, "correlation-test"))
        .hasMessage("NOT_FOUND");
    long next = save(2);
    collection.approve(next, "owner@tradeops.test", UUID.randomUUID().toString());
    var pinned = (Map<String, Object>) searchResult(query("", versions, false), "reader-a");
    assertThat(pinned.get("totalElements")).isEqualTo(5L);
    search.deleteHistory("reader-a", 1L, "correlation-test");
  }

  @SuppressWarnings("unchecked")
  @Test
  void exactAliasTypoNegativeAndCsvFormulaCases() throws Exception {
    StringWriter out = new StringWriter();
    try (var csv = new CSVPrinter(out, CSVFormat.DEFAULT)) {
      csv.printRecord(
          "Source List",
          "Name",
          "Address",
          "Country",
          "Effective Date",
          "License Requirement",
          "License Policy",
          "Alternate Name",
          "Web Link");
      csv.printRecord(
          "EL",
          "FICTIONAL BEACON",
          "+FICTIONAL ADDRESS",
          "KR",
          "1/1/2026;2/1/2026",
          "FICTIONAL",
          "FICTIONAL",
          "EMBER, MERIDIAN;FICTIONAL SECOND ALIAS",
          "");
    }
    byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
    long id = repository.start("EL", "owner@tradeops.test", UUID.randomUUID().toString(), "MANUAL");
    repository.file(
        id,
        download(bytes, "https://media.bis.gov/release.csv"),
        "fixture.csv",
        BisParser.sha(bytes));
    repository.saveParsed(id, parser.parse("EL", bytes));
    var exact = (Map<String, Object>) searchResult(query("fictionalbeacon", null, false), "reader");
    assertThat(((List<Map<String, Object>>) exact.get("items")).get(0).get("priority"))
        .isEqualTo(0);
    var alias = (Map<String, Object>) searchResult(query("EMBER, MERIDIAN", null, false), "reader");
    assertThat(((List<Map<String, Object>>) alias.get("items")).get(0).get("priority"))
        .isEqualTo(1);
    var typo = (Map<String, Object>) searchResult(query("fictional becon", null, false), "reader");
    assertThat(((List<Map<String, Object>>) typo.get("items")).get(0).get("priority")).isEqualTo(3);
    assertThat(
            ((Map<?, ?>) searchResult(query("zzzz qqqq", null, false), "reader"))
                .get("totalElements"))
        .isEqualTo(0L);
    assertThat(
            new String(
                search.export(query("", null, false), "reader", UUID.randomUUID().toString()),
                StandardCharsets.UTF_8))
        .contains("'+FICTIONAL ADDRESS");
    assertThatThrownBy(
            () ->
                search.prepare(
                    new BisSearchService.Query("", "BASIC", "", "K", "score", 0, 20, null, false)))
        .hasMessage("INVALID_REQUEST");
  }

  @SuppressWarnings("unchecked")
  @Test
  void tenThousandRowsMeetSearchAndExportBudget() throws Exception {
    save(10000);
    var query = query("FICTIONAL MERIDIAN", null, false);
    for (int i = 0; i < 5; i++) searchResult(query, "benchmark");
    var times = Collections.synchronizedList(new ArrayList<Long>());
    var pool = java.util.concurrent.Executors.newFixedThreadPool(5);
    try {
      var tasks = new ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 100; i++)
        tasks.add(
            pool.submit(
                () -> {
                  long start = System.nanoTime();
                  searchResult(query, "benchmark");
                  times.add((System.nanoTime() - start) / 1000000);
                }));
      for (var task : tasks) task.get();
    } finally {
      pool.shutdown();
    }
    Collections.sort(times);
    long p95 = times.get(94);
    long start = System.nanoTime();
    byte[] exported =
        search.export(query("", null, false), "owner@tradeops.test", UUID.randomUUID().toString());
    long exportMs = (System.nanoTime() - start) / 1000000;
    assertThat(p95).isLessThan(1000);
    assertThat(exportMs).isLessThan(10000);
    assertThat(new String(exported, StandardCharsets.UTF_8).lines().count()).isEqualTo(10001);
    Path out = Path.of("../artifacts/bis-performance.json");
    Files.createDirectories(out.getParent());
    Files.writeString(
        out,
        "{\"rows\":10000,\"concurrency\":5,\"requests\":100,\"p95Ms\":"
            + p95
            + ",\"exportMs\":"
            + exportMs
            + "}");
  }

  BisSearchService.Query query(String q, Map<String, Long> versions, boolean history) {
    return new BisSearchService.Query(q, "HYBRID", "", "", "score", 0, 20, versions, history);
  }

  Object searchResult(BisSearchService.Query query, String actor) {
    return search.search(query, actor, UUID.randomUUID().toString());
  }

  @Test
  void parserUpgradeRevalidatesIdenticalBytesWithoutRewritingHistory() throws Exception {
    long oldRun = save(4);
    jdbc.update("UPDATE bis_runs SET parser_version='bis-csv-1',warnings=2 WHERE id=?", oldRun);
    Object oldSnapshot = repository.run(oldRun).get("snapshot_id");
    when(http.get(endsWith("release.txt"), any(), any()))
        .thenReturn(download(fixture(4), "https://media.bis.gov/release.txt"));
    collection.start("DPL", "owner@tradeops.test", UUID.randomUUID().toString());
    await(2);
    var current = repository.run(2);
    assertThat(current.get("status")).isEqualTo("COMPLETED");
    assertThat(current.get("parser_version")).isEqualTo(BisParser.VERSION);
    assertThat(current.get("snapshot_id")).isNotEqualTo(oldSnapshot);
    assertThat(current.get("warnings")).isEqualTo(0);
    assertThat(current.get("unchanged_rows")).isEqualTo(4);
    assertThat(repository.run(oldRun).get("warnings")).isEqualTo(2);
    verify(http).get(endsWith("release.txt"), isNull(), isNull());
  }

  void await(long id) throws Exception {
    for (int i = 0; i < 200; i++) {
      if (!repository.run(id).get("status").equals("RUNNING")) return;
      Thread.sleep(50);
    }
    fail("Collection timed out");
  }

  @Test
  @SuppressWarnings("unchecked")
  void recentTermsAreDistinctPrivateAndDeleteAllMatchingHistory() {
    for (String term :
        List.of(
            "",
            "FICTIONAL ONE",
            "FICTIONAL TWO",
            "FICTIONAL THREE",
            "FICTIONAL FOUR",
            "FICTIONAL FIVE",
            "FICTIONAL SIX",
            " fictional six ")) searchResult(query(term, null, true), "reader-a");
    searchResult(query("FICTIONAL SIX", null, true), "reader-b");
    var recent = (List<Map<String, Object>>) search.recentHistory("reader-a");
    assertThat(recent).hasSize(5);
    assertThat(recent.get(0).get("q")).isEqualTo("fictional six");
    assertThat(recent.stream().map(r -> r.get("q"))).doesNotContain("", "FICTIONAL ONE");
    long id = ((Number) recent.get(0).get("id")).longValue();
    assertThatThrownBy(() -> search.deleteRecentHistory("reader-b", id, "fixture-denied"))
        .hasMessage("NOT_FOUND");
    search.deleteRecentHistory("reader-a", id, "fixture-delete");
    var remaining = (List<Map<String, Object>>) search.recentHistory("reader-a");
    assertThat(remaining).hasSize(5);
    assertThat(remaining.stream().map(r -> r.get("q")))
        .contains("FICTIONAL ONE")
        .doesNotContain("FICTIONAL SIX", "fictional six");
    assertThat((List<?>) search.recentHistory("reader-b")).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM search_history WHERE username='reader-a'", Long.class))
        .isEqualTo(6L);
    search.deleteHistory("reader-a", null, "fixture-clear");
    assertThat((List<?>) search.recentHistory("reader-a")).isEmpty();
  }
}
