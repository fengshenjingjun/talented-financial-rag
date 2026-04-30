package com.financial.rag.query;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FinancialTermExpander {

    private static final Map<String, List<String>> TERM_SYNONYMS = new HashMap<>() {{
        put("debt-to-equity", List.of("D/E ratio", "leverage ratio", "financial leverage", "gearing ratio"));
        put("ebitda", List.of("operating earnings", "earnings before interest taxes depreciation amortization", "operating cash flow proxy"));
        put("roi", List.of("return on investment", "investment return", "profitability ratio"));
        put("roe", List.of("return on equity", "equity return", "shareholder return ratio"));
        put("roa", List.of("return on assets", "asset return", "asset efficiency"));
        put("p/e", List.of("price to earnings", "earnings multiple", "P/E ratio", "price earnings ratio"));
        put("eps", List.of("earnings per share", "net income per share", "diluted eps", "basic eps"));
        put("revenue", List.of("net sales", "total revenue", "turnover", "top line", "gross sales"));
        put("net income", List.of("net profit", "bottom line", "net earnings", "profit after tax"));
        put("cash flow", List.of("operating cash flow", "free cash flow", "cash from operations", "OCF", "FCF"));
        put("assets", List.of("total assets", "asset base", "balance sheet assets"));
        put("liabilities", List.of("total liabilities", "debt obligations", "financial obligations"));
        put("equity", List.of("stockholders equity", "shareholders equity", "book value", "net assets"));
        put("sec filing", List.of("10-K", "10-Q", "8-K", "annual report", "quarterly report", "SEC report"));
        put("balance sheet", List.of("statement of financial position", "financial position statement"));
        put("income statement", List.of("profit and loss statement", "P&L", "statement of operations"));
        put("compliance", List.of("regulatory compliance", "regulatory requirements", "SEC compliance", "GAAP compliance"));
    }};

    public String expand(String query) {
        String expanded = query;
        for (Map.Entry<String, List<String>> entry : TERM_SYNONYMS.entrySet()) {
            if (query.toLowerCase().contains(entry.getKey().toLowerCase())) {
                String synonyms = String.join(", ", entry.getValue());
                expanded = expanded + " (also known as: " + synonyms + ")";
            }
        }
        return expanded;
    }

    public List<String> getSynonyms(String term) {
        return TERM_SYNONYMS.getOrDefault(term.toLowerCase(), List.of());
    }
}
