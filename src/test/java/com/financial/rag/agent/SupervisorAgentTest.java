package com.financial.rag.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financial.rag.model.AgentResponse.SubTask;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupervisorAgentTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    private SupervisorPlanner supervisorPlanner;

    @BeforeEach
    void setUp() {
        supervisorPlanner = new SupervisorPlanner(chatLanguageModel, new ObjectMapper());
    }

    @Test
    void planSubTasks_shouldParseValidJsonPlan() {
        String mockPlan = """
                Here is the decomposed plan:
                [
                  {
                    "taskId": "task-1",
                    "workerType": "DATA_RETRIEVAL",
                    "description": "Retrieve Apple 2023 annual report",
                    "inputParams": {"ticker": "AAPL", "year": "2023"},
                    "dependsOn": []
                  },
                  {
                    "taskId": "task-2",
                    "workerType": "METRIC_CALCULATOR",
                    "description": "Calculate debt-to-equity ratio",
                    "inputParams": {"metric": "D/E"},
                    "dependsOn": ["task-1"]
                  },
                  {
                    "taskId": "task-3",
                    "workerType": "ANSWER_SYNTHESIZER",
                    "description": "Synthesize final answer",
                    "inputParams": {},
                    "dependsOn": ["task-2"]
                  }
                ]
                """;

        when(chatLanguageModel.generate(anyString())).thenReturn(mockPlan);

        List<SubTask> subTasks = supervisorPlanner.planSubTasks(
                "What is Apple's debt-to-equity ratio in 2023?");

        assertThat(subTasks).hasSize(3);
        assertThat(subTasks.get(0).getWorkerType()).isEqualTo("DATA_RETRIEVAL");
        assertThat(subTasks.get(1).getWorkerType()).isEqualTo("METRIC_CALCULATOR");
        assertThat(subTasks.get(2).getWorkerType()).isEqualTo("ANSWER_SYNTHESIZER");
    }

    @Test
    void planSubTasks_shouldFallBackOnMalformedJson() {
        when(chatLanguageModel.generate(anyString()))
                .thenReturn("I cannot decompose this query properly.");

        List<SubTask> subTasks = supervisorPlanner.planSubTasks("simple query");

        // Should fall back to 2-task plan (retrieval + synthesis)
        assertThat(subTasks).hasSize(2);
        assertThat(subTasks.get(0).getWorkerType()).isEqualTo("DATA_RETRIEVAL");
        assertThat(subTasks.get(1).getWorkerType()).isEqualTo("ANSWER_SYNTHESIZER");
    }

    @Test
    void planSubTasks_shouldAlwaysEndWithAnswerSynthesizer() {
        String plan = """
                [{"taskId":"t1","workerType":"DATA_RETRIEVAL","description":"retrieve","inputParams":{},"dependsOn":[]},
                 {"taskId":"t2","workerType":"ANSWER_SYNTHESIZER","description":"synthesize","inputParams":{},"dependsOn":["t1"]}]
                """;
        when(chatLanguageModel.generate(anyString())).thenReturn(plan);

        List<SubTask> tasks = supervisorPlanner.planSubTasks("any query");

        assertThat(tasks).isNotEmpty();
        assertThat(tasks.get(tasks.size() - 1).getWorkerType()).isEqualTo("ANSWER_SYNTHESIZER");
    }
}
