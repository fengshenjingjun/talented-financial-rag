package com.financial.rag.query;

import com.financial.rag.model.QueryContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class QueryAnalyzer {

    private final IntentClassifier intentClassifier;
    private final QueryRewriter queryRewriter;
    private final FinancialTermExpander termExpander;

    private static final Pattern TICKER_PATTERN = Pattern.compile("\\b([A-Z]{1,5})\\b");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");

    public QueryContext analyze(String rawQuery, String userId, String sessionId) {
        log.info("Analyzing query for user {}: {}", userId, rawQuery);

        // Classify intent
        QueryContext.QueryIntent intent = intentClassifier.classify(rawQuery);
        log.debug("Detected intent: {}", intent);

        // Expand domain-specific terms
        String expandedQuery = termExpander.expand(rawQuery);

        // Generate multiple query formulations based on intent
        List<String> expandedQueries;
        String primaryQuery;

        switch (intent) {
            case COMPARATIVE_ANALYSIS, TREND_ANALYSIS -> {
                // Use query expansion for complex analytical queries
                expandedQueries = queryRewriter.expandQuery(expandedQuery);
                primaryQuery = queryRewriter.stepBack(rawQuery);
            }
            case RISK_ASSESSMENT, COMPLIANCE_CHECK -> {
                // Use HyDE for better semantic matching on risk/compliance
                primaryQuery = queryRewriter.hydeRewrite(rawQuery);
                expandedQueries = queryRewriter.expandQuery(rawQuery);
            }
            default -> {
                primaryQuery = expandedQuery;
                expandedQueries = List.of(rawQuery, expandedQuery);
            }
        }

        // Extract metadata filters from query
        Map<String, String> filters = extractFilters(rawQuery);

        return QueryContext.builder()
                .queryId(UUID.randomUUID().toString())
                .originalQuery(rawQuery)
                .rewrittenQuery(primaryQuery)
                .intent(intent)
                .expandedQueries(expandedQueries)
                .filters(filters)
                .userId(userId)
                .sessionId(sessionId)
                .timestamp(Instant.now())
                .build();
    }

    private Map<String, String> extractFilters(String query) {
        Map<String, String> filters = new HashMap<>();

        // Extract company tickers
        Matcher tickerMatcher = TICKER_PATTERN.matcher(query);
        StringBuilder tickers = new StringBuilder();
        while (tickerMatcher.find()) {
            String candidate = tickerMatcher.group(1);
            // Exclude common words that match ticker pattern
            if (!isCommonWord(candidate)) {
                if (!tickers.isEmpty()) tickers.append(",");
                tickers.append(candidate);
            }
        }
        if (!tickers.isEmpty()) {
            filters.put("company_ticker", tickers.toString());
        }

        // Extract date ranges
        Matcher dateMatcher = DATE_PATTERN.matcher(query);
        List<String> years = dateMatcher.results()
                .map(r -> r.group(1))
                .toList();
        if (!years.isEmpty()) {
            filters.put("year_from", years.get(0));
            filters.put("year_to", years.get(years.size() - 1));
        }

        // Detect document type from keywords
        if (query.toLowerCase().contains("annual") || query.toLowerCase().contains("10-k")) {
            filters.put("document_type", "ANNUAL_REPORT");
        } else if (query.toLowerCase().contains("quarterly") || query.toLowerCase().contains("10-q")) {
            filters.put("document_type", "QUARTERLY_REPORT");
        }

        return filters;
    }

    private boolean isCommonWord(String word) {
        return List.of("IN", "OF", "TO", "FOR", "AND", "OR", "THE", "A", "IS", "ON",
                "AT", "BY", "FROM", "WITH", "CEO", "CFO", "CTO", "IPO", "SEC").contains(word);
    }
}
