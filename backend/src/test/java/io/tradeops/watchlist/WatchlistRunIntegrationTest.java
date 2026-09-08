package io.tradeops.watchlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tradeops.user.AppUser;
import io.tradeops.user.Role;
import io.tradeops.user.UserRepository;
import io.tradeops.watchlist.persistence.WatchlistChangeRecordRepository;
import io.tradeops.watchlist.persistence.WatchlistSnapshotRecordRepository;
import io.tradeops.watchlist.persistence.WatchlistSnapshotRepository;
import io.tradeops.watchlist.persistence.WatchlistSourceRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WatchlistRunIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private WatchlistChangeRecordRepository changes;
    @Autowired private WatchlistSnapshotRecordRepository records;
    @Autowired private WatchlistSnapshotRepository snapshots;
    @Autowired private WatchlistSourceRunRepository runs;

    @BeforeEach
    void setUp() {
        changes.deleteAll(); records.deleteAll(); snapshots.deleteAll(); runs.deleteAll(); users.deleteAll();
        users.save(new AppUser("operator@tradeops.test", passwordEncoder.encode("portfolio-demo"), Role.OPERATOR, true));
        users.save(new AppUser("viewer@tradeops.test", passwordEncoder.encode("portfolio-demo"), Role.VIEWER, true));
    }

    @Test
    void operatorCreatesSnapshotsComparesVersionsAndRerunsIdempotently() throws Exception {
        String token = login("operator@tradeops.test");
        MvcResult first = run(token, "2026-01-A", "XML")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.idempotent").value(false)).andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.addedCount").value(4)).andExpect(jsonPath("$.changedCount").value(0))
                .andExpect(header().exists("X-Correlation-Id")).andReturn();
        long firstSnapshotId = objectMapper.readTree(first.getResponse().getContentAsString()).get("snapshotId").asLong();

        MvcResult second = run(token, "2026-02-B", "CSV")
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotent").value(false))
                .andExpect(jsonPath("$.addedCount").value(1)).andExpect(jsonPath("$.changedCount").value(1))
                .andExpect(jsonPath("$.removedCount").value(1)).andExpect(jsonPath("$.unchangedCount").value(2)).andReturn();
        long secondSnapshotId = objectMapper.readTree(second.getResponse().getContentAsString()).get("snapshotId").asLong();

        run(token, "2026-02-B", "CSV").andExpect(status().isOk()).andExpect(jsonPath("$.idempotent").value(true))
                .andExpect(jsonPath("$.snapshotId").value(secondSnapshotId));
        org.junit.jupiter.api.Assertions.assertEquals(2, snapshots.count());
        org.junit.jupiter.api.Assertions.assertNotEquals(firstSnapshotId, secondSnapshotId);
    }

    @Test
    void viewerCannotTriggerAWatchlistRun() throws Exception {
        run(login("viewer@tradeops.test"), "2026-01-A", "XML").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unknownFixtureProducesSafeFailedRun() throws Exception {
        run(login("operator@tradeops.test"), "2099-01-Z", "XML").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.safeErrorCode").value("FIXTURE_NOT_FOUND"));
        org.junit.jupiter.api.Assertions.assertEquals(0, snapshots.count());
    }

    private org.springframework.test.web.servlet.ResultActions run(String token, String version, String format) throws Exception {
        return mockMvc.perform(post("/api/v1/watchlist/runs").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fixtureVersion\":\"" + version + "\",\"format\":\"" + format + "\"}"));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"portfolio-demo\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}