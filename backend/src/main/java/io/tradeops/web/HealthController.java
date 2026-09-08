package io.tradeops.web;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    @GetMapping("/health")
    public HealthResponse health(HttpServletRequest request) {
        Object correlationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return new HealthResponse("ok", "tradeops-api", Instant.now(), correlationId == null ? "" : correlationId.toString());
    }

    public record HealthResponse(String status, String service, Instant checkedAt, String correlationId) { }
}
