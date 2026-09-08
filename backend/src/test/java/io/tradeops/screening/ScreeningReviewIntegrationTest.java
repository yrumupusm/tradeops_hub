package io.tradeops.screening;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tradeops.user.AppUser;
import io.tradeops.user.Role;
import io.tradeops.user.UserRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ScreeningReviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ScreeningReviewService service;
    @MockitoSpyBean ScreeningReviewRepository repository;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from screening_reviews");
        jdbc.update("delete from audit_events");
        for (Role role : Role.values()) {
            String username = role.name().toLowerCase() + "@review.test";
            if (users.findByUsernameIgnoreCase(username).isEmpty()) {
                users.save(new AppUser(username, encoder.encode("local-review-test"), role, true));
            }
        }
    }

    @Test
    void operatorAndAdminRecordReviewsWithLinkedAuditEvents() throws Exception {
        for (String role : new String[]{"operator", "admin"}) {
            mvc.perform(post("/api/v1/screening-reviews").header("Authorization", "Bearer " + token(role))
                    .header("X-Correlation-Id", "review-" + role + "-0001").contentType("application/json").content(body("CLEARED")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECORDED"));
        }
        assertEquals(2, jdbc.queryForObject("select count(*) from screening_reviews", Integer.class));
        assertEquals(2, jdbc.queryForObject("""
                select count(*) from screening_reviews r join audit_events a
                on a.entity_id=cast(r.id as varchar) and a.correlation_id=r.correlation_id
                where a.actor_username=r.actor_username and r.created_at is not null
                and a.entity_type='SCREENING_REVIEW' and a.event_type='SCREENING_REVIEW_RECORDED'
                """, Integer.class));
    }

    @Test
    void viewerAndAnonymousCannotWrite() throws Exception {
        mvc.perform(post("/api/v1/screening-reviews").header("Authorization", "Bearer " + token("viewer"))
                .contentType("application/json").content(body("CLEARED")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/v1/screening-reviews").contentType("application/json").content(body("CLEARED")))
                .andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("select count(*) from screening_reviews", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_events", Integer.class));
    }

    @Test
    void missingOrInvalidDispositionAndOutOfRangeScoreAreRejected() throws Exception {
        String token = token("operator");
        for (String invalid : new String[]{
                body("CLEARED").replace("\"CLEARED\"", "null"),
                body("UNSUPPORTED"),
                body("CLEARED").replace("0.75", "1.01")}) {
            mvc.perform(post("/api/v1/screening-reviews").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from screening_reviews", Integer.class));
    }

    @Test
    void auditFailureRollsBackTheReview() {
        doThrow(new DataAccessResourceFailureException("SIMULATED_AUDIT_FAILURE"))
                .when(repository).recordAudit(anyLong(), anyString(), anyString());
        assertThrows(DataAccessResourceFailureException.class, () -> service.record(
                "TX-REVIEW-001", "FCP-1001", new BigDecimal("0.75"), "CLEARED",
                "operator@review.test", "review-rollback-0001"));
        assertEquals(0, jdbc.queryForObject("select count(*) from screening_reviews", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_events", Integer.class));
    }

    @Test
    void persistenceFailureReturnsSafeErrorWithCorrelationId() throws Exception {
        doThrow(new DataAccessResourceFailureException("SENSITIVE_PROVIDER_EXCEPTION"))
                .when(repository).recordAudit(anyLong(), anyString(), anyString());
        var result = mvc.perform(post("/api/v1/screening-reviews")
                .header("Authorization", "Bearer " + token("operator"))
                .header("X-Correlation-Id", "review-failure-0001")
                .contentType("application/json").content(body("CLEARED")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PERSISTENCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.correlationId").value("review-failure-0001"))
                .andExpect(jsonPath("$.trace").doesNotExist()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("SENSITIVE_PROVIDER_EXCEPTION"));
        assertEquals(0, jdbc.queryForObject("select count(*) from screening_reviews", Integer.class));
    }

    private String body(String disposition) throws Exception {
        return json.writeValueAsString(Map.of("transactionId", "TX-REVIEW-001", "watchlistExternalId", "FCP-1001",
                "matchScore", new BigDecimal("0.75"), "disposition", disposition));
    }

    private String token(String role) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", role + "@review.test", "password", "local-review-test"))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
