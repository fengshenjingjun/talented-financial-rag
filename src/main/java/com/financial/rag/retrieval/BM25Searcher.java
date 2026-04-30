package com.financial.rag.retrieval;

import com.financial.rag.model.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class BM25Searcher {

    private final Directory directory;
    private final StandardAnalyzer analyzer;
    private IndexWriter indexWriter;
    private DirectoryReader directoryReader;
    private IndexSearcher indexSearcher;

    public BM25Searcher() throws IOException {
        this.directory = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(new BM25Similarity(1.2f, 0.75f));
        this.indexWriter = new IndexWriter(directory, config);
        refreshSearcher();
    }

    public void indexDocument(String docId, String content, String ticker, String docType, String date) {
        try {
            Document doc = new Document();
            doc.add(new StoredField("id", docId));
            doc.add(new TextField("content", content, Field.Store.YES));
            doc.add(new StoredField("ticker", ticker != null ? ticker : ""));
            doc.add(new StoredField("docType", docType != null ? docType : ""));
            doc.add(new StoredField("date", date != null ? date : ""));
            indexWriter.addDocument(doc);
            indexWriter.commit();
            refreshSearcher();
        } catch (IOException e) {
            log.error("Failed to index document {}", docId, e);
        }
    }

    public List<RetrievalResult> search(String query, Map<String, String> filters, int topK) {
        try {
            QueryParser parser = new QueryParser("content", analyzer);
            Query luceneQuery = parser.parse(QueryParser.escape(query));

            TopDocs topDocs = indexSearcher.search(luceneQuery, topK);
            List<RetrievalResult> results = new ArrayList<>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = indexSearcher.storedFields().document(scoreDoc.doc);
                results.add(RetrievalResult.builder()
                        .documentId(doc.get("id"))
                        .content(doc.get("content"))
                        .score(scoreDoc.score)
                        .rerankScore(scoreDoc.score)
                        .companyTicker(doc.get("ticker"))
                        .documentType(doc.get("docType"))
                        .filingDate(doc.get("date"))
                        .build());
            }

            return results;
        } catch (Exception e) {
            log.warn("BM25 search failed for query: {}", query, e);
            return List.of();
        }
    }

    @Async
    public CompletableFuture<List<RetrievalResult>> searchAsync(String query,
                                                                  Map<String, String> filters,
                                                                  int topK) {
        return CompletableFuture.completedFuture(search(query, filters, topK));
    }

    private void refreshSearcher() throws IOException {
        if (directoryReader == null) {
            try {
                directoryReader = DirectoryReader.open(directory);
            } catch (IndexNotFoundException e) {
                indexWriter.commit();
                directoryReader = DirectoryReader.open(directory);
            }
        } else {
            DirectoryReader newReader = DirectoryReader.openIfChanged(directoryReader);
            if (newReader != null) {
                directoryReader.close();
                directoryReader = newReader;
            }
        }
        indexSearcher = new IndexSearcher(directoryReader);
        indexSearcher.setSimilarity(new BM25Similarity(1.2f, 0.75f));
    }
}
