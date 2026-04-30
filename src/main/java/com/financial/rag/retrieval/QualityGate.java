package com.financial.rag.retrieval;

import com.financial.rag.model.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class QualityGate {

    @Value("${retrieval.quality-threshold:0.7}")
    private double qualityThreshold;

    @Value("${retrieval.min-results:1}")
    private int minResults;

    public record GateResult(List<RetrievalResult> results, boolean passed, double avgScore) {}

    // Stage 3: Quality Gating - threshold check per architecture spec
    // If below threshold → trigger re-retrieval (handled by HybridRetriever)
    public GateResult filter(List<RetrievalResult> rerankedResults) {
        List<RetrievalResult> passing = rerankedResults.stream()
                .filter(r -> r.getRerankScore() >= qualityThreshold)
                .toList();

        double avgScore = rerankedResults.stream()
                .mapToDouble(RetrievalResult::getRerankScore)
                .average()
                .orElse(0.0);

        if (passing.size() < minResults) {
            log.warn("Quality gate: only {}/{} results above threshold {}. Avg score: {}",
                    passing.size(), rerankedResults.size(), qualityThreshold, avgScore);

            // Return at least minResults even if below threshold (graceful degradation)
            List<RetrievalResult> fallback = rerankedResults.stream()
                    .sorted((a, b) -> Double.compare(b.getRerankScore(), a.getRerankScore()))
                    .limit(Math.max(minResults, passing.size()))
                    .toList();

            return new GateResult(fallback, false, avgScore);
        }

        log.debug("Quality gate passed: {}/{} results, avg score: {}",
                passing.size(), rerankedResults.size(), avgScore);
        return new GateResult(passing, true, avgScore);
    }
}
