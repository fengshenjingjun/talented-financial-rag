package com.financial.rag.retrieval;

import com.financial.rag.model.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridRetrieverTest {

    @Mock
    private MilvusConnector milvusConnector;

    @Mock
    private BM25Searcher bm25Searcher;

    @Mock
    private RerankerService reranker;

    @Mock
    private QualityGate qualityGate;

    private HybridRetriever hybridRetriever;

    @BeforeEach
    void setUp() {
        hybridRetriever = new HybridRetriever(milvusConnector, bm25Searcher, reranker, qualityGate);
        // Set field values via reflection (since @Value doesn't work in unit tests)
        setField(hybridRetriever, "vectorTopK", 50);
        setField(hybridRetriever, "bm25TopK", 30);
        setField(hybridRetriever, "rerankTopK", 5);
        setField(hybridRetriever, "maxRetries", 2);
        setField(hybridRetriever, "vectorWeight", 0.6);
        setField(hybridRetriever, "bm25Weight", 0.4);
    }

    @Test
    void retrieve_shouldMergeVectorAndBM25Results() {
        List<RetrievalResult> vectorResults = List.of(
                buildResult("doc1", 0.9),
                buildResult("doc2", 0.8)
        );
        List<RetrievalResult> bm25Results = List.of(
                buildResult("doc2", 0.7),
                buildResult("doc3", 0.6)
        );
        List<RetrievalResult> reranked = List.of(
                buildResult("doc1", 0.95),
                buildResult("doc2", 0.85)
        );

        when(milvusConnector.searchAsync(anyString(), any(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(vectorResults));
        when(bm25Searcher.searchAsync(anyString(), any(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(bm25Results));
        when(reranker.rerank(anyString(), anyList(), anyInt())).thenReturn(reranked);
        when(qualityGate.filter(anyList()))
                .thenReturn(new QualityGate.GateResult(reranked, true, 0.9));

        List<RetrievalResult> results = hybridRetriever.retrieve("Apple revenue 2023", Map.of());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getDocumentId()).isEqualTo("doc1");
        verify(reranker).rerank(anyString(), anyList(), anyInt());
    }

    @Test
    void retrieve_shouldRetryWhenQualityGateFails() {
        List<RetrievalResult> results = List.of(buildResult("doc1", 0.5));

        when(milvusConnector.searchAsync(anyString(), any(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(results));
        when(bm25Searcher.searchAsync(anyString(), any(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(reranker.rerank(anyString(), anyList(), anyInt())).thenReturn(results);

        // First attempt fails quality gate, second passes
        when(qualityGate.filter(anyList()))
                .thenReturn(new QualityGate.GateResult(results, false, 0.5))
                .thenReturn(new QualityGate.GateResult(results, true, 0.8));

        List<RetrievalResult> finalResults = hybridRetriever.retrieve("test query", Map.of());

        assertThat(finalResults).isNotEmpty();
        verify(milvusConnector, times(2)).searchAsync(anyString(), any(), anyInt());
    }

    @Test
    void retrieve_shouldReturnEmptyListWhenNoCandidates() {
        when(milvusConnector.searchAsync(anyString(), any(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(bm25Searcher.searchAsync(anyString(), any(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        List<RetrievalResult> results = hybridRetriever.retrieve("nonexistent query", Map.of());

        assertThat(results).isEmpty();
    }

    private RetrievalResult buildResult(String docId, double score) {
        return RetrievalResult.builder()
                .documentId(docId)
                .content("Sample financial content for " + docId)
                .score(score)
                .rerankScore(score)
                .companyTicker("AAPL")
                .filingDate("2023-10-27")
                .build();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = HybridRetriever.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
