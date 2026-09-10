package io.tradeops.bis;

import java.text.Normalizer;
import java.util.*;

public final class NameNormalizer {
  private NameNormalizer() {}

  public static String normalize(String text) {
    return Normalizer.normalize(Objects.toString(text, ""), Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  public static String compact(String value) {
    return normalize(value).replace(" ", "");
  }

  public static String tokens(String value) {
    return Arrays.stream(normalize(value).split(" "))
        .filter(s -> !s.isEmpty())
        .sorted()
        .collect(java.util.stream.Collectors.joining(" "));
  }

  public static List<String> variants(String query) {
    var names = new LinkedHashSet<String>();
    names.add(normalize(query));
    Map<String, String> dictionary =
        Map.ofEntries(
            Map.entry("테크놀로지", "technology"),
            Map.entry("테크놀로지스", "technologies"),
            Map.entry("인터내셔널", "international"),
            Map.entry("트레이딩", "trading"),
            Map.entry("일렉트로닉스", "electronics"),
            Map.entry("뱅크", "bank"),
            Map.entry("노스라인 비콘", "northline beacon"),
            Map.entry("엠버 메리디언", "ember meridian"));
    String converted = query;
    for (var entry :
        dictionary.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
            .toList()) converted = converted.replace(entry.getKey(), entry.getValue());
    names.add(normalize(converted));
    return List.copyOf(names);
  }
}
