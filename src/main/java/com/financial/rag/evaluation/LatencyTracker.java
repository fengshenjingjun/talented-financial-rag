package com.financial.rag.evaluation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class LatencyTracker {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> activeTimers = new ConcurrentHashMap<>();

    public void startStage(String queryId, String stage) {
        activeTimers.put(queryId + ":" + stage, System.currentTimeMillis());
    }

    public long endStage(String queryId, String stage) {
        String key = queryId + ":" + stage;
        Long startMs = activeTimers.remove(key);
        if (startMs == null) {
            log.warn("No start time found for queryId={} stage={}", queryId, stage);
            return 0;
        }
        long durationMs = System.currentTimeMillis() - startMs;

        // Record to Micrometer for Prometheus scraping
        Timer.builder("rag.stage.latency")
                .tag("stage", stage)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        log.debug("Stage {} completed in {}ms for query {}", stage, durationMs, queryId);
        return durationMs;
    }

    public Map<String, Long> collectStageLatencies(String queryId, String... stages) {
        Map<String, Long> latencies = new HashMap<>();
        for (String stage : stages) {
            String key = queryId + ":" + stage;
            Long startMs = activeTimers.get(key);
            if (startMs != null) {
                latencies.put(stage, System.currentTimeMillis() - startMs);
            }
        }
        return latencies;
    }

    // Convenience wrapper for timed execution
    public <T> T time(String queryId, String stage, java.util.concurrent.Callable<T> task) {
        startStage(queryId, stage);
        try {
            T result = task.call();
            endStage(queryId, stage);
            return result;
        } catch (Exception e) {
            endStage(queryId, stage);
            throw new RuntimeException("Stage " + stage + " failed", e);
        }
    }
}
