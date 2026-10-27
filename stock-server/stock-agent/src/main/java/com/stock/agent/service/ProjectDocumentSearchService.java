// AI_GENERATE_START ----
package com.stock.agent.service;

import com.stock.agent.config.AgentProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 项目 Markdown 文档只读检索服务。
 *
 * 在启动阶段从配置的只读文件系统目录构建轻量章节索引，
 * 不依赖 Embedding、MongoDB 或外部摄取任务。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Service
public class ProjectDocumentSearchService {

    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9_]+|[\\p{IsHan}]{2,}");
    private static final int MAX_SECTION_CHARS = 2400;
    private static final Set<String> STOP_WORDS = Set.of(
            "怎么", "如何", "什么", "哪些", "项目", "相关", "模块", "设计", "实现", "说明", "介绍");

    private final AgentProperties properties;

    private volatile List<DocumentSection> sections = List.of();
    private volatile String status = STATUS_UNAVAILABLE;

    /**
     * 创建项目文档检索服务。
     *
     * @param properties Agent 运行配置
     */
    public ProjectDocumentSearchService(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 加载并建立项目文档章节索引。
     */
    @PostConstruct
    public void initialize() {
        sections = List.of();
        AgentProperties.ProjectDocumentsProperties config = properties.getProjectDocuments();
        if (config == null || !config.isEnabled()) {
            status = STATUS_DISABLED;
            log.info("[Agent][Documents] status={}", status);
            return;
        }
        if (!StringUtils.hasText(config.getPath())) {
            status = STATUS_UNAVAILABLE;
            log.warn("[Agent][Documents] status={} reason=文档目录未配置", status);
            return;
        }
        try {
            Path documentRoot = Path.of(config.getPath()).toAbsolutePath().normalize();
            if (!Files.isDirectory(documentRoot)) {
                status = STATUS_UNAVAILABLE;
                log.warn("[Agent][Documents] status={} path={} reason=文档目录不存在或不可读",
                        status, documentRoot);
                return;
            }
            List<DocumentSection> indexedSections = new ArrayList<>();
            try (var paths = Files.walk(documentRoot)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(this::isMarkdownFile)
                        .sorted()
                        .toList()) {
                    indexPath(documentRoot, path, indexedSections);
                }
            }
            sections = List.copyOf(indexedSections);
            status = sections.isEmpty() ? STATUS_UNAVAILABLE : STATUS_READY;
            if (STATUS_READY.equals(status)) {
                log.info("[Agent][Documents] status={} path={} sectionCount={}",
                        status, documentRoot, sections.size());
            } else {
                log.warn("[Agent][Documents] status={} path={} reason=未发现可索引的 Markdown 章节",
                        status, documentRoot);
            }
        } catch (IOException | InvalidPathException | SecurityException exception) {
            status = STATUS_UNAVAILABLE;
            sections = List.of();
            log.error("[Agent][Documents] status={} reason=项目文档索引加载失败: {}",
                    status, exception.getMessage());
        }
    }

    /**
     * 搜索与问题最相关的项目文档章节。
     *
     * @param query 用户问题或检索词
     * @param limit 最大返回数量，范围 1 到 5
     * @return 按相关度降序排列的章节
     */
    public List<DocumentSearchResult> search(String query, int limit) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("项目文档检索词不能为空");
        }
        int safeLimit = Math.max(1, Math.min(limit, 5));
        Set<String> terms = terms(query);
        String normalizedQuery = normalize(query);
        return sections.stream()
                .map(section -> new ScoredSection(section, score(section, normalizedQuery, terms)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredSection::score).reversed()
                        .thenComparing(item -> item.section().path()))
                .limit(safeLimit)
                .map(item -> new DocumentSearchResult(
                        item.section().path(), item.section().title(), item.section().heading(),
                        item.section().content(), item.score()))
                .toList();
    }

    /**
     * 返回已建立索引的章节数量。
     *
     * @return 章节数量
     */
    public int sectionCount() {
        return sections.size();
    }

    /**
     * 返回项目文档索引状态。
     *
     * @return DISABLED、READY 或 UNAVAILABLE
     */
    public String status() {
        return status;
    }

    private boolean isMarkdownFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private void indexPath(Path documentRoot, Path documentPath,
                           List<DocumentSection> indexedSections) throws IOException {
        String path = documentPath(documentRoot, documentPath);
        List<String> lines = Files.readAllLines(documentPath, StandardCharsets.UTF_8);
        String title = frontMatterTitle(lines);
        String heading = title;
        StringBuilder content = new StringBuilder();
        boolean frontMatter = !lines.isEmpty() && "---".equals(lines.get(0).trim());
        boolean frontMatterClosed = !frontMatter;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!frontMatterClosed) {
                if (index > 0 && "---".equals(line.trim())) {
                    frontMatterClosed = true;
                }
                continue;
            }
            Matcher matcher = HEADING_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                addSection(indexedSections, path, title, heading, content);
                heading = matcher.group(2).trim();
                content.setLength(0);
                continue;
            }
            if (!line.trim().startsWith("<") && content.length() < MAX_SECTION_CHARS) {
                content.append(line).append('\n');
            }
        }
        addSection(indexedSections, path, title, heading, content);
    }

    private void addSection(List<DocumentSection> indexedSections, String path,
                            String title, String heading, StringBuilder content) {
        String value = content.toString().trim();
        if (!StringUtils.hasText(value)) {
            return;
        }
        indexedSections.add(new DocumentSection(path, title,
                StringUtils.hasText(heading) ? heading : title,
                value.substring(0, Math.min(value.length(), MAX_SECTION_CHARS))));
    }

    private String frontMatterTitle(List<String> lines) {
        boolean inFrontMatter = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if ("---".equals(trimmed)) {
                if (inFrontMatter) {
                    break;
                }
                inFrontMatter = true;
                continue;
            }
            if (inFrontMatter && trimmed.startsWith("title:")) {
                return trimmed.substring("title:".length()).trim();
            }
        }
        return "未命名项目文档";
    }

    private String documentPath(Path documentRoot, Path documentPath) {
        return "documents/" + documentRoot.relativize(documentPath).toString().replace('\\', '/');
    }

    private int score(DocumentSection section, String normalizedQuery, Set<String> terms) {
        String path = normalize(section.path());
        String title = normalize(section.title());
        String heading = normalize(section.heading());
        String content = normalize(section.content());
        int score = 0;
        if (title.contains(normalizedQuery) || heading.contains(normalizedQuery)) {
            score += 30;
        }
        for (String term : terms) {
            if (path.contains(term)) score += 12;
            if (title.contains(term)) score += 10;
            if (heading.contains(term)) score += 8;
            if (content.contains(term)) score += 2;
        }
        if ((normalizedQuery.contains("model") || normalizedQuery.contains("模型"))
                && path.contains("backend/model")) {
            score += 25;
        }
        if ((normalizedQuery.contains("agent") || normalizedQuery.contains("智能体"))
                && path.contains("agent/")) {
            score += 20;
        }
        return score;
    }

    private Set<String> terms(String query) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(normalize(query));
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOP_WORDS.contains(token)) {
                result.add(token);
                if (token.codePointCount(0, token.length()) > 4 && token.chars().allMatch(value -> value > 127)) {
                    List<Integer> points = token.codePoints().boxed().toList();
                    for (int index = 0; index < points.size() - 1; index++) {
                        result.add(new String(new int[]{points.get(index), points.get(index + 1)}, 0, 2));
                    }
                }
            }
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** 项目文档检索结果。 */
    public record DocumentSearchResult(String path, String title, String heading, String excerpt, int score) {
    }

    private record DocumentSection(String path, String title, String heading, String content) {
    }

    private record ScoredSection(DocumentSection section, int score) {
    }
}
// AI_GENERATE_END ----