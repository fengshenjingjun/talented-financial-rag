package com.financial.rag.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class QueryContext {

    private String queryId;
    private String originalQuery;
    private String rewrittenQuery;
    private QueryIntent intent;
    private List<String> expandedQueries;
    private Map<String, String> filters;
    private String userId;
    private String sessionId;
    private Instant timestamp;

    public enum QueryIntent {
        FACTUAL_LOOKUP,
        COMPARATIVE_ANALYSIS,
        TREND_ANALYSIS,
        RISK_ASSESSMENT,
        COMPLIANCE_CHECK,
        GENERAL
    }
}
