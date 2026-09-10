package io.tradeops.law;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.tradeops.account.*;
import io.tradeops.audit.AuditService;
import io.tradeops.error.OperationException;
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
class LawAdminIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @MockBean LawClient client;
  @MockBean AccountService accounts;
  @MockBean AuditService audit;
  static final String BASE = "/api/v1/law-admin";

  @BeforeEach
  void setup() throws Exception {
    when(accounts.get(anyString())).thenReturn(mock(AccountRepository.Account.class));
    doThrow(new OperationException("ACCESS_DENIED", 403)).when(accounts).requireOwner("reader");
    when(client.administration(anyString()))
        .thenReturn(
            mapper.readTree(
                "{\"items\":[],\"total\":0,\"articles\":[],\"revisions\":[],\"indexStatus\":\"stale\",\"articlesCount\":1,\"reindexEnabled\":true}"));
    when(client.administrationAction(anyString()))
        .thenReturn(
            mapper.readTree(
                "{\"status\":\"SUCCEEDED\",\"errorMessage\":\"private provider text\"}"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "status",
        "laws",
        "laws/1",
        "laws/1/revisions",
        "search-logs",
        "agent-traces",
        "ingestion-runs"
      })
  void readsRequireOwnerAndSession(String path) throws Exception {
    mvc.perform(get(BASE + "/" + path)).andExpect(status().isUnauthorized());
    mvc.perform(get(BASE + "/" + path).with(user("reader"))).andExpect(status().isForbidden());
    verifyNoInteractions(client);
    mvc.perform(get(BASE + "/" + path).with(user("owner"))).andExpect(status().isOk());
    verify(accounts).requireOwner("owner");
  }

  @Test
  void removesPrivateFieldsRecursively() throws Exception {
    when(client.administration("/api/admin/search-logs"))
        .thenReturn(
            mapper.readTree(
                """
{"total":1,"sourcePath":"private-path","items":[{"id":1,"requestId":"fixture",
"questionPreview":"private question","inputSummary":"private input","outputSummary":"private output",
"errorMessage":"secret-error","status":"FAILED","citedArticleCount":0}]}
"""));
    mvc.perform(get(BASE + "/search-logs").with(user("owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].status").value("FAILED"))
        .andExpect(jsonPath("$.sourcePath").doesNotExist())
        .andExpect(jsonPath("$.items[0].questionPreview").doesNotExist())
        .andExpect(jsonPath("$.items[0].inputSummary").doesNotExist())
        .andExpect(jsonPath("$.items[0].outputSummary").doesNotExist())
        .andExpect(jsonPath("$.items[0].errorMessage").doesNotExist());
  }

  @Test
  void rejectsInvalidFiltersAndRoutesBeforeUpstream() throws Exception {
    for (String path :
        new String[] {
          "laws?page=0",
          "laws/0",
          "agent-traces?requestId=../private",
          "unknown",
          "laws?q=" + "a".repeat(201)
        })
      mvc.perform(get(BASE + "/" + path).with(user("owner"))).andExpect(status().isBadRequest());
    verifyNoInteractions(client);
  }

  @ParameterizedTest
  @ValueSource(strings = {"sync-source", "ingest-local", "reindex", "provider-smoke-test"})
  void actionsRequireOwnerCsrfAndAreAudited(String action) throws Exception {
    String path = BASE + "/actions/" + action;
    mvc.perform(
            post(path).with(user("owner")).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path)
                .with(user("reader"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(client);
    mvc.perform(
            post(path)
                .with(user("owner"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.errorMessage").doesNotExist());
    verify(client).administrationAction(action);
    verify(audit)
        .record(
            eq("owner"), eq("LAW_ADMIN_STARTED"), eq("LAW_ADMIN"), isNull(), anyString(), any());
    verify(audit)
        .record(
            eq("owner"), eq("LAW_ADMIN_FINISHED"), eq("LAW_ADMIN"), isNull(), anyString(), any());
  }

  @Test
  void noArbitraryPathsAndDisabledReindex() throws Exception {
    mvc.perform(
            post(BASE + "/actions/sync-source")
                .with(user("owner"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"localDir\":\"private-path\"}"))
        .andExpect(status().isBadRequest());
    when(client.administration("/api/admin/status"))
        .thenReturn(mapper.readTree("{\"reindexEnabled\":false}"));
    mvc.perform(
            post(BASE + "/actions/reindex")
                .with(user("owner"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict());
    verify(client, never()).administrationAction(anyString());
  }
}
