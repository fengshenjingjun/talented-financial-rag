package com.financial.rag.ingestion;

import com.financial.rag.model.FinancialDocument;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Structure-aware chunking:
 * - Chunk size: 500-1000 chars with 15% overlap per architecture spec
 * - Preserves table boundaries (TABLE: prefix blocks)
 * - Splits at semantic boundaries (paragraphs, sentences)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SmartChunker {

    private final EmbeddingModel embeddingModel;

    @Value("${chunking.chunk-size:800}")
    private int chunkSize;

    @Value("${chunking.overlap-ratio:0.15}")
    private double overlapRatio;

    public List<FinancialDocument> chunk(FinancialDocument document) {
        String content = document.getContent();
        List<FinancialDocument> chunks = new ArrayList<>();

        // Tables get their own chunk (never split mid-table)
        if (content.startsWith("TABLE:")) {
            chunks.add(withChunkIndex(document, 0, content));
            return chunks;
        }

        List<String> textChunks = splitIntoChunks(content);
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkContent = textChunks.get(i);
            float[] vector = embeddingModel.embed(chunkContent).content().vector();

            FinancialDocument chunk = FinancialDocument.builder()
                    .id(document.getId() + "_chunk_" + i)
                    .content(chunkContent)
                    .documentType(document.getDocumentType())
                    .companyTicker(document.getCompanyTicker())
                    .filingDate(document.getFilingDate())
                    .sourceUrl(document.getSourceUrl())
                    .accessLevel(document.getAccessLevel())
                    .vector(vector)
                    .build();
            chunks.add(chunk);
        }

        log.debug("Chunked document {} into {} chunks", document.getId(), chunks.size());
        return chunks;
    }

    private List<String> splitIntoChunks(String text) {
        int overlap = (int) (chunkSize * overlapRatio);
        List<String> chunks = new ArrayList<>();

        // Split at paragraph boundaries first
        String[] paragraphs = text.split("\n\n+");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                // Carry over overlap: keep last `overlap` chars of current chunk
                String tail = current.toString();
                current = new StringBuilder(tail.length() > overlap
                        ? tail.substring(tail.length() - overlap)
                        : tail);
                current.append("\n\n");
            }
            current.append(paragraph).append("\n\n");
        }

        if (!current.toString().trim().isEmpty()) {
            chunks.add(current.toString().trim());
        }

        // If any chunk is still too long, split at sentence boundaries
        List<String> finalChunks = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.length() > chunkSize * 1.5) {
                finalChunks.addAll(splitBySentence(chunk, overlap));
            } else {
                finalChunks.add(chunk);
            }
        }

        return finalChunks;
    }

    private List<String> splitBySentence(String text, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                String tail = current.toString();
                current = new StringBuilder(tail.length() > overlap
                        ? tail.substring(tail.length() - overlap)
                        : tail);
                current.append(" ");
            }
            current.append(sentence).append(" ");
        }

        if (!current.toString().trim().isEmpty()) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    private FinancialDocument withChunkIndex(FinancialDocument doc, int index, String content) {
        return FinancialDocument.builder()
                .id(doc.getId() + "_chunk_" + index)
                .content(content)
                .documentType(doc.getDocumentType())
                .companyTicker(doc.getCompanyTicker())
                .filingDate(doc.getFilingDate())
                .sourceUrl(doc.getSourceUrl())
                .accessLevel(doc.getAccessLevel())
                .vector(embeddingModel.embed(content).content().vector())
                .build();
    }
}
