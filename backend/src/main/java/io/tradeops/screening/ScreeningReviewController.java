package io.tradeops.screening;

import io.tradeops.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/screening-reviews")
public class ScreeningReviewController {
    private final ScreeningReviewService service;

    public ScreeningReviewController(ScreeningReviewService service) {
        this.service = service;
    }

    @PostMapping
    public Result decide(@Valid @RequestBody Request request, Authentication auth, HttpServletRequest http) {
        String correlationId = http.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE).toString();
        service.record(request.transactionId(), request.watchlistExternalId(), request.matchScore(),
                request.disposition(), auth.getName(), correlationId);
        return new Result("RECORDED", correlationId);
    }

    public record Request(
            @NotBlank @Size(max = 100) String transactionId,
            @NotBlank @Size(max = 160) String watchlistExternalId,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal matchScore,
            @NotBlank @Pattern(regexp = "CONFIRMED_MATCH|CLEARED|NEEDS_FOLLOW_UP") String disposition) { }
    public record Result(String status, String correlationId) { }
}
