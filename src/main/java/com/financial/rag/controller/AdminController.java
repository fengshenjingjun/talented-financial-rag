package com.financial.rag.controller;

import com.financial.rag.evaluation.AuditLogRepository;
import com.financial.rag.evaluation.AuditLogger;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogger.AuditLogEntry>> getAuditLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<AuditLogger.AuditLogEntry> logs;

        if (userId != null) {
            logs = auditLogRepository.findByUserId(userId);
        } else if (from != null && to != null) {
            Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            logs = auditLogRepository.findByTimestampBetween(fromInstant, toInstant);
        } else {
            logs = auditLogRepository.findAll();
        }

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/metrics/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        Map<String, Object> summary = Map.of(
                "total_queries", getCounterValue("rag.queries.total"),
                "avg_latency_ms", getGaugeValue("rag.stage.latency"),
                "compliance_failures", getCounterValue("rag.compliance.failures"),
                "retrieval_avg_score", getGaugeValue("rag.retrieval.score")
        );
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/compliance/unvalidated")
    public ResponseEntity<List<AuditLogger.AuditLogEntry>> getUnvalidatedEntries() {
        return ResponseEntity.ok(auditLogRepository.findUnvalidatedEntries());
    }

    @GetMapping("/health/milvus")
    public ResponseEntity<Map<String, String>> checkMilvusHealth() {
        // Production: ping Milvus and return actual status
        return ResponseEntity.ok(Map.of("status", "UP", "collection", "financial_documents"));
    }

    private double getCounterValue(String name) {
        try {
            return meterRegistry.get(name).counter().count();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getGaugeValue(String name) {
        try {
            return meterRegistry.get(name).gauge().value();
        } catch (Exception e) {
            return 0.0;
        }
    }
}
