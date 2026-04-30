package com.financial.rag.agent.workers;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ComplianceCheckerAgent {

    private final ChatLanguageModel chatLanguageModel;

    private static final PromptTemplate COMPLIANCE_TEMPLATE = PromptTemplate.from("""
            You are a financial compliance specialist. Review the following answer for potential
            compliance issues before it is delivered to the user.

            Original Query: {{query}}
            Proposed Answer: {{answer}}

            Check for:
            1. Forward-looking statements without required disclaimers (SEC Safe Harbor)
            2. Investment advice that should include risk disclosures
            3. Material non-public information (MNPI) risk
            4. GAAP/IFRS compliance of financial figures cited
            5. Accuracy of regulatory references
            6. Missing required disclaimers for financial analysis

            Return JSON:
            {
              "isCompliant": true|false,
              "riskLevel": "LOW|MEDIUM|HIGH",
              "issues": ["list of identified issues"],
              "requiredDisclaimers": ["list of disclaimers to add"],
              "suggestedModifications": ["list of suggested answer modifications"]
            }
            """);

    public ComplianceResult check(String query, String proposedAnswer) {
        log.info("ComplianceCheckerAgent: validating answer compliance");

        String prompt = COMPLIANCE_TEMPLATE.apply(Map.of(
                "query", query,
                "answer", proposedAnswer
        )).text();

        String response = chatLanguageModel.generate(prompt);

        // Parse compliance result - simplified for brevity
        boolean isCompliant = !response.contains("\"isCompliant\": false");
        List<String> warnings = extractWarnings(response);

        return new ComplianceResult(isCompliant, warnings);
    }

    private List<String> extractWarnings(String response) {
        // In production: use proper JSON parsing
        if (response.contains("\"issues\"")) {
            return List.of("Review compliance issues identified in the answer");
        }
        return List.of();
    }

    public record ComplianceResult(boolean isCompliant, List<String> warnings) {}
}
