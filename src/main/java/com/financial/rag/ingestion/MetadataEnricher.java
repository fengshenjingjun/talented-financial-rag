package com.financial.rag.ingestion;

import com.financial.rag.model.FinancialDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class MetadataEnricher {

    private static final Pattern TICKER_IN_FILENAME = Pattern.compile("^([A-Z]{1,5})[_-]");
    private static final Pattern TICKER_IN_TEXT =
            Pattern.compile("(?:NYSE|NASDAQ|ticker symbol)[:\\s]+([A-Z]{1,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(?:filed|date|period)[:\\s]+(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\w+ \\d{1,2},? \\d{4})",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_IN_FILENAME = Pattern.compile("(20\\d{2})");

    public FinancialDocument.DocumentType detectDocumentType(String fileName, String content) {
        String combined = (fileName + " " + content.substring(0, Math.min(2000, content.length()))).toLowerCase();

        if (combined.contains("10-k") || combined.contains("annual report")) {
            return FinancialDocument.DocumentType.ANNUAL_REPORT;
        } else if (combined.contains("10-q") || combined.contains("quarterly report")) {
            return FinancialDocument.DocumentType.QUARTERLY_REPORT;
        } else if (combined.contains("8-k") || combined.contains("current report")) {
            return FinancialDocument.DocumentType.REGULATORY_FILING;
        } else if (combined.contains("prospectus") || combined.contains("s-1")) {
            return FinancialDocument.DocumentType.PROSPECTUS;
        } else if (combined.contains("earnings call") || combined.contains("conference call")) {
            return FinancialDocument.DocumentType.EARNINGS_CALL;
        } else if (combined.contains("contract") || combined.contains("agreement")) {
            return FinancialDocument.DocumentType.FINANCIAL_CONTRACT;
        } else {
            return FinancialDocument.DocumentType.MARKET_ANALYSIS;
        }
    }

    public String extractTicker(String fileName, String content) {
        // Try filename first (e.g., "AAPL_10K_2023.pdf")
        Matcher filenameMatcher = TICKER_IN_FILENAME.matcher(fileName.toUpperCase());
        if (filenameMatcher.find()) {
            return filenameMatcher.group(1);
        }

        // Try document content
        Matcher textMatcher = TICKER_IN_TEXT.matcher(content.substring(0, Math.min(5000, content.length())));
        if (textMatcher.find()) {
            return textMatcher.group(1).toUpperCase();
        }

        return "";
    }

    public String extractFilingDate(String fileName, String content) {
        // Try to extract from filename
        Matcher yearMatcher = YEAR_IN_FILENAME.matcher(fileName);
        if (yearMatcher.find()) {
            return yearMatcher.group(1);
        }

        // Try to extract from document header
        String header = content.substring(0, Math.min(3000, content.length()));
        Matcher dateMatcher = DATE_PATTERN.matcher(header);
        if (dateMatcher.find()) {
            return normalizeDate(dateMatcher.group(1));
        }

        return LocalDate.now().toString();
    }

    private String normalizeDate(String rawDate) {
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("M-d-yyyy"),
                DateTimeFormatter.ofPattern("MMMM d, yyyy"),
                DateTimeFormatter.ofPattern("MMMM d yyyy"),
                DateTimeFormatter.ofPattern("MMM d, yyyy")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(rawDate.trim(), formatter).toString();
            } catch (DateTimeParseException ignored) {}
        }

        return rawDate.trim();
    }
}
