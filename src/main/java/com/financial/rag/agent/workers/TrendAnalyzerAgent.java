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
public class TrendAnalyzerAgent {

    private final ChatLanguageModel chatLanguageModel;

    private static final PromptTemplate TREND_ANALYSIS_TEMPLATE = PromptTemplate.from("""
            Perform time-series trend analysis on the following financial data.

            Context documents:
            {{context}}

            Analysis task: {{task}}

            Instructions:
            1. Identify all time-series data points (quarterly/annual figures)
            2. Calculate year-over-year (YoY) and quarter-over-quarter (QoQ) changes
            3. Identify trends: INCREASING, DECREASING, VOLATILE, STABLE
            4. Detect anomalies or significant inflection points
            5. Provide a narrative summary of the trend

            Return structured JSON:
            {
              "dataPoints": [{"period": "string", "value": number, "source": "doc_id"}],
              "yoyChanges": [{"fromPeriod": "string", "toPeriod": "string", "changePercent": number}],
              "trend": "INCREASING|DECREASING|VOLATILE|STABLE",
              "anomalies": [{"period": "string", "description": "string"}],
              "summary": "narrative summary of trend"
            }
            """);

    public String analyzeTrend(List<RetrievalResult> context, String task) {
        log.info("TrendAnalyzerAgent: analyzing trend for task: {}", task);

        String contextText = context.stream()
                .map(r -> String.format("[%s | %s]: %s",
                        r.getDocumentId(), r.getFilingDate(), r.getContent()))
                .collect(Collectors.joining("\n\n"));

        String prompt = TREND_ANALYSIS_TEMPLATE.apply(Map.of(
                "context", contextText,
                "task", task
        )).text();

        return chatLanguageModel.generate(prompt);
    }
}
