package io.tradeops.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminStatusController {
    @GetMapping("/status")
    public AdminStatusResponse status() {
        return new AdminStatusResponse("ok", "tradeops-api", "admin");
    }

    public record AdminStatusResponse(String status, String service, String access) { }
}
