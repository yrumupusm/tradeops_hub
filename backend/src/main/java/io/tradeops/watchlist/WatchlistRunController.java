package io.tradeops.watchlist;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.tradeops.watchlist.domain.WatchlistPayload;
import io.tradeops.watchlist.service.WatchlistReadService;
import io.tradeops.watchlist.service.WatchlistUpdateService;
import io.tradeops.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/watchlist")
public class WatchlistRunController {
 private final WatchlistUpdateService updateService; private final WatchlistReadService readService;
 public WatchlistRunController(WatchlistUpdateService updateService, WatchlistReadService readService) { this.updateService=updateService;this.readService=readService; }
 @PostMapping("/runs") public WatchlistUpdateService.RunSummary run(@Valid @RequestBody StartRunRequest request, Authentication auth,HttpServletRequest req) { return updateService.run(request.fixtureVersion(),request.format(),auth.getName(),req.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE).toString()); }
 @GetMapping("/runs") public WatchlistReadService.PageResponse<WatchlistReadService.RunView> runs(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return readService.runs(page,size); }
 @GetMapping("/entities") public WatchlistReadService.PageResponse<WatchlistReadService.EntityView> entities(@RequestParam(required=false) String provider,@RequestParam(required=false) String search,@RequestParam(required=false) String country,@RequestParam(required=false) String status,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return readService.entities(provider,search,country,status,page,size); }
 @GetMapping("/changes") public WatchlistReadService.PageResponse<WatchlistReadService.ChangeView> changes(@RequestParam(required=false) String type,@RequestParam(required=false) String search,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return readService.changes(type,search,page,size); }
 @JsonIgnoreProperties(ignoreUnknown=false) public record StartRunRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9-]{3,80}") String fixtureVersion,@NotNull WatchlistPayload.TransportFormat format) { }
}