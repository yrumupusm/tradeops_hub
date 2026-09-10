package io.tradeops.bis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tradeops.audit.AuditService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.junit.jupiter.api.Test;

class BisExportTest {
  @Test
  void exportsOriginalFieldsWithKoreanHeadersAndSafeCsv() throws Exception {
    var repository = mock(BisSearchRepository.class);
    var bis = mock(BisRepository.class);
    var mapper = new ObjectMapper();
    when(bis.snapshot(7L)).thenReturn(Map.of("source_code", "DPL"));
    when(bis.snapshot(8L)).thenReturn(Map.of("source_code", "EL"));
    var dpl =
        Map.of(
            "Name",
            "FICTIONAL, \"BEACON\"",
            "Street_Address",
            "+FICTIONAL ADDRESS",
            "Country",
            "FICTIONAL COUNTRY",
            "Effective_Date",
            "1/1/2026",
            "Expiration_Date",
            "1/1/2030",
            "City",
            "FICTIONAL CITY",
            "Action",
            "first line\nsecond line");
    var el =
        Map.of(
            "Name",
            "FICTIONAL MERIDIAN",
            "Alternate Name",
            "FICTIONAL ALIAS;SECOND ALIAS",
            "Effective Date",
            "1/1/2026;2/1/2026",
            "Date Lifted/Waived/Expired",
            "3/1/2026",
            "License Requirement",
            "@FICTIONAL",
            "License Policy",
            "FICTIONAL POLICY");
    when(repository.search(any(), anyList(), eq(1000), eq(0), eq(true)))
        .thenReturn(
            new BisSearchRepository.Result(
                List.of(
                    Map.of("source_code", "DPL", "raw_json", mapper.writeValueAsString(dpl)),
                    Map.of("source_code", "EL", "raw_json", mapper.writeValueAsString(el))),
                2));
    var service = new BisSearchService(repository, bis, mapper, mock(AuditService.class));
    var query =
        new BisSearchService.Query(
            "", "HYBRID", "", "", "score", 4, 1, Map.of("DPL", 7L, "EL", 8L), false);
    String result =
        new String(
            service.export(query, "fictional-user", "fixture-correlation"), StandardCharsets.UTF_8);
    assertThat(result).startsWith("\uFEFF");
    try (var csv =
        CSVParser.parse(result.substring(1), CSVFormat.DEFAULT.builder().setHeader().get())) {
      assertThat(csv.getHeaderNames())
          .startsWith("출처", "이름", "다른 이름", "국가", "주소")
          .doesNotContain("일치 방식", "유사도", "중복 행 수", "기준 버전", "원본 행");
      var rows = csv.getRecords();
      assertThat(rows).hasSize(2);
      assertThat(rows.get(0).get("이름")).isEqualTo(dpl.get("Name"));
      assertThat(rows.get(0).get("주소")).isEqualTo("'+FICTIONAL ADDRESS");
      assertThat(rows.get(0).get("국가")).isEqualTo("FICTIONAL COUNTRY");
      assertThat(rows.get(0).get("변경 설명")).isEqualTo(dpl.get("Action"));
      assertThat(rows.get(0).get("만료일")).isEqualTo("1/1/2030");
      assertThat(rows.get(0).get("허가 요건")).isEmpty();
      assertThat(rows.get(1).get("발효일")).isEqualTo(el.get("Effective Date"));
      assertThat(rows.get(1).get("다른 이름")).isEqualTo(el.get("Alternate Name"));
      assertThat(rows.get(1).get("해제·면제·만료일")).isEqualTo("3/1/2026");
      assertThat(rows.get(1).get("허가 요건")).isEqualTo("'@FICTIONAL");
      assertThat(rows.get(1).get("만료일")).isEmpty();
    }
  }
}
