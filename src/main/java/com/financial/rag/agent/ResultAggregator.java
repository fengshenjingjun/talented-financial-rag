package com.financial.rag.agent;

import com.financial.rag.model.AgentResponse;
import com.financial.rag.model.RetrievalResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class ResultAggregator {

    private final ChatLanguageModel chatLanguageModel;

    private static final PromptTemplate SYNTHESIS_TEMPLATE = PromptTemplate.from("""
            You are a financial analyst synthesizing information to answer a query accurately.

            QUERY: {{query}}

            RETRIEVED CONTEXT:
            {{context}}

            WORKER RESULTS:
            {{workerResults}}

            Instructions:
            1. Answer the query based ONLY on the provided context and worker results
            2. Cite specific sources for each factual claim (use document IDs)
            3. If information is missing or uncertain, explicitly state so
            4. Use precise financial terminology
            5. Structure the answer clearly with key findings first
            6. Include relevant numbers, percentages, and dates from the sources

            Provide a comprehensive, accurate answer:
            """);

    public AgentResponse aggregate(String query,
                                   List<RetrievalResult> retrievalResults,
                                   List<AgentResponse.SubTask> completedTasks,
                                   Map<String, Long> stageLatencies) {

        String context = formatContext(retrievalResults);
        String workerResults = formatWorkerResults(completedTasks);

        String prompt = SYNTHESIS_TEMPLATE.apply(Map.of(
                "query", query,
                "context", context,
                "workerResults", workerResults
        )).text();

        String answer = chatLanguageModel.generate(prompt);

        List<AgentResponse.Citation> citations = buildCitations(retrievalResults);
        double confidence = computeConfidence(retrievalResults);

        return AgentResponse.builder()
                .queryId(UUID.randomUUID().toString())
                .answer(answer)
                .citations(citations)
                .subTasks(completedTasks)
                .confidenceScore(confidence)
                .complianceValidated(false)  // set by ComplianceCheckerAgent
                .stageLatencies(stageLatencies)
                .generatedAt(Instant.now())
                .build();
    }

    private String formatContext(List<RetrievalResult> results) {
        return results.stream()
                .map(r -> String.format("[%s | %s | %s]\n%s",
                        r.getDocumentId(), r.getCompanyTicker(), r.getFilingDate(), r.getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String formatWorkerResults(List<AgentResponse.SubTask> tasks) {
        return tasks.stream()
                .filter(t -> t.getResult() != null && !t.getResult().isBlank())
                .map(t -> String.format("[%s - %s]: %s", t.getTaskId(), t.getWorkerType(), t.getResult()))
                .collect(Collectors.joining("\n"));
    }

    private List<AgentResponse.Citation> buildCitations(List<RetrievalResult> results) {
        return results.stream()
                .map(r -> AgentResponse.Citation.builder()
                        .documentId(r.getDocumentId())
                        .sourceUrl(r.getSourceUrl())
                        .excerpt(r.getContent().substring(0, Math.min(200, r.getContent().length())) + "...")
                        .companyTicker(r.getCompanyTicker())
                        .filingDate(r.getFilingDate())
                        .relevanceScore(r.getRerankScore())
                        .build())
                .toList();
    }

    private double computeConfidence(List<RetrievalResult> results) {
        if (results.isEmpty()) return 0.0;
        return results.stream()
                .mapToDouble(RetrievalResult::getRerankScore)
                .average()
                .orElse(0.0);
    }
}
