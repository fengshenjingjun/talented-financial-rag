package com.financial.rag.controller;

import com.financial.rag.agent.ResultAggregator;
import com.financial.rag.agent.SupervisorPlanner;
import com.financial.rag.agent.workers.*;
import com.financial.rag.evaluation.AuditLogger;
import com.financial.rag.evaluation.LatencyTracker;
import com.financial.rag.evaluation.RagasEvaluator;
import com.financial.rag.ingestion.DocumentParser;
import com.financial.rag.ingestion.SmartChunker;
import com.financial.rag.model.AgentResponse;
import com.financial.rag.model.FinancialDocument;
import com.financial.rag.model.QueryContext;
import com.financial.rag.model.RetrievalResult;
import com.financial.rag.query.QueryAnalyzer;
import com.financial.rag.retrieval.MilvusConnector;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Slf4j
public class RagController {

    private final QueryAnalyzer queryAnalyzer;
    private final SupervisorPlanner supervisorPlanner;
    private final DataRetrievalAgent dataRetrievalAgent;
    private final MetricCalculatorAgent metricCalculatorAgent;
    private final TrendAnalyzerAgent trendAnalyzerAgent;
    private final ComplianceCheckerAgent complianceCheckerAgent;
    private final AnswerSynthesizerAgent answerSynthesizerAgent;
    private final ResultAggregator resultAggregator;
    private final DocumentParser documentParser;
    private final SmartChunker smartChunker;
    private final MilvusConnector milvusConnector;
    private final RagasEvaluator ragasEvaluator;
    private final LatencyTracker latencyTracker;
    private final AuditLogger auditLogger;

    @PostMapping("/query")
    public ResponseEntity<AgentResponse> query(@Valid @RequestBody QueryRequest request,
                                               Authentication auth) {
        String userId = auth != null ? auth.getName() : "anonymous";
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();

        log.info("Query received from user {}: {}", userId, request.query());

        // Step 1: Query understanding
        QueryContext context = queryAnalyzer.analyze(request.query(), userId, sessionId);
        String queryId = context.getQueryId();

        Map<String, Long> stageLatencies = new HashMap<>();

        // Step 2: Plan sub-tasks (Supervisor)
        latencyTracker.startStage(queryId, "planning");
        List<AgentResponse.SubTask> subTasks = supervisorPlanner.planSubTasks(request.query());
        stageLatencies.put("planning", latencyTracker.endStage(queryId, "planning"));

        // Step 3: Execute retrieval
        latencyTracker.startStage(queryId, "retrieval");
        List<RetrievalResult> retrievalResults = dataRetrievalAgent.retrieve(context);
        stageLatencies.put("retrieval", latencyTracker.endStage(queryId, "retrieval"));

        // Step 4: Execute analytical workers based on intent
        latencyTracker.startStage(queryId, "analysis");
        StringBuilder workerOutputs = new StringBuilder();

        if (context.getIntent() == QueryContext.QueryIntent.COMPARATIVE_ANALYSIS
                || context.getIntent() == QueryContext.QueryIntent.TREND_ANALYSIS) {
            String metrics = metricCalculatorAgent.calculateMetrics(retrievalResults, request.query());
            workerOutputs.append("METRICS:\n").append(metrics).append("\n\n");

            if (context.getIntent() == QueryContext.QueryIntent.TREND_ANALYSIS) {
                String trends = trendAnalyzerAgent.analyzeTrend(retrievalResults, request.query());
                workerOutputs.append("TRENDS:\n").append(trends);
            }
        }
        stageLatencies.put("analysis", latencyTracker.endStage(queryId, "analysis"));

        // Step 5: Synthesize final answer
        latencyTracker.startStage(queryId, "synthesis");
        String answer = answerSynthesizerAgent.synthesize(
                request.query(), retrievalResults, workerOutputs.toString());
        stageLatencies.put("synthesis", latencyTracker.endStage(queryId, "synthesis"));

        // Step 6: Compliance check
        latencyTracker.startStage(queryId, "compliance");
        ComplianceCheckerAgent.ComplianceResult compliance =
                complianceCheckerAgent.check(request.query(), answer);
        stageLatencies.put("compliance", latencyTracker.endStage(queryId, "compliance"));

        // Step 7: Build final response
        AgentResponse response = resultAggregator.aggregate(
                request.query(), retrievalResults, subTasks, stageLatencies);

        AgentResponse finalResponse = AgentResponse.builder()
                .queryId(response.getQueryId())
                .answer(answer)
                .citations(response.getCitations())
                .subTasks(subTasks)
                .confidenceScore(response.getConfidenceScore())
                .complianceValidated(compliance.isCompliant())
                .complianceWarnings(compliance.warnings())
                .stageLatencies(stageLatencies)
                .generatedAt(response.getGeneratedAt())
                .build();

        // Step 8: Async audit logging
        auditLogger.logQuery(context, retrievalResults, finalResponse);

        return ResponseEntity.ok(finalResponse);
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingestDocument(@RequestParam("file") MultipartFile file,
                                                         Authentication auth) throws Exception {
        log.info("Ingesting document: {} ({}KB)", file.getOriginalFilename(), file.getSize() / 1024);

        Path tempPath = Files.createTempFile("ingest_", "_" + file.getOriginalFilename());
        file.transferTo(tempPath);

        try {
            List<FinancialDocument> documents = documentParser.parseFile(tempPath);
            List<FinancialDocument> chunks = documents.stream()
                    .flatMap(doc -> smartChunker.chunk(doc).stream())
                    .toList();

            milvusConnector.batchInsert(chunks);

            return ResponseEntity.ok(new IngestResponse(
                    file.getOriginalFilename(), documents.size(), chunks.size(), "SUCCESS"));
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    @PostMapping("/evaluate")
    public ResponseEntity<RagasEvaluator.EvaluationResult> evaluate(
            @Valid @RequestBody EvaluateRequest request,
            Authentication auth) {

        QueryContext context = queryAnalyzer.analyze(request.question(), auth.getName(), "eval");
        List<RetrievalResult> results = dataRetrievalAgent.retrieve(context);

        RagasEvaluator.EvaluationResult evalResult =
                ragasEvaluator.evaluate(request.question(), request.answer(), results);

        return ResponseEntity.ok(evalResult);
    }

    public record QueryRequest(
            @NotBlank String query,
            String sessionId,
            Map<String, String> filters
    ) {}

    public record IngestResponse(String filename, int documentCount, int chunkCount, String status) {}

    public record EvaluateRequest(@NotBlank String question, @NotBlank String answer) {}
}
