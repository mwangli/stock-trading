// AI_GENERATE_START ----
package com.stock.console.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.console.agent.GitHubDevelopmentClient.ChangeOperation;
import com.stock.console.agent.GitHubDevelopmentClient.ChangeSet;
import com.stock.console.agent.GitHubDevelopmentClient.FileChange;
import com.stock.console.agent.GitHubDevelopmentClient.RepositoryFile;
import com.stock.console.config.ConsoleProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DevelopmentModelService {

    static final int MAX_SELECTED_FILES = 16;
    private static final int MAX_TREE_PATHS = 3_000;
    private static final int MAX_CONTEXT_CHARS = 500_000;

    private final ConsoleProperties properties;
    private final ObjectMapper objectMapper;
    private volatile DevelopmentAssistant assistant;

    public DevelopmentModelService(ConsoleProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public BusinessSpec analyzeRequirement(String requirement) {
        String prompt = """
                将下面的业务需求整理为开发规格。返回 JSON：
                {
                  "goal": "一句话目标",
                  "nonGoals": ["明确不做的事项"],
                  "acceptanceCriteria": ["可由源码或构建判断的验收条件"],
                  "searchTerms": ["用于在代码仓库搜索的精确英文标识符或路径词"]
                }
                searchTerms 最多 6 个，不要包含宽泛词语。

                原始需求：
                """ + requirement;
        return parse(execute(prompt), BusinessSpec.class);
    }

    public List<String> selectPaths(BusinessSpec spec, List<String> repositoryTree, List<String> searchMatches) {
        String tree = String.join("\n", repositoryTree.stream().limit(MAX_TREE_PATHS).toList());
        String prompt = """
                根据开发规格，从仓库目录中选择实现所需的现有文件。返回 JSON：
                {"paths":["path/to/file"],"reason":"选择依据"}
                最多选择 16 个文本文件。必须优先包含 AGENTS.md、直接入口、配置和相关实现；不要选择 lockfile、二进制文件和 .github/workflows 文件。

                开发规格：
                %s

                搜索命中：
                %s

                仓库目录：
                %s
                """.formatted(json(spec), json(searchMatches), tree);
        PathSelection selection = parse(execute(prompt), PathSelection.class);
        Set<String> unique = new LinkedHashSet<>();
        unique.add("AGENTS.md");
        if (selection.paths() != null) {
            selection.paths().stream().filter(path -> path != null && !path.isBlank())
                    .limit(MAX_SELECTED_FILES - 1L).forEach(unique::add);
        }
        return unique.stream().limit(MAX_SELECTED_FILES).toList();
    }

    public ChangeSet generateChangeSet(
            BusinessSpec spec,
            List<RepositoryFile> files,
            String buildFailure,
            List<String> previousChangedFiles) {
        String context = repositoryContext(files);
        String failureSection = buildFailure == null || buildFailure.isBlank()
                ? "这是第一次实现，没有构建失败日志。"
                : "这是自动修复。上次修改文件：" + json(previousChangedFiles)
                + "\nGitHub Actions 失败日志：\n" + buildFailure;
        String prompt = """
                根据开发规格和仓库文件生成完整变更。返回 JSON：
                {
                  "summary":"不超过 60 字的中文提交摘要",
                  "acceptanceSummary":"说明本次修改如何满足验收条件",
                  "changes":[
                    {"path":"仓库相对路径","operation":"UPSERT","content":"文件完整内容"},
                    {"path":"仓库相对路径","operation":"REPLACE","replacements":[{"oldText":"唯一匹配的原文","newText":"替换后的文本"}]},
                    {"path":"仓库相对路径","operation":"DELETE","content":null}
                  ]
                }

                规则：
                1. 新文件使用 UPSERT 并给出完整内容；已有大文件优先使用 REPLACE，oldText 必须在原文件中唯一匹配。
                2. 只修改完成需求必需的文件；禁止修改 .github/workflows/**。
                3. Java、TypeScript、CSS、Markdown 文件必须按 AGENTS.md 维护首尾 AI_GENERATE 标记；YAML、JSON、XML、锁文件不加标记。
                4. 不生成空文件，不修改二进制文件，不虚构不存在的 API。

                开发规格：
                %s

                构建上下文：
                %s

                仓库文件：
                %s
                """.formatted(json(spec), failureSection, context);
        RawChangeSet raw = parse(execute(prompt), RawChangeSet.class);
        if (raw.changes() == null) {
            throw new IllegalArgumentException("代码 Agent 未返回 changes");
        }
        Map<String, RepositoryFile> fileByPath = files.stream()
                .collect(Collectors.toMap(RepositoryFile::path, Function.identity(), (left, right) -> left));
        List<FileChange> changes = new ArrayList<>();
        for (RawFileChange change : raw.changes()) {
            if ("REPLACE".equals(change.operation())) {
                RepositoryFile original = fileByPath.get(change.path());
                if (original == null) {
                    throw new IllegalArgumentException("REPLACE 文件未包含在读取上下文中: " + change.path());
                }
                String content = original.content();
                if (change.replacements() == null || change.replacements().isEmpty()) {
                    throw new IllegalArgumentException("REPLACE 未提供 replacements: " + change.path());
                }
                for (RawReplacement replacement : change.replacements()) {
                    content = replaceExactlyOnce(content, replacement.oldText(), replacement.newText(), change.path());
                }
                content = maintainAiMarkers(change.path(), original.content(), content);
                if (!original.content().equals(content)) {
                    changes.add(new FileChange(change.path(), ChangeOperation.UPSERT, content));
                }
                continue;
            }
            ChangeOperation operation = parseOperation(change.operation());
            String original = fileByPath.containsKey(change.path()) ? fileByPath.get(change.path()).content() : null;
            if (operation == ChangeOperation.DELETE && original == null) {
                throw new IllegalArgumentException("DELETE 文件未包含在读取上下文中: " + change.path());
            }
            String content = operation == ChangeOperation.UPSERT
                    ? maintainAiMarkers(change.path(), original, change.content()) : change.content();
            if (operation == ChangeOperation.DELETE || original == null || !original.equals(content)) {
                changes.add(new FileChange(change.path(), operation, content));
            }
        }
        return new ChangeSet(raw.summary(), raw.acceptanceSummary(), changes);
    }

    private ChangeOperation parseOperation(String value) {
        try {
            return ChangeOperation.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("代码 Agent 返回了非法 operation: " + value);
        }
    }

    String replaceExactlyOnce(String content, String oldText, String newText, String path) {
        if (oldText == null || oldText.isEmpty() || newText == null) {
            throw new IllegalArgumentException("REPLACE 包含空文本: " + path);
        }
        int first = content.indexOf(oldText);
        if (first < 0 || content.indexOf(oldText, first + oldText.length()) >= 0) {
            throw new IllegalArgumentException("REPLACE 原文不存在或不是唯一匹配: " + path);
        }
        return content.substring(0, first) + newText + content.substring(first + oldText.length());
    }

    String maintainAiMarkers(String path, String original, String generated) {
        if (generated == null || !markerEligible(path)) {
            return generated;
        }
        String normalized = generated.replace("\r\n", "\n");
        String originalNormalized = original == null ? null : original.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0) {
            return generated;
        }
        String originalFirst = originalNormalized == null ? "" : originalNormalized.lines().findFirst().orElse("");
        String originalLast = originalNormalized == null ? "" : lastLine(originalNormalized);
        if (lines[0].contains("AI_GENERATE_START")) {
            lines[0] = incrementMarker(lines[0], originalFirst, "AI_GENERATE_START");
        } else {
            lines = prepend(lines, marker(path, true));
        }
        int last = lines.length - 1;
        if (lines[last].contains("AI_GENERATE_END")) {
            lines[last] = incrementMarker(lines[last], originalLast, "AI_GENERATE_END");
        } else {
            lines = append(lines, marker(path, false));
        }
        return String.join("\n", lines);
    }

    private String incrementMarker(String generated, String original, String marker) {
        int originalCount = dashCount(original, marker);
        int generatedCount = dashCount(generated, marker);
        if (generatedCount > originalCount) {
            return generated;
        }
        int position = generated.indexOf(marker) + marker.length();
        return generated.substring(0, position) + "-".repeat(originalCount + 1)
                + generated.substring(position + generatedCount);
    }

    private int dashCount(String line, String marker) {
        int position = line.indexOf(marker);
        if (position < 0) {
            return 0;
        }
        int count = 0;
        for (int index = position + marker.length(); index < line.length() && line.charAt(index) == '-'; index++) {
            count++;
        }
        return count;
    }

    private boolean markerEligible(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".ts") || lower.endsWith(".tsx")
                || lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".css")
                || lower.endsWith(".scss") || lower.endsWith(".md");
    }

    private String marker(String path, boolean start) {
        String text = start ? "AI_GENERATE_START -" : "AI_GENERATE_END -";
        String lower = path.toLowerCase();
        if (lower.endsWith(".md")) {
            return "<!-- " + text + " -->";
        }
        if (lower.endsWith(".css") || lower.endsWith(".scss")) {
            return "/* " + text + " */";
        }
        return "// " + text;
    }

    private String[] prepend(String[] lines, String value) {
        String[] result = new String[lines.length + 1];
        result[0] = value;
        System.arraycopy(lines, 0, result, 1, lines.length);
        return result;
    }

    private String[] append(String[] lines, String value) {
        String[] result = new String[lines.length + 1];
        System.arraycopy(lines, 0, result, 0, lines.length);
        result[lines.length] = value;
        return result;
    }

    private String lastLine(String value) {
        int index = value.lastIndexOf('\n');
        return index < 0 ? value : value.substring(index + 1);
    }

    private String repositoryContext(List<RepositoryFile> files) {
        StringBuilder context = new StringBuilder();
        for (RepositoryFile file : files) {
            String block = "\n===== " + file.path() + " =====\n" + file.content() + "\n";
            if (context.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            context.append(block);
        }
        return context.toString();
    }

    private String execute(String prompt) {
        return assistant().execute(prompt).content();
    }

    private DevelopmentAssistant assistant() {
        DevelopmentAssistant current = assistant;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (assistant == null) {
                ConsoleProperties.Agent config = properties.agent();
                OpenAiChatModel model = OpenAiChatModel.builder()
                        .baseUrl(config.baseUrl())
                        .apiKey(config.apiKey())
                        .modelName(config.model())
                        .maxTokens(Math.max(8_000, config.maxTokens()))
                        .timeout(Duration.ofSeconds(Math.max(90, config.timeoutSeconds())))
                        .maxRetries(0)
                        .build();
                assistant = AiServices.builder(DevelopmentAssistant.class).chatModel(model).build();
            }
            return assistant;
        }
    }

    private <T> T parse(String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("开发 Agent 返回了空响应");
        }
        String normalized = value.trim();
        if (normalized.startsWith("```")) {
            int firstLine = normalized.indexOf('\n');
            int lastFence = normalized.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                normalized = normalized.substring(firstLine + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readValue(normalized, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("开发 Agent 返回的 JSON 无法解析", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("开发 Agent 上下文序列化失败", exception);
        }
    }

    public record BusinessSpec(
            String goal,
            List<String> nonGoals,
            List<String> acceptanceCriteria,
            List<String> searchTerms) {
    }

    private record PathSelection(List<String> paths, String reason) {
    }

    private record RawChangeSet(String summary, String acceptanceSummary, List<RawFileChange> changes) {
    }

    private record RawFileChange(
            String path,
            String operation,
            String content,
            List<RawReplacement> replacements) {
    }

    private record RawReplacement(String oldText, String newText) {
    }
}
// AI_GENERATE_END ----
