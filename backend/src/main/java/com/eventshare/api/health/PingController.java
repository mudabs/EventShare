package com.eventshare.api.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@Tag(name = "System")
@RestController
public class PingController {

    @Operation(summary = "Lightweight liveness probe for the API")
    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("service", "eventshare-api", "status", "ok", "time", Instant.now().toString());
    }
}
