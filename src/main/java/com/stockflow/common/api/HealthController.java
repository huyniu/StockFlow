package com.stockflow.common.api;

import java.time.Instant;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Điểm kiểm tra hoạt động công khai cho demo và theo dõi ứng dụng. */
@RestController
@Tag(name = "Health", description = "Public application health check")
@RequestMapping("/api/v1/health")
public class HealthController {

    /** Trả trạng thái hoạt động và thời điểm kiểm tra. */
    @GetMapping
    @Operation(summary = "Check application health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "timestamp", Instant.now().toString()));
    }
}
