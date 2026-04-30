package com.financial.rag.retrieval;

import com.financial.rag.config.MilvusConfig;
import com.financial.rag.model.FinancialDocument;
import com.financial.rag.model.RetrievalResult;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
//import io.milvus.param.FieldType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class MilvusConnector {

    private final MilvusServiceClient milvusClient;
    private final MilvusConfig milvusConfig;
    private final EmbeddingModel embeddingModel;

    private static final String VECTOR_FIELD = "vector";
    private static final String CONTENT_FIELD = "content";
    private static final String DOC_TYPE_FIELD = "document_type";
    private static final String TICKER_FIELD = "company_ticker";
    private static final String DATE_FIELD = "filing_date";
    private static final String URL_FIELD = "source_url";
    private static final String ACCESS_FIELD = "access_level";

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCollection() {
        String collection = milvusConfig.getCollectionName();
        boolean exists = milvusClient.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collection).build()
        ).getData();

        if (!exists) {
            log.info("Creating Milvus collection: {}", collection);
            createCollection(collection);
            createIndex(collection);
        }

        milvusClient.loadCollection(
                LoadCollectionParam.newBuilder().withCollectionName(collection).build()
        );
        log.info("Milvus collection loaded: {}", collection);
    }

    private void createCollection(String collectionName) {
        // Use withFieldTypes() directly — CollectionSchemaParam not needed
        List<FieldType> fields = List.of(
                FieldType.newBuilder()
                        .withName("id").withDataType(DataType.Int64)
                        .withPrimaryKey(true).withAutoID(true).build(),
                FieldType.newBuilder()
                        .withName(VECTOR_FIELD).withDataType(DataType.FloatVector)
                        .withDimension(MilvusConfig.EMBEDDING_DIM).build(),
                FieldType.newBuilder()
                        .withName(CONTENT_FIELD).withDataType(DataType.VarChar)
                        .withMaxLength(2000).build(),
                FieldType.newBuilder()
                        .withName(DOC_TYPE_FIELD).withDataType(DataType.VarChar)
                        .withMaxLength(50).build(),
                FieldType.newBuilder()
                        .withName(TICKER_FIELD).withDataType(DataType.VarChar)
                        .withMaxLength(10).build(),
                FieldType.newBuilder()
                        .withName(DATE_FIELD).withDataType(DataType.VarChar)
                        .withMaxLength(20).build(),
                FieldType.newBuilder()
                        .withName(URL_FIELD).withDataType(DataType.VarChar)
                        .withMaxLength(500).build(),
                FieldType.newBuilder()
                        .withName(ACCESS_FIELD).withDataType(DataType.VarChar)
                        .withMaxLength(20).build()
        );

        milvusClient.createCollection(
                CreateCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldTypes(fields)
                        .build()
        );
    }

    private void createIndex(String collectionName) {
        // withIndexType / withMetricType accept String in milvus-sdk-java 2.4.x
        milvusClient.createIndex(
                CreateIndexParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldName(VECTOR_FIELD)
                        .withIndexType(IndexType.HNSW)
                        .withMetricType(MetricType.COSINE)
                        .withExtraParam(String.format("{\"M\":%d,\"efConstruction\":%d}",
                                MilvusConfig.HNSW_M, MilvusConfig.HNSW_EF_CONSTRUCTION))
                        .build()
        );
    }

    // Batch insert: 1000–5000 vectors per operation per architecture spec
    public void batchInsert(List<FinancialDocument> documents) {
        if (documents.isEmpty()) return;

        int batchSize = 1000;
        for (int i = 0; i < documents.size(); i += batchSize) {
            List<FinancialDocument> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
            insertBatch(batch);
            log.info("Inserted batch {}/{}", (i / batchSize) + 1,
                    (documents.size() + batchSize - 1) / batchSize);
        }
    }

    private void insertBatch(List<FinancialDocument> docs) {
        List<List<Float>> vectors = docs.stream()
                .map(d -> toFloatList(d.getVector()))
                .collect(Collectors.toList());

        List<InsertParam.Field> fields = List.of(
                new InsertParam.Field(VECTOR_FIELD, vectors),
                new InsertParam.Field(CONTENT_FIELD,
                        docs.stream().map(FinancialDocument::getContent).toList()),
                new InsertParam.Field(DOC_TYPE_FIELD,
                        docs.stream().map(d -> d.getDocumentType() != null
                                ? d.getDocumentType().name() : "UNKNOWN").toList()),
                new InsertParam.Field(TICKER_FIELD,
                        docs.stream().map(d -> d.getCompanyTicker() != null
                                ? d.getCompanyTicker() : "").toList()),
                new InsertParam.Field(DATE_FIELD,
                        docs.stream().map(d -> d.getFilingDate() != null
                                ? d.getFilingDate() : "").toList()),
                new InsertParam.Field(URL_FIELD,
                        docs.stream().map(d -> d.getSourceUrl() != null
                                ? d.getSourceUrl() : "").toList()),
                new InsertParam.Field(ACCESS_FIELD,
                        docs.stream().map(d -> d.getAccessLevel() != null
                                ? d.getAccessLevel() : "PUBLIC").toList())
        );

        milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withFields(fields)
                .build());
    }

    public List<RetrievalResult> search(String query, Map<String, String> filters, int topK) {
        float[] queryVector = embeddingModel.embed(query).content().vector();
        String filterExpr = buildFilterExpression(filters);

        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(milvusConfig.getCollectionName())
                .withVectorFieldName(VECTOR_FIELD)
                .withFloatVectors(List.of(toFloatList(queryVector)))
                .withTopK(topK)
                .withMetricType(MetricType.COSINE)   // String "COSINE"
                .withOutFields(List.of(CONTENT_FIELD, DOC_TYPE_FIELD, TICKER_FIELD, DATE_FIELD, URL_FIELD))
                .withParams("{\"ef\":64}");

        if (!filterExpr.isBlank()) {
            builder.withExpr(filterExpr);
        }

        R<SearchResults> response = milvusClient.search(builder.build());

        if (response.getData() == null) {
            log.warn("Milvus search returned no results");
            return List.of();
        }

        return parseSearchResults(response.getData(), topK);
    }

    @Async
    public CompletableFuture<List<RetrievalResult>> searchAsync(String query,
                                                                 Map<String, String> filters,
                                                                 int topK) {
        return CompletableFuture.completedFuture(search(query, filters, topK));
    }

    private String buildFilterExpression(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return "";

        List<String> conditions = new ArrayList<>();
        if (filters.containsKey("company_ticker")) {
            String tickerExpr = Arrays.stream(filters.get("company_ticker").split(","))
                    .map(t -> TICKER_FIELD + " == \"" + t.trim() + "\"")
                    .collect(Collectors.joining(" || "));
            conditions.add("(" + tickerExpr + ")");
        }
        if (filters.containsKey("document_type")) {
            conditions.add(DOC_TYPE_FIELD + " == \"" + filters.get("document_type") + "\"");
        }
        if (filters.containsKey("access_level")) {
            conditions.add(ACCESS_FIELD + " == \"" + filters.get("access_level") + "\"");
        }
        return String.join(" && ", conditions);
    }

    // Use getFieldData() per result index — correct API for milvus-sdk-java 2.4.x
    private List<RetrievalResult> parseSearchResults(SearchResults results, int topK) {
        SearchResultsWrapper wrapper = new SearchResultsWrapper(results.getResults());
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

        if (idScores == null || idScores.isEmpty()) return List.of();

        // Fetch all field columns up front (one call per field)
        List<Object> contents  = safeFieldData(wrapper, CONTENT_FIELD);
        List<Object> docTypes  = safeFieldData(wrapper, DOC_TYPE_FIELD);
        List<Object> tickers   = safeFieldData(wrapper, TICKER_FIELD);
        List<Object> dates     = safeFieldData(wrapper, DATE_FIELD);
        List<Object> urls      = safeFieldData(wrapper, URL_FIELD);

        List<RetrievalResult> retrievalResults = new ArrayList<>();
        for (int i = 0; i < idScores.size(); i++) {
            SearchResultsWrapper.IDScore idScore = idScores.get(i);
            retrievalResults.add(RetrievalResult.builder()
                    .documentId(String.valueOf(idScore.getLongID()))
                    .score(idScore.getScore())
                    .rerankScore(idScore.getScore())
                    .content(getString(contents, i))
                    .documentType(getString(docTypes, i))
                    .companyTicker(getString(tickers, i))
                    .filingDate(getString(dates, i))
                    .sourceUrl(getString(urls, i))
                    .build());
        }
        return retrievalResults;
    }

    private List<Object> safeFieldData(SearchResultsWrapper wrapper, String field) {
        try {
            return Collections.singletonList(wrapper.getFieldData(field, 0));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String getString(List<Object> list, int index) {
        if (list == null || index >= list.size()) return "";
        Object val = list.get(index);
        return val != null ? val.toString() : "";
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }
}
