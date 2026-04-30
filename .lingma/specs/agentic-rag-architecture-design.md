# Financial Agentic-RAG System Architecture Design

## Context

This document outlines the architecture for a **Financial Domain Agentic-RAG System** built with LangChain4j and Milvus. The system is designed to provide intelligent, accurate, and compliant question-answering capabilities for financial documents including regulatory filings, financial reports, contracts, and market analysis.

**Key Requirements:**
- Domain: Financial services (regulatory compliance, risk analysis, financial reporting)
- Core Agent Capabilities: Task decomposition, tool calling, intelligent retrieval optimization
- Build Tool: Gradle
- Innovation Focus: Performance optimization (retrieval accuracy, response latency, resource efficiency)

---

## Recommended Architecture

### High-Level Design

The system follows a **Supervisor-Worker Multi-Agent Pattern** with optimized retrieval pipeline:

```
User Query → Query Analyzer → Supervisor Agent → Worker Agents → Answer Synthesis
                  ↓                    ↓
            Query Rewriting      Task Decomposition
            Intent Detection     Parallel Execution
                                      ↓
                              Retrieval Pipeline
                              (Hybrid Search + Rerank)
```

### Core Components

#### 1. Query Understanding Layer

**Purpose**: Transform raw user queries into optimized retrieval requests

**Components:**
- **Intent Classifier**: Categorize queries (factual lookup, comparative analysis, trend analysis, risk assessment)
- **Query Rewriter**: Apply three strategies:
  - Step-Back Prompting: Generate abstract queries for foundational context
  - HyDE (Hypothetical Document Embeddings): Create fictional ideal answers for better semantic matching
  - Query Expansion: Generate multiple reformulated queries covering different aspects
- **Domain Term Enhancer**: Expand financial jargon with synonyms and related concepts

**Implementation:**
```
src/main/java/com/financial/rag/query/
├── QueryAnalyzer.java           // Main orchestrator
├── IntentClassifier.java        // LLM-based classification
├── QueryRewriter.java           // Step-back, HyDE, expansion
└── FinancialTermExpander.java   // Domain-specific enhancement
```

#### 2. Supervisor Agent

**Purpose**: Decompose complex queries and coordinate worker agents

**Responsibilities:**
- Parse multi-part questions into independent sub-tasks
- Route sub-tasks to specialized worker agents
- Aggregate results with conflict resolution
- Handle retry logic for failed retrievals

**Example Decomposition:**
```
Query: "Compare the debt-to-equity ratios of Company A and B over 2023-2025"

Sub-tasks:
1. [Worker: DataRetrieval] Retrieve Company A financial statements 2023-2025
2. [Worker: DataRetrieval] Retrieve Company B financial statements 2023-2025
3. [Worker: MetricCalculator] Extract and calculate debt-to-equity ratios
4. [Worker: TrendAnalyzer] Perform year-over-year comparison
5. [Worker: AnswerSynthesizer] Generate comparative analysis report
```

**Implementation:**
```
src/main/java/com/financial/rag/agent/
├── SupervisorAgent.java         // Central coordinator with @AiServices
├── SupervisorPlanner.java       // Task decomposition logic
└── ResultAggregator.java        // Merge worker outputs
```

#### 3. Worker Agents (Specialized)

**DataRetrievalAgent**:
- Execute vector searches against Milvus
- Apply metadata filters (date range, document type, company ticker)
- Return ranked document chunks

**MetricCalculatorAgent**:
- Extract structured data from retrieved text (tables, financial figures)
- Calculate ratios, growth rates, volatility metrics
- Return structured JSON with computed values

**TrendAnalyzerAgent**:
- Perform time-series analysis on extracted metrics
- Identify patterns (increasing/decreasing trends, anomalies)
- Generate statistical summaries

**ComplianceCheckerAgent**:
- Validate answers against regulatory requirements
- Flag potential compliance issues
- Add disclaimers for uncertain information

**AnswerSynthesizerAgent**:
- Combine structured data and narrative explanations
- Generate final response with citations
- Ensure answer faithfulness to retrieved context

**Implementation:**
```
src/main/java/com/financial/rag/agent/workers/
├── DataRetrievalAgent.java
├── MetricCalculatorAgent.java
├── TrendAnalyzerAgent.java
├── ComplianceCheckerAgent.java
└── AnswerSynthesizerAgent.java
```

#### 4. Optimized Retrieval Pipeline (Performance Innovation)

**Three-Stage Retrieval Architecture:**

**Stage 1: Candidate Generation (Fast)**
- Hybrid search combining:
  - Vector similarity (BGE-M3 embeddings) via Milvus HNSW index
  - BM25 keyword search on original text
- Retrieve top-50 candidates
- Apply metadata filters (date, source, access control)

**Stage 2: Precision Reranking (Accurate)**
- Cross-encoder reranker (BGE-Reranker or domain-finetuned model)
- Score query-document pairs for answer relevance
- Reduce top-50 → top-5 highest quality chunks
- Target: 35-49% retrieval failure reduction

**Stage 3: Quality Gating (Reliable)**
- Relevance threshold check (score > 0.7)
- If below threshold: trigger re-retrieval with expanded query
- Maximum 2 retry iterations to control latency

**Performance Optimizations:**
- Batch insert: 1000-5000 vectors per operation
- Milvus HNSW parameters: M=16, efConstruction=200
- Async retrieval for parallel sub-task execution
- Connection pooling for MilvusClient

**Implementation:**
```
src/main/java/com/financial/rag/retrieval/
├── HybridRetriever.java         // Vector + BM25 fusion
├── RerankerService.java         // Cross-encoder scoring
├── QualityGate.java             // Threshold-based validation
└── MilvusConnector.java         // Optimized Milvus client
```

#### 5. Document Ingestion Pipeline

**Structure-Aware Chunking:**
- Preserve table structures (balance sheets, income statements)
- Maintain reading order for multi-column layouts
- Recursive character splitting at semantic boundaries
- Chunk size: 500-1000 characters with 15% overlap

**Financial Document Processing:**
- Table extraction with row/column preservation
- Convert tables to structured JSON before chunking
- Extract metadata: document date, company ticker, filing type
- Handle cross-page paragraph continuation

**Implementation:**
```
src/main/java/com/financial/rag/ingestion/
├── DocumentParser.java          // PDF/Word parsing
├── TableExtractor.java          // Structured table processing
├── SmartChunker.java            // Semantic-aware splitting
└── MetadataEnricher.java        // Add domain tags, dates, sources
```

#### 6. Tool Calling Framework

**Available Tools:**
```java
@Tool("Retrieve real-time stock price for given ticker")
StockPrice getStockPrice(String ticker);

@Tool("Fetch historical financial statements")
FinancialStatement getFinancialStatement(String ticker, String period);

@Tool("Calculate financial ratios from balance sheet data")
Map<String, Double> calculateRatios(String balanceSheetJson);

@Tool("Query regulatory database for compliance rules")
List<Regulation> searchRegulations(String keyword);
```

**Integration:**
- Register tools with `@AiServices` builder
- Automatic function calling handled by LangChain4j
- Tools return structured POJOs/JSON for LLM consumption

**Implementation:**
```
src/main/java/com/financial/rag/tools/
├── MarketDataTools.java         // Stock prices, market indices
├── FinancialDataTools.java      // Statements, ratios, metrics
└── RegulatoryTools.java         // Compliance rules, regulations
```

#### 7. Evaluation & Monitoring

**Ragas Metrics Integration:**
- **Faithfulness**: Answer derives strictly from retrieved context
- **Answer Relevancy**: Response addresses the specific question
- **Context Precision**: Retrieved documents are actually relevant
- **Context Recall**: No critical information missed in retrieval

**Operational Monitoring:**
- Retrieval hit rates per document type
- Reranking effectiveness (before/after score distribution)
- Hallucination detection via automated fact-checking
- Latency tracking per pipeline stage
- Token consumption monitoring

**Implementation:**
```
src/main/java/com/financial/rag/evaluation/
├── RagasEvaluator.java          // Automated quality metrics
├── LatencyTracker.java          // Per-stage performance monitoring
└── AuditLogger.java             // Immutable query/response logs
```

---

## Technology Stack

| Component | Choice | Version | Rationale |
|-----------|--------|---------|-----------|
| **Framework** | LangChain4j | 1.10+ | Mature Java ecosystem, Spring Boot integration, declarative @AiServices |
| **Vector DB** | Milvus | 2.4+ | High-performance at scale, native Java SDK, hybrid search support |
| **Build Tool** | Gradle | 8.x | Modern dependency management, Kotlin DSL support |
| **Embedding Model** | BGE-M3 | Latest | Strong multilingual support, optimized for financial text |
| **Reranker** | BGE-Reranker | Latest | Proven 35-49% retrieval failure reduction |
| **LLM** | Qwen-72B / Llama-3-70B | Local deployment | Strong reasoning, bilingual, on-premises for compliance |
| **Web Framework** | Spring Boot | 3.2+ | Enterprise-grade, extensive ecosystem |
| **Evaluation** | Ragas | Latest | Comprehensive RAG-specific metrics |

---

## Project Structure

```
talented-financial-rag/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/com/financial/rag/
│   │   │   ├── FinancialRagApplication.java      // Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   ├── MilvusConfig.java              // Milvus connection setup
│   │   │   │   ├── LangChain4jConfig.java         // LLM, embedding model config
│   │   │   │   └── SecurityConfig.java            // Access control, RBAC
│   │   │   ├── query/
│   │   │   │   ├── QueryAnalyzer.java
│   │   │   │   ├── IntentClassifier.java
│   │   │   │   ├── QueryRewriter.java
│   │   │   │   └── FinancialTermExpander.java
│   │   │   ├── agent/
│   │   │   │   ├── SupervisorAgent.java
│   │   │   │   ├── SupervisorPlanner.java
│   │   │   │   ├── ResultAggregator.java
│   │   │   │   └── workers/
│   │   │   │       ├── DataRetrievalAgent.java
│   │   │   │       ├── MetricCalculatorAgent.java
│   │   │   │       ├── TrendAnalyzerAgent.java
│   │   │   │       ├── ComplianceCheckerAgent.java
│   │   │   │       └── AnswerSynthesizerAgent.java
│   │   │   ├── retrieval/
│   │   │   │   ├── HybridRetriever.java
│   │   │   │   ├── RerankerService.java
│   │   │   │   ├── QualityGate.java
│   │   │   │   └── MilvusConnector.java
│   │   │   ├── ingestion/
│   │   │   │   ├── DocumentParser.java
│   │   │   │   ├── TableExtractor.java
│   │   │   │   ├── SmartChunker.java
│   │   │   │   └── MetadataEnricher.java
│   │   │   ├── tools/
│   │   │   │   ├── MarketDataTools.java
│   │   │   │   ├── FinancialDataTools.java
│   │   │   │   └── RegulatoryTools.java
│   │   │   ├── evaluation/
│   │   │   │   ├── RagasEvaluator.java
│   │   │   │   ├── LatencyTracker.java
│   │   │   │   └── AuditLogger.java
│   │   │   ├── model/
│   │   │   │   ├── QueryContext.java
│   │   │   │   ├── RetrievalResult.java
│   │   │   │   ├── FinancialDocument.java
│   │   │   │   └── AgentResponse.java
│   │   │   └── controller/
│   │   │       ├── RagController.java               // REST API endpoints
│   │   │       └── AdminController.java             // Monitoring, admin ops
│   │   └── resources/
│   │       ├── application.yml                      // Spring Boot config
│   │       ├── prompts/                             // LLM prompt templates
│   │       │   ├── supervisor-system-prompt.txt
│   │       │   ├── query-rewriting-prompt.txt
│   │       │   └── answer-synthesis-prompt.txt
│   │       └── schemas/                             // Milvus collection schemas
│   │           └── financial-docs-schema.json
│   └── test/
│       ├── java/com/financial/rag/
│       │   ├── retrieval/
│       │   │   └── HybridRetrieverTest.java
│       │   ├── agent/
│       │   │   └── SupervisorAgentTest.java
│       │   └── evaluation/
│       │       └── RagasEvaluatorTest.java
│       └── resources/
│           └── test-documents/                      // Sample financial PDFs
├── docker/
│   ├── docker-compose.yml                           // Milvus + dependencies
│   └── milvus-config.yaml
└── docs/
    ├── API.md                                       // REST API documentation
    └── DEPLOYMENT.md                                // Production deployment guide
```

---

## Key Implementation Details

### 1. Gradle Build Configuration

**build.gradle.kts highlights:**
```kotlin
dependencies {
    // LangChain4j core
    implementation("dev.langchain4j:langchain4j:1.10.0")
    implementation("dev.langchain4j:langchain4j-milvus:1.10.0")
    implementation("dev.langchain4j:langchain4j-spring-boot-starter:1.10.0")
    
    // Milvus Java SDK
    implementation("io.milvus:milvus-sdk-java:2.4.0")
    
    // Embedding & Reranking models (local deployment)
    implementation("dev.langchain4j:langchain4j-ollama:1.10.0")
    
    // Document parsing
    implementation("org.apache.pdfbox:pdfbox:3.0.1")
    implementation("tech.tablesaw:tablesaw-core:0.43.1")
    
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

### 2. Milvus Collection Schema

**financial-docs-schema.json:**
```json
{
  "collection_name": "financial_documents",
  "fields": [
    {"name": "id", "type": "INT64", "is_primary": true},
    {"name": "vector", "type": "FLOAT_VECTOR", "dim": 1024},
    {"name": "content", "type": "VARCHAR", "max_length": 2000},
    {"name": "document_type", "type": "VARCHAR", "max_length": 50},
    {"name": "company_ticker", "type": "VARCHAR", "max_length": 10},
    {"name": "filing_date", "type": "VARCHAR", "max_length": 20},
    {"name": "source_url", "type": "VARCHAR", "max_length": 500},
    {"name": "access_level", "type": "VARCHAR", "max_length": 20}
  ],
  "index_params": {
    "metric_type": "COSINE",
    "index_type": "HNSW",
    "params": {"M": 16, "efConstruction": 200}
  }
}
```

### 3. Supervisor Agent Pattern

**SupervisorAgent.java (conceptual):**
```java
@AiService
public interface SupervisorAgent {
    
    @SystemMessage("""
        You are a financial analysis supervisor. Your job is to:
        1. Break down complex queries into sub-tasks
        2. Assign tasks to specialized worker agents
        3. Aggregate results into a coherent answer
        
        Available workers:
        - DataRetrievalAgent: Fetch documents from vector database
        - MetricCalculatorAgent: Compute financial ratios and metrics
        - TrendAnalyzerAgent: Analyze temporal patterns
        - ComplianceCheckerAgent: Validate regulatory compliance
        - AnswerSynthesizerAgent: Generate final response with citations
        
        For each sub-task, specify:
        - Worker type
        - Input parameters
        - Expected output format
        """)
    SupervisedResponse decomposeAndExecute(@UserMessage String query);
}

record SupervisedResponse(
    List<SubTask> subTasks,
    String finalAnswer,
    List<Citation> citations
) {}
```

### 4. Hybrid Retriever Implementation

**HybridRetriever.java (conceptual):**
```java
@Component
public class HybridRetriever {
    
    private final MilvusConnector milvusConnector;
    private final BM25Searcher bm25Searcher;
    private final RerankerService reranker;
    
    public List<RetrievalResult> retrieve(String query, Map<String, String> filters) {
        // Stage 1: Parallel candidate generation
        CompletableFuture<List<DocumentChunk>> vectorResults = 
            milvusConnector.searchAsync(query, filters, topK=50);
        
        CompletableFuture<List<DocumentChunk>> keywordResults = 
            bm25Searcher.searchAsync(query, filters, topK=30);
        
        // Merge and deduplicate
        List<DocumentChunk> candidates = mergeResults(
            vectorResults.join(), 
            keywordResults.join()
        );
        
        // Stage 2: Reranking
        List<RerankedChunk> reranked = reranker.rerank(query, candidates, topK=5);
        
        // Stage 3: Quality gating
        return qualityGate.filter(reranked, threshold=0.7);
    }
}
```

---

## Performance Optimization Strategies

### 1. Retrieval Pipeline Optimizations

**Target Metrics:**
- P95 latency: < 2s for simple queries, < 8s for complex multi-agent queries
- Retrieval precision@5: > 85%
- Hallucination rate: < 2%

**Optimization Techniques:**
- **Async Execution**: Parallel sub-task retrieval reduces overall latency by 40-60%
- **Caching**: Cache frequent queries with 1-hour TTL (Redis)
- **Batch Processing**: Group similar queries for batch embedding computation
- **Index Tuning**: Milvus HNSW with M=16, efConstruction=200 balances build time vs search speed

### 2. Token Efficiency

**Strategies:**
- Use smaller models (Qwen-14B) for query rewriting and intent classification
- Reserve large models (Qwen-72B) only for final answer synthesis
- Compress retrieved context by removing redundant chunks
- Implement progressive summarization for long documents

**Expected Savings:**
- 30-40% reduction in total token consumption
- 2x improvement in throughput (queries/second)

### 3. Memory Management

**Chat Memory Strategy:**
- Sliding window: Keep last 10 conversation turns
- Summarization: Compress older turns into concise summaries
- Session isolation: Separate memory per user session
- Persistence: Store conversation history in PostgreSQL for audit trail

---

## Compliance & Security

### 1. Access Control

- **Row-Level Security**: Users only retrieve documents matching their clearance level
- **Metadata Filtering**: Enforce department, role-based access at retrieval time
- **Query Validation**: Detect and block prompt injection attempts

### 2. Audit Trail

Every query logs:
- User ID, timestamp, query text
- Retrieved document IDs and relevance scores
- Generated answer with confidence score
- Total token consumption

Logs stored in immutable append-only database for regulatory examination.

### 3. Data Isolation

- Separate Milvus collections per tenant/department
- Encryption at rest (AES-256) and in transit (TLS 1.3)
- Secure key management for embedding models (HashiCorp Vault)

---

## Verification & Testing

### Unit Tests
- Test each worker agent independently with mock data
- Verify retrieval precision with golden dataset
- Validate tool calling with stubbed external APIs

### Integration Tests
- End-to-end query flow: query → retrieval → answer
- Test multi-agent coordination with complex queries
- Verify Milvus connection pooling and failover

### Evaluation Framework
- **Ragas Metrics**: Run automated evaluation on 100+ financial Q&A pairs
- **Human Review**: Domain experts validate 50 random responses monthly
- **A/B Testing**: Compare different retrieval strategies on live traffic

### Performance Benchmarks
- Load test with 100 concurrent users
- Measure P50, P95, P99 latencies
- Monitor Milvus QPS and memory usage

---

## Implementation Phases

### Phase 1: Foundation (Weeks 1-4)
- Set up Gradle project with Spring Boot
- Deploy Milvus cluster (Docker Compose for dev)
- Implement document ingestion pipeline
- Build basic single-retriever RAG
- Establish Ragas evaluation baseline

**Deliverables:**
- Working RAG system with basic retrieval
- Document parser for PDFs with table extraction
- Milvus collection schema and indexing

### Phase 2: Retrieval Optimization (Weeks 5-8)
- Implement hybrid search (vector + BM25)
- Add cross-encoder reranking
- Integrate metadata filtering
- Tune chunking strategies per document type
- Achieve target retrieval precision@5 > 80%

**Deliverables:**
- HybridRetriever with quality gating
- RerankerService integration
- Performance benchmarks showing 35%+ improvement

### Phase 3: Agentic Capabilities (Weeks 9-12)
- Build SupervisorAgent with task decomposition
- Implement worker agents (DataRetrieval, MetricCalculator, etc.)
- Add tool calling framework
- Implement ReAct loops for complex queries
- Enable async parallel execution

**Deliverables:**
- Multi-agent orchestration working end-to-end
- Tool calling for real-time market data
- Complex query handling (comparisons, trend analysis)

### Phase 4: Production Hardening (Weeks 13-16)
- Implement comprehensive audit logging
- Add access control and data isolation
- Deploy monitoring (Prometheus + Grafana)
- Conduct security review and penetration testing
- Optimize for production workloads

**Deliverables:**
- Production-ready deployment on Kubernetes
- Compliance documentation for regulatory review
- Operational runbooks and incident response procedures

---

## Critical Files to Modify/Create

### Core Implementation Files
1. `build.gradle.kts` - Project dependencies and build configuration
2. `src/main/java/com/financial/rag/config/MilvusConfig.java` - Milvus connection setup
3. `src/main/java/com/financial/rag/config/LangChain4jConfig.java` - LLM and embedding model configuration
4. `src/main/java/com/financial/rag/retrieval/HybridRetriever.java` - Three-stage retrieval pipeline
5. `src/main/java/com/financial/rag/agent/SupervisorAgent.java` - Multi-agent orchestration
6. `src/main/java/com/financial/rag/agent/workers/DataRetrievalAgent.java` - Vector search worker
7. `src/main/java/com/financial/rag/query/QueryRewriter.java` - Query optimization strategies
8. `src/main/java/com/financial/rag/ingestion/SmartChunker.java` - Structure-aware document chunking
9. `src/main/java/com/financial/rag/tools/FinancialDataTools.java` - Tool calling implementations
10. `src/main/java/com/financial/rag/evaluation/RagasEvaluator.java` - Automated quality metrics

### Configuration Files
11. `src/main/resources/application.yml` - Spring Boot configuration
12. `docker/docker-compose.yml` - Milvus and dependencies deployment
13. `src/main/resources/schemas/financial-docs-schema.json` - Milvus collection schema

### Prompt Templates
14. `src/main/resources/prompts/supervisor-system-prompt.txt` - Supervisor agent instructions
15. `src/main/resources/prompts/query-rewriting-prompt.txt` - Query rewriting templates

---

## Success Criteria

### Functional Requirements
- ✅ Handle complex financial queries requiring multi-step reasoning
- ✅ Provide accurate answers with source citations
- ✅ Support tool calling for real-time data access
- ✅ Enforce access control and data privacy

### Performance Requirements
- ✅ P95 latency < 2s for simple queries
- ✅ P95 latency < 8s for complex multi-agent queries
- ✅ Retrieval precision@5 > 85%
- ✅ Hallucination rate < 2%

### Compliance Requirements
- ✅ Complete audit trail for all queries
- ✅ Row-level security enforcement
- ✅ On-premises deployment (no external API calls)
- ✅ Regulatory documentation ready

---

## Risk Mitigation

| Risk | Mitigation Strategy |
|------|---------------------|
| LLM hallucinations | Quality gating + compliance checker + human-in-the-loop for low-confidence answers |
| Retrieval misses critical info | Hybrid search + query expansion + context recall monitoring |
| High latency for complex queries | Async parallel execution + caching + progressive response streaming |
| Regulatory non-compliance | Pre-deployment legal review + immutable audit logs + explainable AI design |
| Milvus performance degradation | Regular index optimization + monitoring + auto-scaling |

---

## Next Steps

1. **Approve this architecture design**
2. **Initialize Gradle project** with Spring Boot and LangChain4j dependencies
3. **Set up development environment** (Milvus via Docker Compose, local LLM deployment)
4. **Begin Phase 1 implementation** starting with document ingestion pipeline
5. **Establish evaluation framework** with initial financial Q&A test dataset
