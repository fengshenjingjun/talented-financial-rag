package com.financial.rag.evaluation;

import com.financial.rag.model.RetrievalResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Automated RAG quality evaluation using Ragas-inspired metrics:
 * - Faithfulness: answer derives strictly from retrieved context
 * - Answer Relevancy: response addresses the specific question
 * - Context Precision: retrieved documents are relevant
 * - Context Recall: no critical information missed
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RagasEvaluator {

    private final ChatLanguageModel chatLanguageModel;

    private static final PromptTemplate FAITHFULNESS_TEMPLATE = PromptTemplate.from("""
            Given the following context and answer, evaluate whether every factual claim
            in the answer is supported by the provided context.

            Context:
            {{context}}

            Answer:
            {{answer}}

            Score from 0.0 to 1.0 where:
            1.0 = all claims are directly supported by context
            0.0 = answer contains claims not found in context (hallucinations)

            Respond with ONLY a number between 0.0 and 1.0.
            """);

    private static final PromptTemplate RELEVANCY_TEMPLATE = PromptTemplate.from("""
            Evaluate how well the following answer addresses the given question.

            Question: {{question}}
            Answer: {{answer}}

            Score from 0.0 to 1.0 where:
            1.0 = answer directly and completely addresses the question
            0.0 = answer is completely irrelevant to the question

            Respond with ONLY a number between 0.0 and 1.0.
            """);

    private static final PromptTemplate CONTEXT_PRECISION_TEMPLATE = PromptTemplate.from("""
            Given the question and retrieved context chunks, evaluate what proportion of
            the retrieved context is actually relevant to answering the question.

            Question: {{question}}

            Retrieved Context:
            {{context}}

            Score from 0.0 to 1.0 where:
            1.0 = all retrieved chunks are highly relevant
            0.0 = none of the retrieved chunks are relevant

            Respond with ONLY a number between 0.0 and 1.0.
            """);

    public EvaluationResult evaluate(String question,
                                     String answer,
                                     List<RetrievalResult> retrievalResults) {
        String context = retrievalResults.stream()
                .map(RetrievalResult::getContent)
                .collect(Collectors.joining("\n\n"));

        double faithfulness = scoreMetric(FAITHFULNESS_TEMPLATE, Map.of(
                "context", context, "answer", answer));

        double relevancy = scoreMetric(RELEVANCY_TEMPLATE, Map.of(
                "question", question, "answer", answer));

        double contextPrecision = scoreMetric(CONTEXT_PRECISION_TEMPLATE, Map.of(
                "question", question, "context", context));

        // Context recall requires ground truth — approximated here
        double contextRecall = (faithfulness + contextPrecision) / 2.0;

        EvaluationResult result = new EvaluationResult(
                faithfulness, relevancy, contextPrecision, contextRecall,
                retrievalResults.size(),
                retrievalResults.stream().mapToDouble(RetrievalResult::getRerankScore).average().orElse(0.0)
        );

        log.info("Ragas evaluation: faithfulness={:.2f}, relevancy={:.2f}, precision={:.2f}, recall={:.2f}",
                faithfulness, relevancy, contextPrecision, contextRecall);

        return result;
    }

    private double scoreMetric(PromptTemplate template, Map<String, Object> vars) {
        try {
            String prompt = template.apply(vars).text();
            String response = chatLanguageModel.generate(prompt).trim();
            return Double.parseDouble(response.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            log.warn("Failed to compute metric: {}", e.getMessage());
            return 0.0;
        }
    }

    public record EvaluationResult(
            double faithfulness,
            double answerRelevancy,
            double contextPrecision,
            double contextRecall,
            int retrievedChunks,
            double avgRetrievalScore
    ) {
        public double overallScore() {
            return (faithfulness + answerRelevancy + contextPrecision + contextRecall) / 4.0;
        }

        public boolean meetsQualityBar() {
            return faithfulness >= 0.8 && answerRelevancy >= 0.7 && contextPrecision >= 0.7;
        }
    }
}
