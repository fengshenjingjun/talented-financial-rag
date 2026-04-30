package com.financial.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class FinancialDocument {

    private String id;
    private String content;
    private DocumentType documentType;
    private String companyTicker;
    private String filingDate;
    private String sourceUrl;
    private String accessLevel;
    private Map<String, Object> metadata;
    private float[] vector;

    public enum DocumentType {
        ANNUAL_REPORT,
        QUARTERLY_REPORT,
        REGULATORY_FILING,
        FINANCIAL_CONTRACT,
        MARKET_ANALYSIS,
        EARNINGS_CALL,
        PROSPECTUS
    }
}
