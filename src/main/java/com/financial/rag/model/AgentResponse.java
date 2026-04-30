package com.financial.rag.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AgentResponse {

    private String queryId;
    private String answer;
    private List<Citation> citations;
    private List<SubTask> subTasks;
    private double confidenceScore;
    private boolean complianceValidated;
    private List<String> complianceWarnings;
    private Map<String, Long> stageLatencies;
    private int totalTokensUsed;
    private Instant generatedAt;

    @Data
    @Builder
    public static class Citation {
        private String documentId;
        private String sourceUrl;
        private String excerpt;
        private String companyTicker;
        private String filingDate;
        private double relevanceScore;
    }

    @Data
    @Builder
    public static class SubTask {
        private String taskId;
        private String workerType;
        private String description;
        private String status;
        private String result;
        private long latencyMs;
    }
}
