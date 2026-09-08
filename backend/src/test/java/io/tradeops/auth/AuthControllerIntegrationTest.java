package io.tradeops.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.tradeops.user.AppUser;
import io.tradeops.user.Role;
import io.tradeops.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        users.save(new AppUser("operator@tradeops.test", passwordEncoder.encode("portfolio-demo"), Role.OPERATOR, true));
        users.save(new AppUser("viewer@tradeops.test", passwordEncoder.encode("portfolio-demo"), Role.VIEWER, true));
        users.save(new AppUser("admin@tradeops.test", passwordEncoder.encode("portfolio-demo"), Role.ADMIN, true));
    }

    @Test
    void operatorCanAuthenticateAndReadOwnRole() throws Exception {
        String token = login("operator@tradeops.test", "portfolio-demo");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operator@tradeops.test"))
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    void viewerCannotAccessAdminStatus() throws Exception {
        String token = login("viewer@tradeops.test", "portfolio-demo");
        mockMvc.perform(get("/api/v1/admin/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanAccessAdminStatus() throws Exception {
        String token = login("admin@tradeops.test", "portfolio-demo");
        mockMvc.perform(get("/api/v1/admin/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("admin"));
    }

    @Test
    void loginRejectsUnexpectedRequestFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator@tradeops.test\",\"password\":\"portfolio-demo\",\"unexpected\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void securityDenialsRetainCorrelationId() throws Exception {
        String correlationId = "auth-denied-0001";
        mockMvc.perform(get("/api/v1/auth/me").header("X-Correlation-Id", correlationId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", correlationId))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
        String token = login("viewer@tradeops.test", "portfolio-demo");
        mockMvc.perform(get("/api/v1/admin/status").header("Authorization", "Bearer " + token)
                        .header("X-Correlation-Id", correlationId))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-Id", correlationId))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }

    @Test
    void frameworkClientErrorsKeepTheirHttpStatus() throws Exception {
        String token = login("operator@tradeops.test", "portfolio-demo");
        mockMvc.perform(multipart("/api/v1/imports").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/v1/imports").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/screening-reviews").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
