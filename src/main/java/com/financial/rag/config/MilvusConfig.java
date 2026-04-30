package com.financial.rag.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.collection-name:financial_documents}")
    private String collectionName;

    @Value("${milvus.username:}")
    private String username;

    @Value("${milvus.password:}")
    private String password;

    // HNSW index params per architecture spec (M=16, efConstruction=200)
    public static final int HNSW_M = 16;
    public static final int HNSW_EF_CONSTRUCTION = 200;
    public static final int EMBEDDING_DIM = 1024;  // BGE-M3 output dimension

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withConnectTimeout(10, TimeUnit.SECONDS)
                .withKeepAliveTime(55, TimeUnit.SECONDS)
                .withKeepAliveTimeout(20, TimeUnit.SECONDS)
                .withRpcDeadline(10, TimeUnit.MILLISECONDS);

        if (!username.isBlank()) {
            builder.withAuthorization(username, password);
        }

        return new MilvusServiceClient(builder.build());
    }

    public String getCollectionName() {
        return collectionName;
    }
}
