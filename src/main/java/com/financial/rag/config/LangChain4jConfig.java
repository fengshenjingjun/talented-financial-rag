package com.financial.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.llm-model:qwen2.5:72b}")
    private String llmModel;

    @Value("${ollama.small-model:qwen2.5:14b}")
    private String smallModel;

    @Value("${ollama.embedding-model:bge-m3}")
    private String embeddingModel;

    // Primary large LLM for final answer synthesis
    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(llmModel)
                .temperature(0.1)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    // Smaller model for query rewriting and intent classification (token efficiency)
    @Bean("smallChatModel")
    public ChatLanguageModel smallChatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(smallModel)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModel)
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
