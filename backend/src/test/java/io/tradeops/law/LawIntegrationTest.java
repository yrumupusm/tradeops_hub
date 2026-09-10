package io.tradeops.law;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import io.tradeops.account.*;
import io.tradeops.audit.AuditService;
import io.tradeops.error.OperationException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LawIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @MockBean LawClient client;
  @MockBean AccountService accounts;
  @MockBean AuditService audit;
  private static final String PATH = "/api/v1/law-search/ask";
  static final String ID = "a22b4a70-29db-4e0e-b458-7d8a5b8b5e22";

  @BeforeEach
  void setup() {
    var account = mock(AccountRepository.Account.class);
    when(accounts.get(anyString())).thenReturn(account);
  }

  JsonNode response(String status, boolean citation, int retrieved) throws Exception {
    return mapper.readTree(
        """
        {"status":"%s","reasoning":"가상 근거 안내","disclaimer":"가상 확인 안내",
         "followUpQuestions":["가상 추가 질문"],"effectiveBasis":{"snapshotVersion":"fixture",
         "sourcePath":"/private/fixture","indexedAt":null,"sourceVersion":null,"asOf":null},
         "interpretation":{"private":"hidden"},"confidence":0.8,"candidateLaws":[],
         "errorMessage":"provider-secret-error",
         "diagnostics":{"requestId":"%s","retrievalStats":{"retrieved":%d}},"citedArticles":%s}
        """
            .formatted(
                status,
                ID,
                retrieved,
                citation
                    ? """
[{"articleId":2,"lawId":1,"lawTitle":"가상 법령","articleNumber":"제1조","articleTitle":null,
  "content":"<script>fictional</script> **원문**","reason":"score=0.9","previousArticleId":1,
  "historicalEntries":[{"articleId":1,"articleNumber":"제1조","content":"가상 과거 본문"}]}]
"""
                    : "[]"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"OK", "LOW_CONFIDENCE", "INSUFFICIENT_INFO", "FAILED"})
  void mapsStatesAndAuditsWithoutQuestion(String state) throws Exception {
    when(client.ask(any())).thenReturn(response(state, true, 1));
    var result =
        mvc.perform(
                post(PATH)
                    .with(user("reader"))
                    .with(csrf())
                    .header("X-Correlation-Id", "law-fixture-correlation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"question\":\"PRIVATE FICTIONAL"
                            + " QUESTION\",\"researchAreas\":[\"STRATEGIC_GOODS\",\"DEFENSE_MATERIALS\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(state))
            .andExpect(jsonPath("$.requestId").value(ID))
            .andExpect(jsonPath("$.effectiveBasis.sourcePath").doesNotExist())
            .andExpect(jsonPath("$.diagnostics").doesNotExist())
            .andExpect(jsonPath("$.confidence").doesNotExist())
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("provider-secret-error", "score=0.9", "/private/fixture");
    var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
    verify(audit)
        .record(
            eq("reader"),
            eq("LAW_SEARCH_EXECUTED"),
            eq("LAW_SEARCH"),
            isNull(),
            eq("law-fixture-correlation"),
            captor.capture());
    assertThat(mapper.writeValueAsString(captor.getValue()))
        .contains(ID, "/private/fixture")
        .doesNotContain("PRIVATE FICTIONAL QUESTION");
    if (!state.equals("FAILED"))
      assertThat(result.getResponse().getContentAsString()).contains("historicalEntries");
  }

  @ParameterizedTest
  @ValueSource(strings = {"OK", "LOW_CONFIDENCE"})
  void rejectsMissingCitations(String state) throws Exception {
    when(client.ask(any())).thenReturn(response(state, false, 1));
    mvc.perform(
            post(PATH)
                .with(user("reader"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"fixture\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("LAW_INVALID_RESPONSE"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"question\":\" \"}",
        "{\"question\":\"fixture\",\"asOf\":\"2026-02-30\"}",
        "{\"question\":\"fixture\",\"researchAreas\":[\"WRONG\"]}",
        "{\"question\":\"fixture\",\"userId\":1}"
      })
  void rejectsInvalidInput(String input) throws Exception {
    mvc.perform(
            post(PATH)
                .with(user("reader"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(input))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(client);
  }

  @Test
  void limitsQuestionAndAcceptsOptionalAreas() throws Exception {
    mvc.perform(
            post(PATH)
                .with(user("reader"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("question", "a".repeat(4001)))))
        .andExpect(status().isBadRequest());
    when(client.ask(any())).thenReturn(response("INSUFFICIENT_INFO", false, 0));
    for (var areas :
        List.of(
            List.of(),
            List.of("STRATEGIC_GOODS"),
            List.of("DEFENSE_MATERIALS"),
            List.of("STRATEGIC_GOODS", "DEFENSE_MATERIALS"))) {
      mvc.perform(
              post(PATH)
                  .with(user("reader"))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          Map.of("question", "fixture", "researchAreas", areas))))
          .andExpect(status().isOk());
    }
  }

  @Test
  void enforcesSessionCsrfAndPasswordState() throws Exception {
    mvc.perform(
            post(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"fixture\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post(PATH)
                .with(user("reader"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"fixture\"}"))
        .andExpect(status().isForbidden());
    var forced = mock(AccountRepository.Account.class);
    when(forced.mustChangePassword()).thenReturn(true);
    when(accounts.get("reader")).thenReturn(forced);
    mvc.perform(get("/api/v1/law-search/articles/2/history").with(user("reader")))
        .andExpect(status().isForbidden());
    when(accounts.get("reader")).thenThrow(new OperationException("AUTHENTICATION_REQUIRED", 401));
    mvc.perform(get("/api/v1/law-search/articles/2/history").with(user("reader")))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(client);
  }

  @Test
  void preservesHistoryAndComparisonAndSafeFailure() throws Exception {
    when(client.history(2))
        .thenReturn(
            mapper.readTree(
                "{\"lawTitle\":\"가상"
                    + " 법령\",\"entries\":[{\"articleId\":1,\"articleNumber\":\"제1조\",\"content\":\"가상"
                    + " 과거 본문\"}]}"));
    mvc.perform(get("/api/v1/law-search/articles/2/history").with(user("reader")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[0].articleId").value(1));
    when(client.diff(2, 1))
        .thenReturn(
            mapper.readTree(
                "{\"articleIdA\":2,\"articleIdB\":1,\"contentA\":\"new\",\"contentB\":\"old\",\"contentHashEqual\":false}"));
    mvc.perform(get("/api/v1/law-search/articles/2/diff?compareWith=1").with(user("reader")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentB").value("old"));
    mvc.perform(get("/api/v1/law-search/articles/0/history").with(user("reader")))
        .andExpect(status().isBadRequest());
    when(client.ask(any())).thenThrow(new OperationException("LAW_TIMEOUT", 504));
    mvc.perform(
            post(PATH)
                .with(user("reader"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"fixture\"}"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("LAW_TIMEOUT"));
  }
}
