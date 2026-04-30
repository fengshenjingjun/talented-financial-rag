package com.financial.rag.agent.workers;

import com.financial.rag.model.RetrievalResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class AnswerSynthesizerAgent {

    private final ChatLanguageModel chatLanguageModel;

    private static final PromptTemplate SYNTHESIS_TEMPLATE = PromptTemplate.from("""
            You are a senior financial analyst providing accurate, well-cited analysis.

            USER QUERY: {{query}}

            RETRIEVED DOCUMENTS:
            {{context}}

            COMPUTED METRICS AND ANALYSIS:
            {{workerOutputs}}

            Synthesize a comprehensive answer that:
            1. Directly addresses the user's query
            2. Cites specific document sources for each factual claim using [DOC_ID] notation
            3. Includes relevant financial figures with proper units and time periods
            4. Highlights key insights and implications
            5. Acknowledges any data limitations or uncertainties
            6. Uses clear financial terminology appropriate for professional audiences

            IMPORTANT: Only use information from the provided documents. Do not fabricate any financial figures.
            Add this disclaimer at the end: "This analysis is based on the retrieved documents and should not
            be considered investment advice."

            Answer:
            """);

    public String synthesize(String query, List<RetrievalResult> retrievalResults, String workerOutputs) {
        log.info("AnswerSynthesizerAgent: synthesizing final answer");

        String context = retrievalResults.stream()
                .map(r -> String.format("[%s | %s | %s]\n%s",
                        r.getDocumentId(), r.getCompanyTicker(),
                        r.getFilingDate(), r.getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = SYNTHESIS_TEMPLATE.apply(Map.of(
                "query", query,
                "context", context,
                "workerOutputs", workerOutputs != null ? workerOutputs : "No additional analysis available."
        )).text();

        return chatLanguageModel.generate(prompt);
    }
}
