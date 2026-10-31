// AI_GENERATE_START --
package com.stock.console.documents;

import com.stock.console.config.ConsoleProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DocumentService {

    private final ConsoleProperties properties;
    private volatile Map<String, Document> documents = Map.of();

    public DocumentService(ConsoleProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void index() {
        Map<String, Document> indexed = new LinkedHashMap<>();
        int rootIndex = 0;
        for (Path root : properties.resolvedDocumentRoots()) {
            if (!Files.isDirectory(root)) {
                rootIndex++;
                continue;
            }
            try (var paths = Files.walk(root)) {
                int currentRoot = rootIndex;
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                        .sorted()
                        .forEach(path -> read(indexed, root, path, currentRoot));
            } catch (IOException ignored) {
                // One unreadable root must not prevent the console from starting.
            }
            rootIndex++;
        }
        documents = Map.copyOf(indexed);
    }

    public List<Map<String, Object>> list() {
        return documents.values().stream()
                .sorted(Comparator.comparing(Document::id))
                .map(document -> Map.<String, Object>of(
                        "id", document.id(), "title", document.title(), "path", document.path()))
                .toList();
    }

    public Document get(String id) {
        Document document = documents.get(id);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        return document;
    }

    public List<SearchHit> search(String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();
        for (Document document : documents.values()) {
            String content = document.content().toLowerCase(Locale.ROOT);
            boolean titleMatch = document.title().toLowerCase(Locale.ROOT).contains(normalized);
            int score = occurrences(content, normalized) + (titleMatch ? 20 : 0);
            if (score > 0) {
                int matchIndex = content.indexOf(normalized);
                int at = Math.max(0, (matchIndex < 0 ? 0 : matchIndex) - 120);
                int end = Math.min(document.content().length(), at + 480);
                hits.add(new SearchHit(document.id(), document.title(), document.path(),
                        document.content().substring(at, end), score));
            }
        }
        return hits.stream().sorted(Comparator.comparingInt(SearchHit::score).reversed())
                .limit(Math.max(1, Math.min(limit, 8))).toList();
    }

    private void read(Map<String, Document> indexed, Path root, Path path, int rootIndex) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String relative = root.relativize(path).toString().replace('\\', '/');
            String id = rootIndex + ":" + relative;
            indexed.put(id, new Document(id, title(content, path), relative, content));
        } catch (IOException ignored) {
            // Skip unreadable documents instead of exposing filesystem errors.
        }
    }

    private String title(String content, Path path) {
        return content.lines().filter(line -> line.startsWith("# ")).findFirst()
                .map(line -> line.substring(2).trim())
                .orElse(path.getFileName().toString());
    }

    private int occurrences(String text, String term) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(term, from)) >= 0) {
            count++;
            from += term.length();
        }
        return count;
    }

    public record Document(String id, String title, String path, String content) {
    }

    public record SearchHit(String id, String title, String path, String excerpt, int score) {
    }
}
// AI_GENERATE_END --
