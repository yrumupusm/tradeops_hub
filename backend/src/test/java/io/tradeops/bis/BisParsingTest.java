package io.tradeops.bis;

import static org.assertj.core.api.Assertions.*;

import io.tradeops.error.OperationException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.junit.jupiter.api.Test;

class BisParsingTest {
  static final String[] DPL = {
    "Name",
    "Street_Address",
    "City",
    "State",
    "Country",
    "Postal_Code",
    "Effective_Date",
    "Expiration_Date",
    "Standard_Order",
    "Last_Update",
    "Action",
    "FR_Citation"
  };

  byte[] fixture(List<List<String>> rows) throws Exception {
    StringWriter text = new StringWriter();
    try (CSVPrinter csv = new CSVPrinter(text, CSVFormat.DEFAULT)) {
      csv.printRecord((Object[]) DPL);
      for (var row : rows) csv.printRecord(row);
    }
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }

  List<String> row(String name) {
    return Arrays.asList(
        name,
        "1 Fictional Road\nUnit A",
        "Example City",
        "",
        " KR ",
        "00000",
        "1/2/2026",
        "",
        "Y",
        "2/3/2026",
        "",
        "FICTIONAL");
  }

  @Test
  void preservesQuotedRawRowsAndDuplicates() throws Exception {
    var parser = new BisParser();
    var result =
        parser.parse(
            "DPL", fixture(List.of(row("  FICTIONAL, BEACON  "), row("  FICTIONAL, BEACON  "))));
    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().get(0).name()).isEqualTo("  FICTIONAL, BEACON  ");
    assertThat(result.rows().get(1).occurrence()).isEqualTo(2);
    assertThat(result.rows().get(0).country()).isEqualTo("KR");
    assertThat(result.rows().get(0).dates().get("Effective_Date")).containsExactly("2026-01-02");
  }

  @Test
  void missingNamesRemainReviewable() throws Exception {
    var result = new BisParser().parse("DPL", fixture(List.of(row(""), row("FICTIONAL ONE"))));
    assertThat(result.totalRows()).isEqualTo(2);
    assertThat(result.rows()).hasSize(1);
    assertThat(result.issues()).anyMatch(i -> i.code().equals("NAME_REQUIRED") && i.row() == 2);
  }

  @Test
  void optionalDatesAndCountriesAreNotValidationIssues() throws Exception {
    var row = new ArrayList<>(row("FICTIONAL TWO"));
    row.set(4, "");
    row.set(6, "unknown date");
    var result = new BisParser().parse("DPL", fixture(List.of(row)));
    assertThat(result.rows()).hasSize(1);
    assertThat(result.issues()).isEmpty();
    assertThat(result.rows().get(0).warning()).isFalse();
    assertThat(result.rows().get(0).raw().get("Effective_Date")).isEqualTo("unknown date");
    assertThat(result.rows().get(0).raw().get("Country")).isEmpty();
  }

  @Test
  void rejectsHtmlAndChangedSchemas() {
    assertThatThrownBy(
            () ->
                new BisParser().parse("DPL", "<html>error</html>".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(OperationException.class)
        .hasMessage("SCHEMA_CHANGED");
  }

  @Test
  void discoveryToleratesWordingAndRelativeFilenameChange() {
    String page =
        "<h4>Denied Persons List</h4><p>Get the newest text <a"
            + " href='/new-release.txt'>download</a>.</p><h4>Entity List</h4><p><a"
            + " href='/release-2026.csv'>CSV</a></p>";
    assertThat(new BisDiscovery().discover(page, "https://media.bis.gov/info", "DPL"))
        .isEqualTo("https://media.bis.gov/new-release.txt");
    assertThat(new BisDiscovery().discover(page, "https://media.bis.gov/info", "EL"))
        .endsWith("release-2026.csv");
  }

  @Test
  void discoveryRejectsAmbiguousLinks() {
    assertThatThrownBy(
            () ->
                new BisDiscovery()
                    .discover(
                        "<h4>Entity List</h4><p><a href='/a.csv'>CSV</a><a"
                            + " href='/b.csv'>CSV</a></p>",
                        "https://media.bis.gov/",
                        "EL"))
        .hasMessage("DOWNLOAD_LINK_AMBIGUOUS");
  }

  @Test
  void normalizationRetainsOriginalIndependentForms() {
    assertThat(NameNormalizer.normalize("  FÍCTIONAL   Beacon, Inc. "))
        .isEqualTo("fictional beacon inc");
    assertThat(NameNormalizer.compact("NORTH LINE")).isEqualTo("northline");
    assertThat(NameNormalizer.tokens("Beacon Northline")).isEqualTo("beacon northline");
    assertThat(NameNormalizer.variants("노스라인 비콘")).contains("northline beacon");
  }

  @Test
  void unsafeOriginsRejected() {
    var client = new BisHttpClient("bis.gov,media.bis.gov");
    assertThatThrownBy(() -> client.allowed("http://media.bis.gov/a"))
        .hasMessage("SOURCE_URL_NOT_ALLOWED");
    assertThatThrownBy(() -> client.allowed("https://example.org/a"))
        .hasMessage("SOURCE_URL_NOT_ALLOWED");
  }

  @Test
  void oversizedResponseCancelsBeforeBufferingEverything() {
    var subscriber = new BisHttpClient.LimitedBody(4);
    var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    subscriber.onSubscribe(
        new java.util.concurrent.Flow.Subscription() {
          public void request(long n) {}

          public void cancel() {
            cancelled.set(true);
          }
        });
    subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[5])));
    assertThat(cancelled.get()).isTrue();
    assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
        .hasCauseInstanceOf(OperationException.class);
  }
}
