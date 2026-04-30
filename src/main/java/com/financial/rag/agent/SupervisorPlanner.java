package com.financial.rag.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financial.rag.model.AgentResponse.SubTask;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class SupervisorPlanner {

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public List<SubTask> planSubTasks(String query) {
        SupervisorAgent supervisor = AiServices.builder(SupervisorAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        try {
            String planJson = supervisor.planExecution(query);
            log.debug("Supervisor plan: {}", planJson);

            // Extract JSON array from response (model may include prose around it)
            String jsonArray = extractJsonArray(planJson);
            List<Map<String, Object>> rawTasks = objectMapper.readValue(jsonArray,
                    new TypeReference<>() {});

            return rawTasks.stream()
                    .map(this::mapToSubTask)
                    .toList();

        } catch (Exception e) {
            log.error("Failed to parse supervisor plan, falling back to single retrieval task", e);
            return fallbackPlan(query);
        }
    }

    private SubTask mapToSubTask(Map<String, Object> raw) {
        return SubTask.builder()
                .taskId((String) raw.getOrDefault("taskId", "task-" + System.nanoTime()))
                .workerType((String) raw.getOrDefault("workerType", "DATA_RETRIEVAL"))
                .description((String) raw.getOrDefault("description", ""))
                .status("PENDING")
                .build();
    }

    private List<SubTask> fallbackPlan(String query) {
        return List.of(
                SubTask.builder()
                        .taskId("task-retrieve")
                        .workerType("DATA_RETRIEVAL")
                        .description("Retrieve relevant documents for: " + query)
                        .status("PENDING")
                        .build(),
                SubTask.builder()
                        .taskId("task-synthesize")
                        .workerType("ANSWER_SYNTHESIZER")
                        .description("Synthesize final answer")
                        .status("PENDING")
                        .build()
        );
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "[]";
    }
}
