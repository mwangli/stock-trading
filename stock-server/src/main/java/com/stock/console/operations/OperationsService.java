// AI_GENERATE_START ------
package com.stock.console.operations;

import com.stock.console.config.ConsoleProperties;
import com.stock.console.facts.TradingFacts;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardOpenOption.READ;

@Service
public class OperationsService {

    private static final int MAX_LOG_LINES = 2000;
    private static final int TAIL_BLOCK_BYTES = 8192;
    private static final int MAX_TAIL_BYTES = 8 * 1024 * 1024;

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

    public LogSnapshot logs(int lines) {
        if (!Files.isRegularFile(properties.taskLog())) {
            return new LogSnapshot(false, properties.taskLog().toString(), List.of(), null);
        }
        try {
            return new LogSnapshot(
                    true,
                    properties.taskLog().toString(),
                    readTail(properties.taskLog(), lines),
                    null);
        } catch (IOException exception) {
            return new LogSnapshot(
                    true,
                    properties.taskLog().toString(),
                    List.of(),
                    exception.getMessage());
        }
    }

    static List<String> readTail(Path path, int requestedLines) throws IOException {
        int limit = Math.max(1, Math.min(requestedLines, MAX_LOG_LINES));
        try (SeekableByteChannel channel = Files.newByteChannel(path, READ)) {
            long position = channel.size();
            if (position == 0) {
                return List.of();
            }

            Deque<byte[]> chunks = new ArrayDeque<>();
            int newlineCount = 0;
            int totalBytes = 0;
            while (position > 0 && newlineCount <= limit && totalBytes < MAX_TAIL_BYTES) {
                int blockSize = (int) Math.min(
                        Math.min(TAIL_BLOCK_BYTES, position),
                        MAX_TAIL_BYTES - totalBytes);
                position -= blockSize;
                ByteBuffer buffer = ByteBuffer.allocate(blockSize);
                channel.position(position);
                while (buffer.hasRemaining()) {
                    int read = channel.read(buffer);
                    if (read < 0) {
                        break;
                    }
                }
                int bytesRead = buffer.position();
                if (bytesRead == 0) {
                    break;
                }

                byte[] chunk = new byte[bytesRead];
                buffer.flip();
                buffer.get(chunk);
                chunks.addFirst(chunk);
                totalBytes += bytesRead;
                for (byte value : chunk) {
                    if (value == '\n') {
                        newlineCount++;
                    }
                }
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream(totalBytes);
            for (byte[] chunk : chunks) {
                output.write(chunk);
            }
            byte[] content = output.toByteArray();
            int offset = position > 0 ? firstCompleteLineOffset(content) : 0;
            String text = new String(content, offset, content.length - offset, StandardCharsets.UTF_8);
            List<String> values = new ArrayList<>(text.lines().toList());
            int start = Math.max(0, values.size() - limit);
            return List.copyOf(values.subList(start, values.size()));
        }
    }

    private static int firstCompleteLineOffset(byte[] content) {
        for (int index = 0; index < content.length; index++) {
            if (content[index] == '\n') {
                return index + 1;
            }
        }
        return 0;
    }

    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(60_000L);
        AtomicReference<List<String>> previous = new AtomicReference<>(List.of());
        var future = logExecutor.scheduleAtFixedRate(() -> {
            try {
                List<String> values = logs(500).lines();
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

    public record LogSnapshot(
            boolean available,
            String path,
            List<String> lines,
            String error) {
    }
}
// AI_GENERATE_END ------
