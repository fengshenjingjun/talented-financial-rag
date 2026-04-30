package com.financial.rag.evaluation;

import com.financial.rag.model.AgentResponse;
import com.financial.rag.model.QueryContext;
import com.financial.rag.model.RetrievalResult;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Immutable append-only audit log for regulatory compliance.
 * Every query, retrieved document IDs, and generated answer is logged.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;

    public void logQuery(QueryContext context,
                         List<RetrievalResult> retrievalResults,
                         AgentResponse response) {
        try {
            AuditLogEntry entry = new AuditLogEntry();
            entry.setQueryId(context.getQueryId());
            entry.setUserId(context.getUserId());
            entry.setSessionId(context.getSessionId());
            entry.setOriginalQuery(context.getOriginalQuery());
            entry.setIntent(context.getIntent() != null ? context.getIntent().name() : "UNKNOWN");
            entry.setRetrievedDocIds(
                    retrievalResults.stream()
                            .map(r -> r.getDocumentId() + ":" + r.getRerankScore())
                            .toList()
                            .toString()
            );
            entry.setAnswer(response.getAnswer());
            entry.setConfidenceScore(response.getConfidenceScore());
            entry.setComplianceValidated(response.isComplianceValidated());
            entry.setTotalTokens(response.getTotalTokensUsed());
            entry.setTimestamp(Instant.now());

            auditLogRepository.save(entry);
            log.debug("Audit log written for query {}", context.getQueryId());
        } catch (Exception e) {
            log.error("Failed to write audit log for query {}", context.getQueryId(), e);
        }
    }

    @Entity
    @Table(name = "audit_logs")
    public static class AuditLogEntry {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false)
        private String queryId;

        private String userId;
        private String sessionId;

        @Column(length = 2000)
        private String originalQuery;

        private String intent;

        @Column(length = 4000)
        private String retrievedDocIds;

        @Column(length = 8000)
        private String answer;

        private double confidenceScore;
        private boolean complianceValidated;
        private int totalTokens;

        @Column(nullable = false)
        private Instant timestamp;

        // getters/setters
        public void setQueryId(String queryId) { this.queryId = queryId; }
        public void setUserId(String userId) { this.userId = userId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public void setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; }
        public void setIntent(String intent) { this.intent = intent; }
        public void setRetrievedDocIds(String retrievedDocIds) { this.retrievedDocIds = retrievedDocIds; }
        public void setAnswer(String answer) { this.answer = answer; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
        public void setComplianceValidated(boolean complianceValidated) { this.complianceValidated = complianceValidated; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public String getQueryId() { return queryId; }
    }
}
