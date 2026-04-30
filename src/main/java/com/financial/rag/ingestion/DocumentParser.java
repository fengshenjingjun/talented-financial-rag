package com.financial.rag.ingestion;

import com.financial.rag.model.FinancialDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentParser {

    private final TableExtractor tableExtractor;
    private final MetadataEnricher metadataEnricher;

    public List<FinancialDocument> parseFile(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        String rawText;

        if (fileName.endsWith(".pdf")) {
            rawText = parsePdf(filePath);
        } else if (fileName.endsWith(".docx")) {
            rawText = parseDocx(filePath);
        } else if (fileName.endsWith(".txt")) {
            rawText = Files.readString(filePath);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileName);
        }

        // Extract tables separately before chunking
        List<String> tables = tableExtractor.extractTables(filePath);
        log.info("Extracted {} tables from {}", tables.size(), fileName);

        // Enrich with domain metadata
        FinancialDocument.DocumentType docType = metadataEnricher.detectDocumentType(fileName, rawText);
        String ticker = metadataEnricher.extractTicker(fileName, rawText);
        String filingDate = metadataEnricher.extractFilingDate(fileName, rawText);

        List<FinancialDocument> docs = new ArrayList<>();

        // Add main text document
        docs.add(FinancialDocument.builder()
                .id(generateId(filePath))
                .content(rawText)
                .documentType(docType)
                .companyTicker(ticker)
                .filingDate(filingDate)
                .sourceUrl(filePath.toUri().toString())
                .accessLevel("PUBLIC")
                .build());

        // Add extracted tables as separate documents for better retrieval
        for (int i = 0; i < tables.size(); i++) {
            docs.add(FinancialDocument.builder()
                    .id(generateId(filePath) + "_table_" + i)
                    .content(tables.get(i))
                    .documentType(docType)
                    .companyTicker(ticker)
                    .filingDate(filingDate)
                    .sourceUrl(filePath.toUri().toString())
                    .accessLevel("PUBLIC")
                    .build());
        }

        return docs;
    }

    private String parsePdf(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);  // preserve reading order for multi-column layouts
            return stripper.getText(document);
        }
    }

    private String parseDocx(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            document.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            return sb.toString();
        }
    }

    private String generateId(Path filePath) {
        return filePath.getFileName().toString()
                .replaceAll("[^a-zA-Z0-9]", "_")
                .toLowerCase();
    }
}
