package com.financial.rag.evaluation;

import com.financial.rag.model.RetrievalResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagasEvaluatorTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    private RagasEvaluator ragasEvaluator;

    @BeforeEach
    void setUp() {
        ragasEvaluator = new RagasEvaluator(chatLanguageModel);
    }

    @Test
    void evaluate_shouldReturnMetricsInValidRange() {
        when(chatLanguageModel.generate(anyString()))
                .thenReturn("0.85")  // faithfulness
                .thenReturn("0.90")  // relevancy
                .thenReturn("0.80"); // context precision

        List<RetrievalResult> results = List.of(
                buildResult("doc1", 0.88),
                buildResult("doc2", 0.75)
        );

        RagasEvaluator.EvaluationResult evalResult = ragasEvaluator.evaluate(
                "What is Apple's revenue in 2023?",
                "Apple's revenue in 2023 was $394.3 billion [doc1].",
                results
        );

        assertThat(evalResult.faithfulness()).isBetween(0.0, 1.0);
        assertThat(evalResult.answerRelevancy()).isBetween(0.0, 1.0);
        assertThat(evalResult.contextPrecision()).isBetween(0.0, 1.0);
        assertThat(evalResult.overallScore()).isBetween(0.0, 1.0);
        assertThat(evalResult.retrievedChunks()).isEqualTo(2);
    }

    @Test
    void evaluate_meetsQualityBar_shouldReturnTrueForHighScores() {
        when(chatLanguageModel.generate(anyString()))
                .thenReturn("0.9")
                .thenReturn("0.85")
                .thenReturn("0.88");

        RagasEvaluator.EvaluationResult result = ragasEvaluator.evaluate(
                "test question", "test answer", List.of(buildResult("d1", 0.9)));

        assertThat(result.meetsQualityBar()).isTrue();
    }

    @Test
    void evaluate_shouldHandleLLMFailureGracefully() {
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        RagasEvaluator.EvaluationResult result = ragasEvaluator.evaluate(
                "question", "answer", List.of(buildResult("d1", 0.5)));

        // Should return 0.0 scores gracefully, not throw
        assertThat(result.faithfulness()).isEqualTo(0.0);
        assertThat(result.overallScore()).isGreaterThanOrEqualTo(0.0);
    }

    private RetrievalResult buildResult(String docId, double score) {
        return RetrievalResult.builder()
                .documentId(docId)
                .content("Apple Inc. annual report 2023 revenue: $394.3 billion")
                .score(score)
                .rerankScore(score)
                .companyTicker("AAPL")
                .filingDate("2023-10-27")
                .build();
    }
}
