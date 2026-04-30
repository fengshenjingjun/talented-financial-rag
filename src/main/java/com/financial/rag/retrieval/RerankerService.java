package com.financial.rag.retrieval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.financial.rag.model.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class RerankerService {

    private final WebClient.Builder webClientBuilder;

    @Value("${reranker.base-url:http://localhost:8001}")
    private String rerankerBaseUrl;

    @Value("${reranker.model:bge-reranker-v2-m3}")
    private String rerankerModel;

    @Value("${reranker.enabled:true}")
    private boolean enabled;

    // Rerank top candidates → select top-K with highest relevance scores
    // Target: 35-49% retrieval failure reduction per architecture spec
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (!enabled || candidates.isEmpty()) {
            return candidates.stream().limit(topK).toList();
        }

        try {
            List<String> passages = candidates.stream()
                    .map(RetrievalResult::getContent)
                    .collect(Collectors.toList());

            RerankRequest request = new RerankRequest(query, passages, rerankerModel, topK);

            RerankResponse response = webClientBuilder.build()
                    .post()
                    .uri(rerankerBaseUrl + "/rerank")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RerankResponse.class)
                    .block();

            if (response == null || response.results() == null) {
                log.warn("Reranker returned null response, using original ordering");
                return candidates.stream().limit(topK).toList();
            }

            // Map reranker scores back to results
            return response.results().stream()
                    .sorted((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()))
                    .limit(topK)
                    .map(r -> {
                        RetrievalResult original = candidates.get(r.index());
                        return RetrievalResult.builder()
                                .documentId(original.getDocumentId())
                                .content(original.getContent())
                                .score(original.getScore())
                                .rerankScore(r.relevanceScore())
                                .companyTicker(original.getCompanyTicker())
                                .filingDate(original.getFilingDate())
                                .documentType(original.getDocumentType())
                                .sourceUrl(original.getSourceUrl())
                                .build();
                    })
                    .toList();

        } catch (Exception e) {
            log.warn("Reranking failed, falling back to vector similarity ordering", e);
            return candidates.stream().limit(topK).toList();
        }
    }

    record RerankRequest(
            String query,
            List<String> documents,
            String model,
            @JsonProperty("top_n") int topN
    ) {}

    record RerankResponse(List<RerankResult> results) {}

    record RerankResult(
            int index,
            @JsonProperty("relevance_score") double relevanceScore
    ) {}
}
