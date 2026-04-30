package com.financial.rag.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Financial statement and ratio tools.
 * Production: integrate with SEC EDGAR API or financial data providers.
 */
@Component
@Slf4j
public class FinancialDataTools {

    @Tool("Fetch financial statements (income statement, balance sheet, cash flow) for a company")
    public FinancialStatement getFinancialStatement(String ticker, String period, String statementType) {
        log.info("Fetching {} for {} period {}", statementType, ticker, period);
        // Production: query SEC EDGAR or financial data provider
        return FinancialStatement.builder()
                .ticker(ticker)
                .period(period)
                .statementType(statementType)
                .lineItems(Map.of(
                        "revenue", 394328000000.0,
                        "net_income", 99803000000.0,
                        "total_assets", 352583000000.0,
                        "total_liabilities", 290437000000.0,
                        "total_equity", 62146000000.0
                ))
                .currency("USD")
                .build();
    }

    @Tool("Calculate standard financial ratios from balance sheet and income statement data")
    public Map<String, Double> calculateRatios(String ticker, String period) {
        log.info("Calculating ratios for {} period {}", ticker, period);
        // Production: fetch real data and compute ratios
        return Map.of(
                "debt_to_equity", 4.67,
                "current_ratio", 0.99,
                "quick_ratio", 0.94,
                "return_on_equity", 1.608,
                "return_on_assets", 0.283,
                "net_profit_margin", 0.253,
                "gross_profit_margin", 0.441,
                "price_to_earnings", 29.5,
                "earnings_per_share", 6.13
        );
    }

    @Tool("Get earnings per share (EPS) history for a company across multiple periods")
    public List<EpsRecord> getEpsHistory(String ticker, int numberOfPeriods) {
        log.info("Fetching EPS history for {} ({} periods)", ticker, numberOfPeriods);
        // Production: query financial data API
        return List.of(
                new EpsRecord(ticker, "Q4 2023", 2.18, 2.10),
                new EpsRecord(ticker, "Q3 2023", 1.46, 1.39),
                new EpsRecord(ticker, "Q2 2023", 1.26, 1.19),
                new EpsRecord(ticker, "Q1 2023", 1.52, 1.43)
        );
    }

    @Tool("Retrieve dividend history for a company")
    public List<DividendRecord> getDividendHistory(String ticker, String startYear) {
        log.info("Fetching dividend history for {} since {}", ticker, startYear);
        return List.of(
                new DividendRecord(ticker, "2023-11-10", 0.24, "USD", "QUARTERLY"),
                new DividendRecord(ticker, "2023-08-11", 0.24, "USD", "QUARTERLY"),
                new DividendRecord(ticker, "2023-05-12", 0.24, "USD", "QUARTERLY")
        );
    }

    @lombok.Builder
    public record FinancialStatement(
            String ticker,
            String period,
            String statementType,
            Map<String, Double> lineItems,
            String currency
    ) {}

    public record EpsRecord(String ticker, String period, double actualEps, double estimatedEps) {}
    public record DividendRecord(String ticker, String date, double amount, String currency, String frequency) {}
}
