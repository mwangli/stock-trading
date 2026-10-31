// AI_GENERATE_START ---
package com.stock.console.operations;

import com.stock.console.config.ConsoleProperties;
import com.stock.console.facts.TradingFacts;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OperationsService {

    private final ConsoleProperties properties;
    private final TradingFacts facts;
    private final ScheduledExecutorService logExecutor = Executors.newScheduledThreadPool(2);

    public OperationsService(ConsoleProperties properties, TradingFacts facts) {
        this.properties = properties;
        this.facts = facts;
    }

    public Map<String, Object> status() {
        return Map.of(
                "tradingSource", facts.status(),
                "taskLogAvailable", Files.isRegularFile(properties.taskLog()),
                "taskLog", properties.taskLog().toString(),
                "runs", facts.taskRuns(100));
    }

    public List<String> tail(int lines) {
        if (!Files.isRegularFile(properties.taskLog())) {
            return List.of();
        }
        try {
            List<String> values = Files.readAllLines(properties.taskLog(), StandardCharsets.UTF_8);
            int start = Math.max(0, values.size() - Math.max(1, Math.min(lines, 2000)));
            return values.subList(start, values.size());
        } catch (IOException exception) {
            return List.of("日志读取失败: " + exception.getMessage());
        }
    }

    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(60_000L);
        AtomicReference<List<String>> previous = new AtomicReference<>(List.of());
        var future = logExecutor.scheduleAtFixedRate(() -> {
            try {
                List<String> values = tail(500);
                int start = overlap(previous.get(), values);
                for (String value : values.subList(start, values.size())) {
                    emitter.send(SseEmitter.event().name("log").data(value));
                }
                previous.set(List.copyOf(values));
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        }, 0, 2, TimeUnit.SECONDS);
        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        return emitter;
    }

    private int overlap(List<String> previous, List<String> current) {
        int maximum = Math.min(previous.size(), current.size());
        for (int size = maximum; size > 0; size--) {
            if (previous.subList(previous.size() - size, previous.size())
                    .equals(current.subList(0, size))) {
                return size;
            }
        }
        return 0;
    }

    @PreDestroy
    void shutdown() {
        logExecutor.shutdownNow();
    }
}
// AI_GENERATE_END ---
