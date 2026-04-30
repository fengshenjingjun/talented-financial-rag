package com.financial.rag.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetrievalResult {

    private String documentId;
    private String content;
    private double score;
    private double rerankScore;
    private String companyTicker;
    private String filingDate;
    private String documentType;
    private String sourceUrl;
    private int chunkIndex;

    public boolean meetsQualityThreshold(double threshold) {
        return rerankScore >= threshold;
    }
}
