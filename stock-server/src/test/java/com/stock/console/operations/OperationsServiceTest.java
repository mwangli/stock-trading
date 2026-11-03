// AI_GENERATE_START -
package com.stock.console.operations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void readsOnlyTheRequestedUtf8Tail() throws Exception {
        Path log = tempDir.resolve("run.log");
        String content = IntStream.rangeClosed(1, 3000)
                .mapToObj(index -> "第" + index + "行日志")
                .collect(Collectors.joining(System.lineSeparator(), "", System.lineSeparator()));
        Files.writeString(log, content, StandardCharsets.UTF_8);

        List<String> lines = OperationsService.readTail(log, 2000);

        assertThat(lines).hasSize(2000);
        assertThat(lines.get(0)).isEqualTo("第1001行日志");
        assertThat(lines.get(1999)).isEqualTo("第3000行日志");
    }
}
// AI_GENERATE_END -
