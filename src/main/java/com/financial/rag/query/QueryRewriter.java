package com.financial.rag.query;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class QueryRewriter {

    @Qualifier("smallChatModel")
    private final ChatLanguageModel smallModel;

    // Step-Back Prompting: generates an abstract/foundational query
    private static final PromptTemplate STEP_BACK_TEMPLATE = PromptTemplate.from("""
            Given this specific financial query, generate a more abstract, foundational question
            that would provide useful background context. The abstract question should cover
            broader financial concepts relevant to the specific query.

            Specific query: {{query}}

            Abstract/foundational query (one line only):
            """);

    // HyDE: generates a hypothetical ideal answer snippet
    private static final PromptTemplate HYDE_TEMPLATE = PromptTemplate.from("""
            Write a brief, ideally relevant passage (2-3 sentences) that would perfectly answer
            this financial query. This hypothetical passage will be used to improve document retrieval.

            Query: {{query}}

            Hypothetical answer passage:
            """);

    // Query Expansion: generates multiple reformulations
    private static final PromptTemplate EXPANSION_TEMPLATE = PromptTemplate.from("""
            Generate 3 different reformulations of this financial query, each covering
            a different aspect or using different financial terminology.
            Output ONLY the 3 queries, one per line, no numbering.

            Original query: {{query}}

            Reformulations:
            """);

    public String stepBack(String query) {
        try {
            String prompt = STEP_BACK_TEMPLATE.apply(Map.of("query", query)).text();
            return smallModel.generate(prompt).trim();
        } catch (Exception e) {
            log.warn("Step-back rewriting failed for query: {}", query, e);
            return query;
        }
    }

    public String hydeRewrite(String query) {
        try {
            String prompt = HYDE_TEMPLATE.apply(Map.of("query", query)).text();
            return smallModel.generate(prompt).trim();
        } catch (Exception e) {
            log.warn("HyDE rewriting failed for query: {}", query, e);
            return query;
        }
    }

    public List<String> expandQuery(String query) {
        try {
            String prompt = EXPANSION_TEMPLATE.apply(Map.of("query", query)).text();
            String response = smallModel.generate(prompt).trim();
            List<String> expansions = Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(3)
                    .toList();
            List<String> result = new ArrayList<>(expansions);
            result.add(0, query);  // always include original
            return result;
        } catch (Exception e) {
            log.warn("Query expansion failed for query: {}", query, e);
            return List.of(query);
        }
    }
}
