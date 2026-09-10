package io.tradeops.bis;

import io.tradeops.error.OperationException;
import java.io.*;
import java.nio.charset.*;
import java.security.*;
import java.time.LocalDate;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;

@Component
public class BisParser {
  public static final String VERSION = "bis-csv-2";

  public record Issue(int row, String code, String severity) {}

  public record Row(
      int number,
      int occurrence,
      String hash,
      Map<String, String> raw,
      String name,
      String country,
      String countryLabel,
      String address,
      List<String> aliases,
      Map<String, List<String>> dates,
      boolean warning) {}

  public record Parsed(List<Row> rows, List<Issue> issues, int totalRows) {}

  private static final List<String> DPL =
      List.of(
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
          "FR_Citation");
  private static final List<String> EL =
      List.of(
          "Source List",
          "Name",
          "Address",
          "Country",
          "Effective Date",
          "License Requirement",
          "License Policy",
          "Alternate Name",
          "Web Link");

  public Parsed parse(String source, byte[] bytes) {
    if (!Set.of("DPL", "EL").contains(source)) throw new OperationException("SOURCE_INVALID", 400);
    String text;
    try {
      text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(java.nio.ByteBuffer.wrap(bytes))
              .toString();
    } catch (CharacterCodingException e) {
      throw new OperationException("ENCODING_INVALID", 422);
    }
    if (text.startsWith("\uFEFF")) text = text.substring(1);
    var rows = new ArrayList<Row>();
    var issues = new ArrayList<Issue>();
    var occurrences = new HashMap<String, Integer>();
    int total = 0;
    try (var csv =
        CSVParser.parse(
            text,
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .get())) {
      var headers = csv.getHeaderNames();
      if (!headers.containsAll(source.equals("DPL") ? DPL : EL)
          || new HashSet<>(headers).size() != headers.size())
        throw new OperationException("SCHEMA_CHANGED", 422);
      for (CSVRecord record : csv) {
        total++;
        int number = (int) record.getRecordNumber() + 1;
        if (!record.isConsistent()) {
          issues.add(new Issue(number, "COLUMN_COUNT_INVALID", "ERROR"));
          continue;
        }
        Map<String, String> raw = new LinkedHashMap<>();
        for (String h : headers) raw.put(h, record.get(h));
        String name = raw.getOrDefault("Name", "");
        if (name.isBlank()) {
          issues.add(new Issue(number, "NAME_REQUIRED", "ERROR"));
          continue;
        }
        String originalCountry = raw.getOrDefault("Country", "").strip();
        String country = countryCode(originalCountry);
        var dates = new LinkedHashMap<String, List<String>>();
        for (String field :
            source.equals("DPL")
                ? List.of("Effective_Date", "Expiration_Date", "Last_Update")
                : List.of("Effective Date", "Date Lifted/Waived/Expired")) {
          String value = raw.getOrDefault(field, "");
          List<String> parsedDates = new ArrayList<>();
          if (!value.isBlank())
            for (String part : value.split("[;,]")) {
              try {
                parsedDates.add(
                    LocalDate.parse(
                            part.trim(),
                            DateTimeFormatter.ofPattern("M/d/uuuu")
                                .withResolverStyle(ResolverStyle.STRICT))
                        .toString());
              } catch (DateTimeParseException e) {
                // Optional conversion only; retain the source value in raw without an issue.
              }
            }
          dates.put(field, parsedDates);
        }
        String address =
            source.equals("DPL")
                ? String.join(
                    ", ",
                    List.of(
                            raw.getOrDefault("Street_Address", ""),
                            raw.getOrDefault("City", ""),
                            raw.getOrDefault("State", ""),
                            raw.getOrDefault("Postal_Code", ""))
                        .stream()
                        .filter(v -> !v.isBlank())
                        .toList())
                : raw.getOrDefault("Address", "");
        List<String> aliases =
            Arrays.stream(raw.getOrDefault("Alternate Name", "").split(";"))
                .map(String::strip)
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();
        String canonical =
            new TreeMap<>(raw)
                .entrySet().stream()
                    .map(
                        e ->
                            e.getKey().length()
                                + ":"
                                + e.getKey()
                                + e.getValue().length()
                                + ":"
                                + e.getValue())
                    .collect(java.util.stream.Collectors.joining());
        String hash = sha(canonical.getBytes(StandardCharsets.UTF_8));
        int occurrence = occurrences.merge(hash, 1, Integer::sum);
        rows.add(
            new Row(
                number,
                occurrence,
                hash,
                raw,
                name,
                country,
                originalCountry,
                address,
                aliases,
                dates,
                false));
      }
    } catch (OperationException e) {
      throw e;
    } catch (Exception e) {
      throw new OperationException("CSV_INVALID", 422);
    }
    if (total == 0) throw new OperationException("EMPTY_SOURCE", 422);
    return new Parsed(rows, issues, total);
  }

  public static String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String countryCode(String value) {
    if (value.matches("[A-Za-z]{2}")) return value.toUpperCase(Locale.ROOT);
    Map<String, String> alternate =
        Map.of(
            "Russia",
            "RU",
            "Vietnam",
            "VN",
            "Iran",
            "IR",
            "South Korea",
            "KR",
            "North Korea",
            "KP",
            "Syria",
            "SY",
            "Taiwan",
            "TW",
            "Turkey",
            "TR",
            "Czech Republic",
            "CZ");
    if (alternate.containsKey(value)) return alternate.get(value);
    for (String code : Locale.getISOCountries()) {
      Locale l = new Locale("", code);
      if (l.getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(value)) return code;
    }
    return "";
  }
}
