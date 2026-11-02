// AI_GENERATE_START -----
package com.stock.console.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.console.config.ConsoleProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class GitHubDevelopmentClient {

    static final int MAX_FILE_BYTES = 160_000;
    static final int MAX_LOG_CHARS = 24_000;
    private static final String API_ROOT = "https://api.github.com";
    private static final String API_VERSION = "2022-11-28";
    private static final Set<String> BINARY_SUFFIXES = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".pdf", ".zip", ".jar", ".class", ".woff", ".woff2");

    private final ConsoleProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubDevelopmentClient(ConsoleProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    GitHubDevelopmentClient(ConsoleProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean configured() {
        ConsoleProperties.GithubAgent config = properties.githubAgent();
        return config.enabled()
                && present(config.token())
                && present(config.owner())
                && present(config.repository())
                && present(config.branch())
                && present(config.workflow());
    }

    public BranchSnapshot branchSnapshot() {
        JsonNode reference = json("GET", repositoryPath("/git/ref/heads/" + encode(properties.githubAgent().branch())), null);
        String headSha = requiredText(reference.path("object"), "sha");
        JsonNode commit = json("GET", repositoryPath("/git/commits/" + headSha), null);
        return new BranchSnapshot(headSha, requiredText(commit.path("tree"), "sha"));
    }

    public List<String> repositoryTree(String treeSha) {
        JsonNode response = json("GET", repositoryPath("/git/trees/" + treeSha + "?recursive=1"), null);
        if (response.path("truncated").asBoolean(false)) {
            throw new IllegalStateException("GitHub 仓库目录过大，递归目录结果被截断");
        }
        List<String> paths = new ArrayList<>();
        for (JsonNode entry : response.path("tree")) {
            if ("blob".equals(entry.path("type").asText())) {
                paths.add(entry.path("path").asText());
            }
        }
        return paths;
    }

    public RepositoryFile readFile(String path) {
        validateReadablePath(path);
        String endpoint = repositoryPath("/contents/" + encodePath(path)
                + "?ref=" + encode(properties.githubAgent().branch()));
        JsonNode response = json("GET", endpoint, null);
        long size = response.path("size").asLong();
        if (size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文件超过 Agent 可读取上限: " + path);
        }
        String encoding = response.path("encoding").asText();
        if (!"base64".equals(encoding)) {
            throw new IllegalArgumentException("GitHub 返回了不支持的文件编码: " + path);
        }
        byte[] decoded = Base64.getMimeDecoder().decode(response.path("content").asText());
        return new RepositoryFile(path, new String(decoded, StandardCharsets.UTF_8), response.path("sha").asText());
    }

    public List<String> searchCode(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        String query = term.trim() + " repo:" + properties.githubAgent().owner() + "/"
                + properties.githubAgent().repository();
        JsonNode response = json("GET", "/search/code?q=" + encode(query) + "&per_page=20", null);
        List<String> paths = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            String path = item.path("path").asText();
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return paths;
    }

    public CommitResult commit(String taskId, String expectedHead, ChangeSet changeSet) {
        validateChangeSet(changeSet);
        BranchSnapshot latest = branchSnapshot();
        if (!latest.headSha().equals(expectedHead)) {
            throw new IllegalStateException("stock-web 分支已被其他提交更新，本次任务已停止");
        }
        List<Map<String, Object>> treeEntries = new ArrayList<>();
        for (FileChange change : changeSet.changes()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", change.path());
            entry.put("mode", "100644");
            entry.put("type", "blob");
            if (change.operation() == ChangeOperation.DELETE) {
                entry.put("sha", null);
            } else {
                JsonNode blob = json("POST", repositoryPath("/git/blobs"), Map.of(
                        "content", change.content(),
                        "encoding", "utf-8"));
                entry.put("sha", requiredText(blob, "sha"));
            }
            treeEntries.add(entry);
        }
        JsonNode tree = json("POST", repositoryPath("/git/trees"), Map.of(
                "base_tree", latest.treeSha(),
                "tree", treeEntries));
        String message = "[agent:" + taskId + "] " + changeSet.summary().trim()
                + "\n\n" + changeSet.acceptanceSummary().trim();
        JsonNode commit = json("POST", repositoryPath("/git/commits"), Map.of(
                "message", message,
                "tree", requiredText(tree, "sha"),
                "parents", List.of(expectedHead)));
        String commitSha = requiredText(commit, "sha");
        json("PATCH", repositoryPath("/git/refs/heads/" + encode(properties.githubAgent().branch())), Map.of(
                "sha", commitSha,
                "force", false));
        return new CommitResult(commitSha, changeSet.changes().stream().map(FileChange::path).toList());
    }

    public void dispatchWorkflow(String taskId, String commitSha) {
        json("POST", repositoryPath("/actions/workflows/" + encode(properties.githubAgent().workflow()) + "/dispatches"), Map.of(
                "ref", properties.githubAgent().branch(),
                "inputs", Map.of("task_id", taskId, "commit_sha", commitSha)));
    }

    public WorkflowRun findWorkflowRun(String commitSha) {
        String endpoint = repositoryPath("/actions/runs?branch="
                + encode(properties.githubAgent().branch()) + "&per_page=30");
        JsonNode response = json("GET", endpoint, null);
        for (JsonNode run : response.path("workflow_runs")) {
            if (commitSha.equals(run.path("head_sha").asText()) && workflowPath().equals(run.path("path").asText())) {
                return workflowRun(run);
            }
        }
        return null;
    }

    public WorkflowRun workflowRun(long runId) {
        return workflowRun(json("GET", repositoryPath("/actions/runs/" + runId), null));
    }

    public boolean deployJobStarted(long runId) {
        JsonNode response = json("GET", repositoryPath("/actions/runs/" + runId + "/jobs?per_page=100"), null);
        for (JsonNode job : response.path("jobs")) {
            if ("deploy".equalsIgnoreCase(job.path("name").asText())) {
                String status = job.path("status").asText();
                return "in_progress".equals(status) || "completed".equals(status);
            }
        }
        return false;
    }

    public String workflowLogs(long runId) {
        byte[] archive = bytes("GET", repositoryPath("/actions/runs/" + runId + "/logs"));
        StringBuilder logs = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8_192];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                logs.append("\n--- ").append(entry.getName()).append(" ---\n");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) >= 0 && output.size() <= MAX_LOG_CHARS * 2) {
                    output.write(buffer, 0, read);
                }
                logs.append(output.toString(StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法解析 GitHub Actions 日志", exception);
        }
        String value = logs.toString();
        return value.length() <= MAX_LOG_CHARS ? value : value.substring(value.length() - MAX_LOG_CHARS);
    }

    public boolean hasActiveWorkflow() {
        String endpoint = repositoryPath("/actions/runs?branch="
                + encode(properties.githubAgent().branch()) + "&per_page=20");
        JsonNode response = json("GET", endpoint, null);
        for (JsonNode run : response.path("workflow_runs")) {
            String status = run.path("status").asText();
            if (workflowPath().equals(run.path("path").asText())
                    && ("queued".equals(status) || "in_progress".equals(status) || "waiting".equals(status))) {
                return true;
            }
        }
        return false;
    }

    public TaskCommit findTaskCommit(String taskId) {
        return recentTaskCommits(50).stream()
                .filter(commit -> commit.taskId().equals(taskId))
                .findFirst()
                .orElse(null);
    }

    public List<TaskCommit> recentTaskCommits(int limit) {
        int requested = Math.max(1, Math.min(100, limit));
        JsonNode response = json("GET", repositoryPath("/commits?sha="
                + encode(properties.githubAgent().branch()) + "&per_page=" + requested), null);
        Map<String, Integer> attempts = new HashMap<>();
        for (JsonNode item : response) {
            String taskId = taskId(item.path("commit").path("message").asText());
            if (taskId != null) {
                attempts.merge(taskId, 1, Integer::sum);
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        List<TaskCommit> commits = new ArrayList<>();
        for (JsonNode item : response) {
            String message = item.path("commit").path("message").asText();
            String taskId = taskId(message);
            if (taskId == null || !seen.add(taskId)) {
                continue;
            }
            int closing = message.indexOf(']');
            String summary = message.substring(closing + 1).trim().lines().findFirst().orElse("Agent 自动开发");
            String sha = item.path("sha").asText();
            JsonNode detail = json("GET", repositoryPath("/commits/" + sha), null);
            List<String> files = new ArrayList<>();
            for (JsonNode file : detail.path("files")) {
                files.add(file.path("filename").asText());
            }
            String authoredAt = item.path("commit").path("author").path("date").asText();
            commits.add(new TaskCommit(taskId, sha, summary, files, authoredAt, attempts.getOrDefault(taskId, 1)));
        }
        return commits;
    }

    private String taskId(String message) {
        if (message == null || !message.startsWith("[agent:")) {
            return null;
        }
        int closing = message.indexOf(']');
        return closing <= 7 ? null : message.substring(7, closing);
    }

    private WorkflowRun workflowRun(JsonNode run) {
        return new WorkflowRun(
                run.path("id").asLong(),
                run.path("status").asText(),
                run.path("conclusion").isNull() ? null : run.path("conclusion").asText(null),
                run.path("html_url").asText(),
                run.path("head_sha").asText());
    }

    void validateChangeSet(ChangeSet changeSet) {
        if (changeSet == null || changeSet.changes() == null || changeSet.changes().isEmpty()) {
            throw new IllegalArgumentException("Agent 未生成任何文件修改");
        }
        if (!present(changeSet.summary()) || !present(changeSet.acceptanceSummary())) {
            throw new IllegalArgumentException("Agent 变更缺少提交摘要或验收说明");
        }
        Set<String> paths = new LinkedHashSet<>();
        for (FileChange change : changeSet.changes()) {
            validateWritablePath(change.path());
            if (!paths.add(change.path())) {
                throw new IllegalArgumentException("Agent 重复修改文件: " + change.path());
            }
            if (change.operation() == ChangeOperation.UPSERT) {
                if (change.content() == null || change.content().isEmpty()) {
                    throw new IllegalArgumentException("Agent 生成了空文件: " + change.path());
                }
                if (change.content().getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Agent 生成文件超过大小限制: " + change.path());
                }
            }
        }
    }

    private void validateReadablePath(String path) {
        validatePath(path);
        String lower = path.toLowerCase();
        if (BINARY_SUFFIXES.stream().anyMatch(lower::endsWith)) {
            throw new IllegalArgumentException("Agent 不读取二进制文件: " + path);
        }
    }

    private void validateWritablePath(String path) {
        validateReadablePath(path);
        if (path.startsWith(".github/workflows/")) {
            throw new IllegalArgumentException("Agent 禁止修改 GitHub Actions 工作流: " + path);
        }
    }

    private void validatePath(String path) {
        if (!present(path) || !path.equals(path.trim()) || path.startsWith("/") || path.startsWith("\\") || path.contains("..")
                || path.contains("\\") || path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("非法仓库路径: " + path);
        }
    }

    private JsonNode json(String method, String path, Object body) {
        try {
            HttpRequest.Builder request = request(path);
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw apiFailure(method, path, response.statusCode(), response.body());
            }
            return response.body().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("GitHub API 响应解析失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub API 调用被中断", exception);
        }
    }

    private byte[] bytes(String method, String path) {
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    request(path).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw apiFailure(method, path, response.statusCode(), "binary response");
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("GitHub API 日志下载失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub API 日志下载被中断", exception);
        }
    }

    private HttpRequest.Builder request(String path) {
        if (!configured()) {
            throw new IllegalStateException("GitHub 开发 Agent 未配置");
        }
        return HttpRequest.newBuilder(URI.create(API_ROOT + path))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + properties.githubAgent().token())
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "stock-console-development-agent");
    }

    private IllegalStateException apiFailure(String method, String path, int status, String body) {
        String message = body == null ? "" : body.replaceAll("[\\r\\n]+", " ");
        if (message.length() > 300) {
            message = message.substring(0, 300);
        }
        return new IllegalStateException("GitHub API 调用失败: " + method + " " + path
                + " status=" + status + " " + message);
    }

    private String repositoryPath(String suffix) {
        return "/repos/" + encode(properties.githubAgent().owner()) + "/"
                + encode(properties.githubAgent().repository()) + suffix;
    }

    private String workflowPath() {
        return ".github/workflows/" + properties.githubAgent().workflow();
    }

    private String encodePath(String value) {
        return String.join("/", Arrays.stream(value.split("/", -1)).map(this::encode).toList());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("GitHub API 响应缺少字段: " + field);
        }
        return value;
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    public record BranchSnapshot(String headSha, String treeSha) {
    }

    public record RepositoryFile(String path, String content, String sha) {
    }

    public record CommitResult(String commitSha, List<String> changedFiles) {
    }

    public record WorkflowRun(long id, String status, String conclusion, String htmlUrl, String headSha) {
    }

    public record TaskCommit(
            String taskId,
            String commitSha,
            String summary,
            List<String> changedFiles,
            String authoredAt,
            int buildAttempt) {
    }

    public enum ChangeOperation {
        UPSERT,
        DELETE
    }

    public record FileChange(String path, ChangeOperation operation, String content) {
    }

    public record ChangeSet(String summary, String acceptanceSummary, List<FileChange> changes) {
    }
}
// AI_GENERATE_END -----
