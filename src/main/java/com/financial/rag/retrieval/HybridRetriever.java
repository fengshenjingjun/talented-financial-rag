package com.financial.rag.retrieval;

import com.financial.rag.model.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Three-stage retrieval architecture:
 * Stage 1 - Candidate Generation: parallel vector + BM25 search, top-50
 * Stage 2 - Precision Reranking: cross-encoder, top-50 → top-5
 * Stage 3 - Quality Gating: relevance threshold, max 2 retry iterations
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HybridRetriever {

    private final MilvusConnector milvusConnector;
    private final BM25Searcher bm25Searcher;
    private final RerankerService reranker;
    private final QualityGate qualityGate;

    @Value("${retrieval.vector-top-k:50}")
    private int vectorTopK;

    @Value("${retrieval.bm25-top-k:30}")
    private int bm25TopK;

    @Value("${retrieval.rerank-top-k:5}")
    private int rerankTopK;

    @Value("${retrieval.max-retries:2}")
    private int maxRetries;

    @Value("${retrieval.vector-weight:0.6}")
    private double vectorWeight;

    @Value("${retrieval.bm25-weight:0.4}")
    private double bm25Weight;

    public List<RetrievalResult> retrieve(String query, Map<String, String> filters) {
        int attempt = 0;
        String currentQuery = query;
        List<RetrievalResult> finalResults = List.of();

        while (attempt <= maxRetries) {
            log.info("Retrieval attempt {}/{} for query: {}", attempt + 1, maxRetries + 1, currentQuery);

            // Stage 1: Parallel candidate generation
            CompletableFuture<List<RetrievalResult>> vectorFuture =
                    milvusConnector.searchAsync(currentQuery, filters, vectorTopK);
            CompletableFuture<List<RetrievalResult>> bm25Future =
                    bm25Searcher.searchAsync(currentQuery, filters, bm25TopK);

            List<RetrievalResult> vectorResults = vectorFuture.join();
            List<RetrievalResult> bm25Results = bm25Future.join();

            log.debug("Vector results: {}, BM25 results: {}", vectorResults.size(), bm25Results.size());

            // Merge and deduplicate with Reciprocal Rank Fusion
            List<RetrievalResult> candidates = reciprocalRankFusion(vectorResults, bm25Results);

            if (candidates.isEmpty()) {
                log.warn("No candidates found on attempt {}", attempt + 1);
                attempt++;
                currentQuery = expandQuery(query, attempt);
                continue;
            }

            // Stage 2: Precision reranking
            List<RetrievalResult> reranked = reranker.rerank(currentQuery, candidates, rerankTopK);

            // Stage 3: Quality gating
            QualityGate.GateResult gateResult = qualityGate.filter(reranked);
            finalResults = gateResult.results();

            if (gateResult.passed()) {
                log.info("Quality gate passed on attempt {}, returning {} results", attempt + 1, finalResults.size());
                break;
            }

            log.info("Quality gate failed (avg score: {}), retrying with expanded query", gateResult.avgScore());
            attempt++;
            currentQuery = expandQuery(query, attempt);
        }

        return finalResults;
    }

    // Reciprocal Rank Fusion: combines vector and BM25 rankings
    private List<RetrievalResult> reciprocalRankFusion(List<RetrievalResult> vectorResults,
                                                         List<RetrievalResult> bm25Results) {
        int k = 60;  // RRF constant
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, RetrievalResult> docMap = new LinkedHashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            RetrievalResult r = vectorResults.get(i);
            rrfScores.merge(r.getDocumentId(), vectorWeight * (1.0 / (k + i + 1)), Double::sum);
            docMap.put(r.getDocumentId(), r);
        }

        for (int i = 0; i < bm25Results.size(); i++) {
            RetrievalResult r = bm25Results.get(i);
            rrfScores.merge(r.getDocumentId(), bm25Weight * (1.0 / (k + i + 1)), Double::sum);
            docMap.putIfAbsent(r.getDocumentId(), r);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    RetrievalResult r = docMap.get(e.getKey());
                    return RetrievalResult.builder()
                            .documentId(r.getDocumentId())
                            .content(r.getContent())
                            .score(e.getValue())
                            .rerankScore(e.getValue())
                            .companyTicker(r.getCompanyTicker())
                            .filingDate(r.getFilingDate())
                            .documentType(r.getDocumentType())
                            .sourceUrl(r.getSourceUrl())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String expandQuery(String originalQuery, int attempt) {
        return switch (attempt) {
            case 1 -> originalQuery + " financial data report";
            case 2 -> "financial " + originalQuery;
            default -> originalQuery;
        };
    }
}
