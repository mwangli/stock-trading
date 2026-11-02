// AI_GENERATE_START -------
package com.stock.console.agent;

import com.stock.console.agent.DevelopmentModelService.BusinessSpec;
import com.stock.console.agent.GitHubDevelopmentClient.BranchSnapshot;
import com.stock.console.agent.GitHubDevelopmentClient.ChangeSet;
import com.stock.console.agent.GitHubDevelopmentClient.CommitResult;
import com.stock.console.agent.GitHubDevelopmentClient.RepositoryFile;
import com.stock.console.agent.GitHubDevelopmentClient.WorkflowRun;
import com.stock.console.config.ConsoleProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DevelopmentTaskService {

    static final int MAX_REQUIREMENT_LENGTH = 8_000;
    private static final Duration RUN_DISCOVERY_TIMEOUT = Duration.ofMinutes(3);

    private final ConsoleProperties properties;
    private final GitHubDevelopmentClient github;
    private final DevelopmentModelService model;
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeTaskId = new AtomicReference<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "github-development-agent");
        thread.setDaemon(true);
        return thread;
    });

    public DevelopmentTaskService(
            ConsoleProperties properties,
            GitHubDevelopmentClient github,
            DevelopmentModelService model) {
        this.properties = properties;
        this.github = github;
        this.model = model;
    }

    public DevelopmentTaskView start(String sessionId, String requirement) {
        String normalized = validateRequirement(requirement);
        ensureConfigured();
        if (activeTaskId.get() != null || github.hasActiveWorkflow()) {
            throw new DevelopmentTaskConflictException("已有自动开发或发布任务正在运行");
        }
        String taskId = compactTaskId();
        if (!activeTaskId.compareAndSet(null, taskId)) {
            throw new DevelopmentTaskConflictException("已有自动开发任务正在运行");
        }
        TaskState task = new TaskState(taskId, sessionId, normalized);
        tasks.put(taskId, task);
        executor.submit(() -> execute(task));
        return task.view();
    }

    public DevelopmentTaskView task(String taskId) {
        TaskState task = tasks.get(taskId);
        if (task == null) {
            DevelopmentTaskView recovered = recover(taskId);
            if (recovered != null) {
                return recovered;
            }
            throw new IllegalArgumentException("开发任务不存在: " + taskId);
        }
        return task.view();
    }

    public List<DevelopmentTaskView> recent() {
        List<DevelopmentTaskView> current = tasks.values().stream()
                .map(TaskState::view)
                .sorted(Comparator.comparing(DevelopmentTaskView::updatedAt).reversed())
                .limit(20)
                .toList();
        if (!current.isEmpty()) {
            return current;
        }
        return github.recentTaskCommits(20).stream().map(this::recover).toList();
    }

    public Map<String, Object> status() {
        ConsoleProperties.GithubAgent config = properties.githubAgent();
        return Map.of(
                "enabled", config.enabled(),
                "configured", github.configured(),
                "owner", config.owner(),
                "repository", config.repository(),
                "branch", config.branch(),
                "workflow", config.workflow(),
                "maxRepairRounds", config.maxRepairRounds(),
                "activeTaskId", activeTaskId.get() == null ? "" : activeTaskId.get());
    }

    private void execute(TaskState task) {
        try {
            task.update(DevelopmentTaskStatus.ANALYZING, "正在整理业务目标和验收条件");
            BusinessSpec spec = model.analyzeRequirement(task.requirement());
            BranchSnapshot base = github.branchSnapshot();
            task.update(DevelopmentTaskStatus.READING_CODE, "正在读取仓库目录和相关源码");
            List<String> tree = github.repositoryTree(base.treeSha());
            List<String> searchMatches = search(spec);
            Set<String> existingPaths = new LinkedHashSet<>(tree);
            List<String> selectedPaths = model.selectPaths(spec, tree, searchMatches).stream()
                    .filter(existingPaths::contains)
                    .toList();
            List<RepositoryFile> files = readFiles(selectedPaths);

            String expectedHead = base.headSha();
            List<String> changedFiles = List.of();
            String failureLogs = null;
            int maxAttempts = Math.max(1, properties.githubAgent().maxRepairRounds() + 1);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                task.setBuildAttempt(attempt);
                task.update(attempt == 1 ? DevelopmentTaskStatus.GENERATING : DevelopmentTaskStatus.FIXING,
                        attempt == 1 ? "正在生成结构化代码变更" : "正在根据构建日志生成修复");
                if (attempt > 1) {
                    files = readRepairFiles(selectedPaths, changedFiles);
                }
                ChangeSet changeSet = model.generateChangeSet(spec, files, failureLogs, changedFiles);
                task.update(DevelopmentTaskStatus.COMMITTING, "正在提交第 " + attempt + " 版代码");
                CommitResult commit = github.commit(task.taskId(), expectedHead, changeSet);
                expectedHead = commit.commitSha();
                changedFiles = commit.changedFiles();
                task.setCommit(commit.commitSha(), changedFiles);
                task.update(DevelopmentTaskStatus.BUILDING, "代码已提交，等待 stock-web push 触发 GitHub Actions");
                WorkflowRun run = waitForRun(task, commit.commitSha());
                task.setWorkflow(run.id(), run.htmlUrl());
                run = waitForCompletion(task, run);
                if ("success".equals(run.conclusion())) {
                    task.update(DevelopmentTaskStatus.SUCCEEDED, "构建、部署和健康检查全部通过");
                    return;
                }
                failureLogs = github.workflowLogs(run.id());
                if (attempt == maxAttempts) {
                    task.fail("自动修复次数已用完，最后一次 Actions 结论: " + run.conclusion());
                    return;
                }
            }
        } catch (RuntimeException exception) {
            task.fail(safeMessage(exception));
        } finally {
            activeTaskId.compareAndSet(task.taskId(), null);
        }
    }

    private List<String> search(BusinessSpec spec) {
        if (spec.searchTerms() == null) {
            return List.of();
        }
        Set<String> matches = new LinkedHashSet<>();
        for (String term : spec.searchTerms().stream().limit(6).toList()) {
            try {
                matches.addAll(github.searchCode(term));
            } catch (RuntimeException ignored) {
                // Directory selection remains available when GitHub code search is unavailable.
            }
        }
        return matches.stream().limit(40).toList();
    }

    private List<RepositoryFile> readFiles(List<String> paths) {
        List<RepositoryFile> files = new ArrayList<>();
        for (String path : paths.stream().limit(DevelopmentModelService.MAX_SELECTED_FILES).toList()) {
            try {
                files.add(github.readFile(path));
            } catch (IllegalArgumentException exception) {
                if ("AGENTS.md".equals(path)) {
                    throw exception;
                }
            }
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("代码 Agent 未选择到可读取的源码文件");
        }
        return files;
    }

    private List<RepositoryFile> readRepairFiles(List<String> selectedPaths, List<String> changedFiles) {
        Set<String> paths = new LinkedHashSet<>(selectedPaths);
        paths.addAll(changedFiles);
        return readFiles(paths.stream().limit(DevelopmentModelService.MAX_SELECTED_FILES).toList());
    }

    private WorkflowRun waitForRun(TaskState task, String commitSha) {
        Instant deadline = Instant.now().plus(RUN_DISCOVERY_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            WorkflowRun run = github.findWorkflowRun(commitSha);
            if (run != null) {
                task.setWorkflow(run.id(), run.htmlUrl());
                return run;
            }
            sleep();
        }
        throw new IllegalStateException("未在限定时间内找到对应的 GitHub Actions 运行");
    }

    private WorkflowRun waitForCompletion(TaskState task, WorkflowRun initial) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(
                Math.max(5, properties.githubAgent().workflowTimeoutMinutes())));
        WorkflowRun run = initial;
        while (Instant.now().isBefore(deadline)) {
            run = github.workflowRun(run.id());
            task.setWorkflow(run.id(), run.htmlUrl());
            if ("completed".equals(run.status())) {
                return run;
            }
            if (github.deployJobStarted(run.id())) {
                task.update(DevelopmentTaskStatus.DEPLOYING, "镜像构建通过，正在部署并检查健康状态");
            } else {
                task.update(DevelopmentTaskStatus.BUILDING, "GitHub Actions 正在构建第 " + task.buildAttempt() + " 版代码");
            }
            sleep();
        }
        throw new IllegalStateException("GitHub Actions 超过最大等待时间");
    }

    private DevelopmentTaskView recover(String taskId) {
        GitHubDevelopmentClient.TaskCommit commit = github.findTaskCommit(taskId);
        return commit == null ? null : recover(commit);
    }

    private DevelopmentTaskView recover(GitHubDevelopmentClient.TaskCommit commit) {
        WorkflowRun run = github.findWorkflowRun(commit.commitSha());
        DevelopmentTaskStatus status = DevelopmentTaskStatus.COMMITTING;
        String progress = "已恢复 Agent 提交，等待 Actions 信息";
        String failure = null;
        Long runId = null;
        String runUrl = null;
        if (run != null) {
            runId = run.id();
            runUrl = run.htmlUrl();
            if ("completed".equals(run.status())) {
                if ("success".equals(run.conclusion())) {
                    status = DevelopmentTaskStatus.SUCCEEDED;
                    progress = "构建、部署和健康检查全部通过";
                } else {
                    status = DevelopmentTaskStatus.FAILED;
                    progress = "GitHub Actions 执行失败";
                    failure = "Actions 结论: " + run.conclusion();
                }
            } else {
                status = github.deployJobStarted(run.id())
                        ? DevelopmentTaskStatus.DEPLOYING : DevelopmentTaskStatus.BUILDING;
                progress = status == DevelopmentTaskStatus.DEPLOYING ? "正在部署" : "正在构建";
            }
        }
        return new DevelopmentTaskView(
                commit.taskId(), null, commit.summary(), status, commit.buildAttempt(), commit.changedFiles(), commit.commitSha(),
                runId, runUrl, progress, failure, commit.authoredAt(), commit.authoredAt());
    }

    private void ensureConfigured() {
        if (!github.configured()) {
            throw new IllegalStateException("GitHub 开发 Agent 未启用或配置不完整");
        }
        ConsoleProperties.Agent agent = properties.agent();
        if (!agent.enabled() || agent.apiKey() == null || agent.apiKey().isBlank()) {
            throw new IllegalStateException("代码模型未启用或 API Key 未配置");
        }
    }

    private String validateRequirement(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("开发需求不能为空");
        }
        String normalized = requirement.trim();
        if (normalized.length() > MAX_REQUIREMENT_LENGTH) {
            throw new IllegalArgumentException("开发需求不能超过 " + MAX_REQUIREMENT_LENGTH + " 个字符");
        }
        return normalized;
    }

    private String compactTaskId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void sleep() {
        try {
            Thread.sleep(Duration.ofSeconds(Math.max(2, properties.githubAgent().pollIntervalSeconds())).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("自动开发任务已被中断", exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static final class TaskState {
        private final String taskId;
        private final String sessionId;
        private final String requirement;
        private final String createdAt = Instant.now().toString();
        private volatile DevelopmentTaskStatus status = DevelopmentTaskStatus.ANALYZING;
        private volatile int buildAttempt;
        private volatile List<String> changedFiles = List.of();
        private volatile String commitSha;
        private volatile Long workflowRunId;
        private volatile String workflowUrl;
        private volatile String progress = "任务已创建";
        private volatile String failureMessage;
        private volatile String updatedAt = createdAt;

        private TaskState(String taskId, String sessionId, String requirement) {
            this.taskId = taskId;
            this.sessionId = sessionId;
            this.requirement = requirement;
        }

        private synchronized void update(DevelopmentTaskStatus status, String progress) {
            this.status = status;
            this.progress = progress;
            this.updatedAt = Instant.now().toString();
        }

        private synchronized void fail(String failureMessage) {
            this.status = DevelopmentTaskStatus.FAILED;
            this.progress = "自动开发任务失败";
            this.failureMessage = failureMessage;
            this.updatedAt = Instant.now().toString();
        }

        private synchronized void setCommit(String commitSha, List<String> changedFiles) {
            this.commitSha = commitSha;
            this.changedFiles = List.copyOf(changedFiles);
            this.updatedAt = Instant.now().toString();
        }

        private synchronized void setWorkflow(long workflowRunId, String workflowUrl) {
            this.workflowRunId = workflowRunId;
            this.workflowUrl = workflowUrl;
            this.updatedAt = Instant.now().toString();
        }

        private void setBuildAttempt(int buildAttempt) {
            this.buildAttempt = buildAttempt;
        }

        private String taskId() {
            return taskId;
        }

        private String requirement() {
            return requirement;
        }

        private int buildAttempt() {
            return buildAttempt;
        }

        private DevelopmentTaskView view() {
            return new DevelopmentTaskView(
                    taskId, sessionId, requirement, status, buildAttempt, changedFiles, commitSha,
                    workflowRunId, workflowUrl, progress, failureMessage, createdAt, updatedAt);
        }
    }
}
// AI_GENERATE_END -------
