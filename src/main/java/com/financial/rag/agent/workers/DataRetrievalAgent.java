package com.financial.rag.agent.workers;

import com.financial.rag.model.QueryContext;
import com.financial.rag.model.RetrievalResult;
import com.financial.rag.retrieval.HybridRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataRetrievalAgent {

    private final HybridRetriever hybridRetriever;

    public List<RetrievalResult> retrieve(QueryContext context) {
        log.info("DataRetrievalAgent: retrieving for query {}", context.getQueryId());

        List<RetrievalResult> results = hybridRetriever.retrieve(
                context.getRewrittenQuery(),
                context.getFilters()
        );

        // If primary query yields insufficient results, try expanded queries
        if (results.size() < 3 && !context.getExpandedQueries().isEmpty()) {
            log.info("Primary retrieval insufficient ({}), trying expanded queries", results.size());
            for (String expandedQuery : context.getExpandedQueries()) {
                if (expandedQuery.equals(context.getRewrittenQuery())) continue;
                List<RetrievalResult> expanded = hybridRetriever.retrieve(expandedQuery, context.getFilters());
                results = mergeResults(results, expanded);
                if (results.size() >= 5) break;
            }
        }

        log.info("DataRetrievalAgent: retrieved {} results", results.size());
        return results;
    }

    @Async
    public CompletableFuture<List<RetrievalResult>> retrieveAsync(QueryContext context) {
        return CompletableFuture.completedFuture(retrieve(context));
    }

    public List<RetrievalResult> retrieveWithFilters(String query, Map<String, String> filters) {
        return hybridRetriever.retrieve(query, filters);
    }

    private List<RetrievalResult> mergeResults(List<RetrievalResult> primary, List<RetrievalResult> secondary) {
        // Deduplicate by document ID and merge, keeping highest scores
        Map<String, RetrievalResult> merged = new java.util.LinkedHashMap<>();
        primary.forEach(r -> merged.put(r.getDocumentId(), r));
        secondary.forEach(r -> merged.merge(r.getDocumentId(), r,
                (existing, newResult) -> existing.getRerankScore() >= newResult.getRerankScore() ? existing : newResult));
        return merged.values().stream()
                .sorted((a, b) -> Double.compare(b.getRerankScore(), a.getRerankScore()))
                .toList();
    }
}
