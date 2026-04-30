package com.financial.rag.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regulatory and compliance tools.
 * Production: integrate with SEC EDGAR full-text search and regulatory databases.
 */
@Component
@Slf4j
public class RegulatoryTools {

    @Tool("Search SEC EDGAR for regulatory filings by company ticker and form type")
    public List<Filing> searchSecFilings(String ticker, String formType, int maxResults) {
        log.info("Searching SEC filings for {} form type {}", ticker, formType);
        // Production: call SEC EDGAR full-text search API
        return List.of(
                new Filing(ticker, formType, "2024-01-26", "https://www.sec.gov/Archives/edgar/data/example/10-K.htm",
                        "Annual report for fiscal year ended September 30, 2023"),
                new Filing(ticker, formType, "2023-10-27", "https://www.sec.gov/Archives/edgar/data/example/10-Q.htm",
                        "Quarterly report for period ended July 1, 2023")
        );
    }

    @Tool("Retrieve specific regulatory requirement or rule by keyword or regulation code")
    public List<Regulation> searchRegulations(String keyword) {
        log.info("Searching regulations for keyword: {}", keyword);
        // Production: query regulatory database (SEC rules, GAAP codification)
        return List.of(
                new Regulation(
                        "ASC 606",
                        "Revenue from Contracts with Customers",
                        "FASB",
                        "Establishes principles for reporting revenue from contracts with customers",
                        "https://asc.fasb.org/606"
                ),
                new Regulation(
                        "Reg S-K Item 303",
                        "Management's Discussion and Analysis",
                        "SEC",
                        "Requires discussion of company's financial condition and results of operations",
                        "https://www.sec.gov/cgi-bin/browse-edgar"
                )
        );
    }

    @Tool("Check if a company has any recent SEC enforcement actions or compliance violations")
    public ComplianceStatus checkComplianceStatus(String ticker) {
        log.info("Checking compliance status for: {}", ticker);
        // Production: query SEC enforcement actions database
        return new ComplianceStatus(ticker, true, List.of(), "No recent enforcement actions found");
    }

    @Tool("Get insider trading disclosure data for a company (Form 4 filings)")
    public List<InsiderTrade> getInsiderTrades(String ticker, String startDate) {
        log.info("Fetching insider trades for {} since {}", ticker, startDate);
        return List.of(
                new InsiderTrade(ticker, "Tim Cook", "CEO", "BUY", 50000, 185.50, "2024-01-10"),
                new InsiderTrade(ticker, "Luca Maestri", "CFO", "SELL", 30000, 188.20, "2024-01-05")
        );
    }

    public record Filing(String ticker, String formType, String filedDate, String url, String description) {}
    public record Regulation(String code, String name, String authority, String description, String sourceUrl) {}
    public record ComplianceStatus(String ticker, boolean compliant, List<String> violations, String summary) {}
    public record InsiderTrade(String ticker, String person, String role, String transactionType,
                               int shares, double price, String date) {}
}
