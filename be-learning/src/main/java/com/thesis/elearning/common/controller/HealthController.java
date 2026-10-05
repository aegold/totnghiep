package com.thesis.elearning.common.controller;

import com.thesis.elearning.common.dto.ApiResponse;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness probe and API handshake.
 *
 * <p>Public in {@code SecurityConfig} so the browser and any container
 * orchestrator can reach it without a token. It is the first thing to curl when
 * confirming that the service is up and that CORS is negotiating correctly.
 */
@RestController
public class HealthController {

    private final String activeProfile;

    public HealthController(
            @Value("${spring.profiles.active:default}") final String activeProfile) {
        this.activeProfile = activeProfile;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(
                Map.of(
                        "status", "UP",
                        "service", "adaptive-learning-api",
                        "profile", activeProfile,
                        "timestamp", Instant.now().toString()),
                "Dịch vụ đang hoạt động bình thường");
    }
}