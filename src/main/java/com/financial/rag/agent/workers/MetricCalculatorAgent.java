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
public class MetricCalculatorAgent {

    private final ChatLanguageModel chatLanguageModel;

    private static final PromptTemplate EXTRACT_METRICS_TEMPLATE = PromptTemplate.from("""
            Extract and calculate financial metrics from the following financial document excerpts.

            Context:
            {{context}}

            Task: {{task}}

            Instructions:
            1. Extract all numerical financial data (revenues, profits, ratios, etc.)
            2. Calculate requested metrics if underlying data is available
            3. Format results as structured JSON
            4. Include the source document ID for each data point
            5. If a metric cannot be calculated due to missing data, state so explicitly

            Return JSON format:
            {
              "metrics": {
                "metric_name": {"value": number, "unit": "string", "source": "doc_id", "period": "string"}
              },
              "calculations": [{"formula": "string", "result": number, "explanation": "string"}],
              "missing_data": ["list of unavailable metrics"]
            }
            """);

    public String calculateMetrics(List<RetrievalResult> context, String task) {
        log.info("MetricCalculatorAgent: calculating metrics for task: {}", task);

        String contextText = context.stream()
                .map(r -> String.format("[%s]: %s", r.getDocumentId(), r.getContent()))
                .collect(Collectors.joining("\n\n"));

        String prompt = EXTRACT_METRICS_TEMPLATE.apply(Map.of(
                "context", contextText,
                "task", task
        )).text();

        return chatLanguageModel.generate(prompt);
    }
}
