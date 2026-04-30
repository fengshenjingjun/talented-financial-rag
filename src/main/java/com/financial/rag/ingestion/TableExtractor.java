package com.financial.rag.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class TableExtractor {

    private final ObjectMapper objectMapper;

    // Detects lines that look like financial table rows: columns separated by multiple spaces or tabs
    private static final Pattern TABLE_ROW_PATTERN =
            Pattern.compile("^(.+?)\\s{2,}([\\d,.()+\\-$%]+(?:\\s{2,}[\\d,.()+\\-$%]+)*)$");

    // Matches section headers like "Balance Sheet", "Income Statement"
    private static final Pattern TABLE_HEADER_PATTERN =
            Pattern.compile("(?i)(balance sheet|income statement|cash flow|earnings|revenue|profit|loss|equity)");

    public List<String> extractTables(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".pdf")) {
            return List.of();
        }

        String text = extractPdfText(filePath);
        return detectAndConvertTables(text);
    }

    private String extractPdfText(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private List<String> detectAndConvertTables(String text) {
        List<String> tables = new ArrayList<>();
        String[] lines = text.split("\n");

        List<String> currentTableLines = new ArrayList<>();
        String currentHeader = "Financial Data";
        boolean inTable = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (inTable && currentTableLines.size() >= 3) {
                    tables.add(convertTableToJson(currentHeader, currentTableLines));
                    currentTableLines.clear();
                    inTable = false;
                }
                continue;
            }

            Matcher headerMatcher = TABLE_HEADER_PATTERN.matcher(trimmed);
            if (headerMatcher.find()) {
                currentHeader = trimmed;
            }

            Matcher rowMatcher = TABLE_ROW_PATTERN.matcher(trimmed);
            if (rowMatcher.matches()) {
                inTable = true;
                currentTableLines.add(trimmed);
            } else if (inTable) {
                if (currentTableLines.size() >= 3) {
                    tables.add(convertTableToJson(currentHeader, currentTableLines));
                }
                currentTableLines.clear();
                inTable = false;
            }
        }

        if (inTable && currentTableLines.size() >= 3) {
            tables.add(convertTableToJson(currentHeader, currentTableLines));
        }

        return tables;
    }

    private String convertTableToJson(String header, List<String> rows) {
        try {
            List<Map<String, String>> tableData = new ArrayList<>();
            for (String row : rows) {
                String[] parts = row.split("\\s{2,}");
                if (parts.length >= 2) {
                    Map<String, String> rowMap = new LinkedHashMap<>();
                    rowMap.put("label", parts[0].trim());
                    for (int i = 1; i < parts.length; i++) {
                        rowMap.put("value_" + i, parts[i].trim());
                    }
                    tableData.add(rowMap);
                }
            }

            Map<String, Object> table = new LinkedHashMap<>();
            table.put("table_name", header);
            table.put("data", tableData);

            return "TABLE: " + header + "\n" + objectMapper.writeValueAsString(table);
        } catch (Exception e) {
            log.warn("Failed to convert table to JSON: {}", header, e);
            return "TABLE: " + header + "\n" + String.join("\n", rows);
        }
    }
}
