package com.financial.rag.query;

import com.financial.rag.model.QueryContext.QueryIntent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class IntentClassifier {

    @Qualifier("smallChatModel")
    private final ChatLanguageModel smallModel;

    private static final PromptTemplate INTENT_TEMPLATE = PromptTemplate.from("""
            Classify the following financial query into exactly ONE of these categories:
            - FACTUAL_LOOKUP: Looking for specific facts (e.g., "What is Apple's revenue in 2023?")
            - COMPARATIVE_ANALYSIS: Comparing entities or periods (e.g., "Compare debt ratios of A and B")
            - TREND_ANALYSIS: Analyzing changes over time (e.g., "How has profit margin changed 2020-2024?")
            - RISK_ASSESSMENT: Evaluating financial risks (e.g., "What are the main risks in this filing?")
            - COMPLIANCE_CHECK: Regulatory or compliance queries (e.g., "Does this filing meet SEC requirements?")
            - GENERAL: Does not fit above categories

            Query: {{query}}

            Respond with ONLY the category name, nothing else.
            """);

    public QueryIntent classify(String query) {
        try {
            Prompt prompt = INTENT_TEMPLATE.apply(Map.of("query", query));
            String response = smallModel.generate(prompt.text()).trim().toUpperCase();
            return QueryIntent.valueOf(response);
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse intent for query: {}, defaulting to GENERAL", query);
            return QueryIntent.GENERAL;
        }
    }
}
