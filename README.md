# Talented Financial RAG

金融领域 Agentic-RAG 系统，基于 **LangChain4j + Milvus + Spring Boot** 构建，采用 Supervisor-Worker 多智能体架构与三阶段混合检索管道，面向监管合规、风险分析、财务报告等场景提供高精度问答能力。

---

## 架构概览

```
用户查询
  │
  ▼
┌─────────────────────────────────────────────────────┐
│                  Query Understanding Layer           │
│  IntentClassifier → QueryRewriter → TermExpander    │
│  (Step-Back / HyDE / Query Expansion 三种改写策略)  │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                  Supervisor Agent                    │
│         任务分解 → 子任务规划 → 并发调度             │
└──┬──────────┬───────────┬──────────┬────────────────┘
   │          │           │          │
   ▼          ▼           ▼          ▼
DataRetrieval  MetricCalc  TrendAnalyzer  Compliance
   Worker      Worker       Worker        Checker
   │
   ▼
┌─────────────────────────────────────────────────────┐
│              三阶段混合检索管道                      │
│  Stage 1: 并行 Vector(HNSW) + BM25 → 候选集 Top-50 │
│  Stage 2: BGE-Reranker 精排 → Top-5                │
│  Stage 3: 质量门控(阈值 0.7) + 最多 2 次重试        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
              AnswerSynthesizer
           (带来源引用的最终回答)
```

---

## 技术栈

| 组件 | 选型 | 说明 |
|------|------|------|
| Agent 框架 | LangChain4j 0.36.0 | `@AiServices` 声明式 Agent，`@Tool` 工具调用 |
| 向量数据库 | Milvus 2.4.x | HNSW 索引，M=16 / efConstruction=200 |
| 向量检索 SDK | milvus-sdk-java 2.4.3 | 批量插入 / 带过滤的向量检索 |
| 关键词检索 | Apache Lucene 9.10 | BM25 相似度，与向量检索 RRF 融合 |
| 重排序 | BGE-Reranker-v2-m3 | 独立 FastAPI 服务，目标降低检索失败率 35-49% |
| 本地 LLM | Qwen2.5-72B (大) / 14B (小) | 通过 Ollama 部署，大模型用于最终合成，小模型用于意图分类 |
| 嵌入模型 | BGE-M3 (1024维) | 多语言，适合中英文金融文档 |
| Web 框架 | Spring Boot 3.2.5 | REST API，Spring Security，JPA 审计日志 |
| 缓存 | Redis | 频繁查询缓存，TTL 1 小时 |
| 监控 | Micrometer + Prometheus | 各阶段延迟、检索命中率、Token 消耗 |
| 构建工具 | Gradle 8.x (Kotlin DSL) | |

---

## 项目结构

```
talented-financial-rag/
├── build.gradle.kts
├── settings.gradle.kts
├── docker/
│   ├── docker-compose.yml          # Milvus + Ollama + Reranker + Redis
│   ├── milvus-config.yaml
│   ├── Dockerfile
│   └── reranker/
│       └── reranker_server.py      # BGE-Reranker FastAPI 服务
└── src/main/java/com/financial/rag/
    ├── FinancialRagApplication.java
    ├── config/
    │   ├── MilvusConfig.java        # Milvus 连接 & HNSW 参数
    │   ├── LangChain4jConfig.java   # 大/小双模型配置
    │   └── SecurityConfig.java      # HTTP Basic Auth, 角色控制
    ├── query/                       # 查询理解层
    │   ├── QueryAnalyzer.java       # 主编排器
    │   ├── IntentClassifier.java    # 意图分类（小模型）
    │   ├── QueryRewriter.java       # HyDE / Step-Back / 扩展
    │   └── FinancialTermExpander.java
    ├── agent/                       # 多智能体层
    │   ├── SupervisorAgent.java     # @AiService 接口，任务分解
    │   ├── SupervisorPlanner.java   # 解析 LLM 的 JSON 执行计划
    │   ├── ResultAggregator.java    # 合并所有 Worker 输出
    │   └── workers/
    │       ├── DataRetrievalAgent.java
    │       ├── MetricCalculatorAgent.java
    │       ├── TrendAnalyzerAgent.java
    │       ├── ComplianceCheckerAgent.java
    │       └── AnswerSynthesizerAgent.java
    ├── retrieval/                   # 三阶段检索管道
    │   ├── HybridRetriever.java     # RRF 融合 + 重排 + 质量门控
    │   ├── MilvusConnector.java     # 向量检索 & 批量写入
    │   ├── BM25Searcher.java        # Lucene BM25
    │   ├── RerankerService.java     # 调用 BGE-Reranker REST API
    │   └── QualityGate.java         # 阈值过滤 & 降级
    ├── ingestion/                   # 文档摄入管道
    │   ├── DocumentParser.java      # PDF / DOCX / TXT 解析
    │   ├── TableExtractor.java      # 财务报表表格提取 → JSON
    │   ├── SmartChunker.java        # 语义感知分块 (800字符 / 15%重叠)
    │   └── MetadataEnricher.java    # 自动识别 Ticker / 日期 / 文档类型
    ├── tools/                       # LLM 工具调用
    │   ├── MarketDataTools.java     # 股价、指数、波动率
    │   ├── FinancialDataTools.java  # 财务报表、比率计算
    │   └── RegulatoryTools.java     # SEC 文件、合规规则
    ├── evaluation/
    │   ├── RagasEvaluator.java      # 忠实度 / 相关性 / 精度 / 召回
    │   ├── LatencyTracker.java      # 各阶段延迟 (Micrometer)
    │   └── AuditLogger.java         # 不可变审计日志 (JPA)
    ├── model/                       # 数据模型
    │   ├── QueryContext.java
    │   ├── RetrievalResult.java
    │   ├── FinancialDocument.java
    │   └── AgentResponse.java
    └── controller/
        ├── RagController.java       # POST /api/rag/query  /ingest  /evaluate
        └── AdminController.java     # GET  /api/admin/audit-logs  /metrics
```

---

## 快速开始

### 前置条件

- Docker & Docker Compose
- JDK 17+
- Gradle 8.x（或使用项目自带 wrapper）
- GPU 推荐（用于本地运行 Qwen2.5-72B）

### 1. 启动基础设施

```bash
cd docker
docker-compose up -d milvus etcd minio redis reranker
```

### 2. 启动 Ollama 并拉取模型

```bash
docker-compose up -d ollama

# 等待 Ollama 就绪后拉取模型
docker exec ollama ollama pull bge-m3          # 嵌入模型
docker exec ollama ollama pull qwen2.5:14b     # 小模型（意图分类 / 改写）
docker exec ollama ollama pull qwen2.5:72b     # 大模型（最终合成）
```

> **资源不足时**：可在 `application.yml` 中将 `ollama.llm-model` 改为 `qwen2.5:7b`，或将 `ollama.base-url` 指向远程推理服务。

### 3. 构建并运行应用

```bash
./gradlew bootRun
```

或打包后运行：

```bash
./gradlew bootJar
java -jar build/libs/financial-rag.jar
```

应用默认监听 `http://localhost:8088`。

---

## API 使用

### 上传金融文档

```bash
curl -X POST http://localhost:8088/api/rag/ingest \
  -u analyst:analyst123 \
  -F "file=@AAPL_10K_2023.pdf"
```

响应：
```json
{
  "filename": "AAPL_10K_2023.pdf",
  "documentCount": 3,
  "chunkCount": 127,
  "status": "SUCCESS"
}
```

### 查询

```bash
curl -X POST http://localhost:8088/api/rag/query \
  -u analyst:analyst123 \
  -H "Content-Type: application/json" \
  -d '{
    "query": "比较苹果和微软2023年的净利润率趋势",
    "sessionId": "session-001"
  }'
```

响应结构：
```json
{
  "queryId": "...",
  "answer": "根据检索到的文档...[AAPL_10K_2023]...",
  "citations": [
    {
      "documentId": "aapl_10k_2023_chunk_12",
      "excerpt": "Net income was $96.9 billion...",
      "companyTicker": "AAPL",
      "filingDate": "2023-11-03",
      "relevanceScore": 0.91
    }
  ],
  "subTasks": [...],
  "confidenceScore": 0.87,
  "complianceValidated": true,
  "stageLatencies": {
    "planning": 820,
    "retrieval": 340,
    "analysis": 1200,
    "synthesis": 2100,
    "compliance": 650
  }
}
```

### 质量评估

```bash
curl -X POST http://localhost:8088/api/rag/evaluate \
  -u analyst:analyst123 \
  -H "Content-Type: application/json" \
  -d '{
    "question": "苹果2023年的营收是多少？",
    "answer": "苹果2023财年营收为3943亿美元。"
  }'
```

### 管理接口（需 ADMIN 角色）

```bash
# 审计日志
curl http://localhost:8088/api/admin/audit-logs -u admin:admin123

# 按用户查询
curl "http://localhost:8088/api/admin/audit-logs?userId=analyst" -u admin:admin123

# Milvus 健康状态
curl http://localhost:8088/api/admin/health/milvus -u admin:admin123
```

---

## 配置说明

所有参数均可通过环境变量或 `application.yml` 覆盖：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `MILVUS_HOST` | `localhost` | Milvus 地址 |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama 服务地址 |
| `LLM_MODEL` | `qwen2.5:72b` | 大模型（最终合成） |
| `SMALL_MODEL` | `qwen2.5:14b` | 小模型（分类/改写） |
| `EMBEDDING_MODEL` | `bge-m3` | 嵌入模型 |
| `RERANKER_URL` | `http://localhost:8001` | BGE-Reranker 服务地址 |
| `RERANKER_ENABLED` | `true` | 是否启用重排序 |
| `retrieval.quality-threshold` | `0.7` | 质量门控阈值 |
| `retrieval.vector-top-k` | `50` | 向量检索候选数 |
| `retrieval.rerank-top-k` | `5` | 重排后保留数量 |
| `chunking.chunk-size` | `800` | 分块字符数 |
| `chunking.overlap-ratio` | `0.15` | 重叠比例 |

生产环境额外配置（`spring.profiles.active=prod`）：
- 数据库切换为 PostgreSQL（审计日志持久化）
- 缓存切换为 Redis

---

## 检索管道详解

```
查询字符串
    │
    ├─ [并行] Milvus 向量检索 (BGE-M3, HNSW, Top-50)
    └─ [并行] Lucene BM25 关键词检索 (Top-30)
              │
              ▼
       RRF 融合去重 (α·向量排名 + β·BM25排名)
              │
              ▼
    BGE-Reranker 精排 (cross-encoder, Top-5)
              │
              ▼
    质量门控 (rerankScore ≥ 0.7)
      ├─ 通过 → 返回结果
      └─ 未通过 → 扩展查询重试（最多 2 次）
```

**目标性能指标（架构设计规格）：**

| 指标 | 目标 |
|------|------|
| 简单查询 P95 延迟 | < 2 秒 |
| 复杂多 Agent 查询 P95 延迟 | < 8 秒 |
| Precision@5 | > 85% |
| 幻觉率 | < 2% |

---

## 运行测试

```bash
# 单元测试
./gradlew test

# 查看测试报告
open build/reports/tests/test/index.html
```

测试覆盖：
- `HybridRetrieverTest` — RRF 融合、质量门控重试逻辑
- `SupervisorAgentTest` — 任务分解计划解析、JSON 容错
- `RagasEvaluatorTest` — 四维指标评估、LLM 异常降级

---

## 监控

Prometheus 指标端点：`http://localhost:8088/actuator/prometheus`

关键指标：

| 指标名 | 含义 |
|--------|------|
| `rag.stage.latency{stage}` | 各阶段延迟分布 |
| `rag.queries.total` | 总查询次数 |
| `rag.retrieval.score` | 检索平均分 |
| `rag.compliance.failures` | 合规校验失败次数 |

---

## 合规与安全

- **行级安全**：Milvus 过滤表达式按 `access_level` 字段隔离数据
- **审计日志**：每条查询记录用户 ID、检索文档 ID、生成答案、置信分，存储于 append-only 数据库表
- **合规检查**：`ComplianceCheckerAgent` 在回答发出前校验前瞻性陈述免责声明、GAAP 引用准确性等
- **本地部署**：LLM 和嵌入模型均通过 Ollama 本地运行，无外部 API 调用

---

## 实现阶段

| 阶段 | 内容 | 周期 |
|------|------|------|
| Phase 1 | 文档摄入管道、基础 RAG、Milvus schema | 第 1-4 周 |
| Phase 2 | 混合检索、重排序、元数据过滤、性能基准 | 第 5-8 周 |
| Phase 3 | 多 Agent 编排、工具调用、异步并行执行 | 第 9-12 周 |
| Phase 4 | 审计日志、访问控制、Prometheus 监控、生产加固 | 第 13-16 周 |

---

## 许可证

本项目为内部研究原型，生产部署前请完成以下事项：

- [ ] 替换工具类中的 stub 实现（接入 Bloomberg / SEC EDGAR / Refinitiv）
- [ ] 将 `InMemoryUserDetailsManager` 替换为 LDAP / OAuth2 集成
- [ ] 通过 HashiCorp Vault 管理密钥
- [ ] 对 Milvus 集群启用 TLS 和静态加密
- [ ] 完成安全审计与渗透测试
