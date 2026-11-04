// AI_GENERATE_START --------
package com.stock.console.agent;

import com.stock.console.agent.DevelopmentModelService.BusinessSpec;
import com.stock.console.agent.DevelopmentTaskStore.StoredDevelopmentTask;
import com.stock.console.agent.GitHubDevelopmentClient.BranchSnapshot;
import com.stock.console.agent.GitHubDevelopmentClient.ChangeSet;
import com.stock.console.agent.GitHubDevelopmentClient.CommitResult;
import com.stock.console.agent.GitHubDevelopmentClient.RepositoryFile;
import com.stock.console.agent.GitHubDevelopmentClient.WorkflowRun;
import com.stock.console.config.ConsoleProperties;
import jakarta.annotation.PostConstruct;
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
    private final DevelopmentTaskStore store;
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
            DevelopmentModelService model,
            DevelopmentTaskStore store) {
        this.properties = properties;
        this.github = github;
        this.model = model;
        this.store = store;
    }

    @PostConstruct
    void restoreTasks() {
        store.recent(20).forEach(saved -> tasks.put(saved.taskId(), new TaskState(saved)));
        tasks.values().stream()
                .filter(task -> !terminal(task.status))
                .sorted(Comparator.comparing(task -> task.updatedAt))
                .findFirst()
                .ifPresent(task -> {
                    if (activeTaskId.compareAndSet(null, task.taskId())) {
                        executor.submit(() -> execute(task));
                    }
                });
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
            StoredDevelopmentTask saved = store.find(taskId);
            if (saved != null) {
                task = new TaskState(saved);
                tasks.put(taskId, task);
                return task.view();
            }
            DevelopmentTaskView recovered = recover(taskId);
            if (recovered != null) {
                return recovered;
            }
            throw new IllegalArgumentException("开发任务不存在: " + taskId);
        }
        return task.view();
    }

    public DevelopmentTaskView cancel(String taskId) {
        TaskState task = tasks.get(taskId);
        if (task == null) {
            StoredDevelopmentTask saved = store.find(taskId);
            if (saved == null) {
                throw new IllegalArgumentException("开发任务不存在: " + taskId);
            }
            task = new TaskState(saved);
            tasks.put(taskId, task);
        }
        task.cancel();
        return task.view();
    }

    public List<DevelopmentTaskView> recent() {
        List<DevelopmentTaskView> current = store.recent(20).stream()
                .map(saved -> tasks.computeIfAbsent(saved.taskId(), id -> new TaskState(saved)).view())
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
            task.checkCancelled();
            if (task.commitSha != null && task.temporaryBranch != null) {
                resumeCommittedTask(task);
                return;
            }
            task.update(DevelopmentTaskStatus.ANALYZING, "正在整理业务目标和验收条件");
            BusinessSpec spec = model.analyzeRequirement(task.requirement());
            task.checkCancelled();
            BranchSnapshot base = github.branchSnapshot();
            String temporaryBranch = "agent/fix-" + task.taskId();
            task.setBranch(temporaryBranch, base.headSha());
            task.update(DevelopmentTaskStatus.READING_CODE, "正在读取仓库目录和相关源码");
            List<String> tree = github.repositoryTree(base.treeSha());
            List<String> searchMatches = search(spec);
            Set<String> existingPaths = new LinkedHashSet<>(tree);
            List<String> selectedPaths = model.selectPaths(spec, tree, searchMatches).stream()
                    .filter(existingPaths::contains)
                    .toList();
            List<RepositoryFile> files = readFiles(selectedPaths, properties.githubAgent().branch());

            if (!spec.writeAllowed()) {
                task.update(DevelopmentTaskStatus.GENERATING, "正在生成只读修改方案");
                ChangeSet plan = model.generateChangeSet(spec, files, null, List.of());
                task.setPlan(plan.changes().stream().map(change -> change.path()).toList());
                task.update(DevelopmentTaskStatus.PLANNED, "Dry-run 已完成，未创建分支、提交或发布");
                return;
            }

            task.checkCancelled();
            if (!github.branchExists(temporaryBranch)) {
                github.createBranch(temporaryBranch, base.headSha());
            }

            String expectedHead = base.headSha();
            List<String> changedFiles = List.of();
            String failureLogs = null;
            int maxAttempts = Math.max(1, properties.githubAgent().maxRepairRounds() + 1);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                task.setBuildAttempt(attempt);
                task.update(attempt == 1 ? DevelopmentTaskStatus.GENERATING : DevelopmentTaskStatus.FIXING,
                        attempt == 1 ? "正在生成结构化代码变更" : "正在根据构建日志生成修复");
                if (attempt > 1) {
                    files = readRepairFiles(selectedPaths, changedFiles, temporaryBranch);
                }
                ChangeSet changeSet = model.generateChangeSet(spec, files, failureLogs, changedFiles);
                task.checkCancelled();
                task.update(DevelopmentTaskStatus.COMMITTING, "正在提交第 " + attempt + " 版代码到临时分支");
                CommitResult commit = github.commit(task.taskId(), temporaryBranch, expectedHead, changeSet);
                expectedHead = commit.commitSha();
                changedFiles = commit.changedFiles();
                task.setCommit(commit.commitSha(), changedFiles);
                task.checkCancelled();
                github.dispatchWorkflow(task.taskId(), commit.commitSha(), temporaryBranch, false);
                task.update(DevelopmentTaskStatus.VERIFYING, "临时分支已提交，等待完整构建验证");
                WorkflowRun run = waitForRun(task, commit.commitSha(), temporaryBranch);
                task.setWorkflow(run.id(), run.htmlUrl());
                run = waitForCompletion(task, run, false);
                if ("success".equals(run.conclusion())) {
                    release(task, temporaryBranch, base.headSha(), commit.commitSha());
                    return;
                }
                failureLogs = github.workflowLogs(run.id());
                if (attempt == maxAttempts) {
                    task.fail("自动修复次数已用完，最后一次 Actions 结论: " + run.conclusion());
                    return;
                }
            }
        } catch (TaskCancelledException ignored) {
            task.cancel();
        } catch (RuntimeException exception) {
            task.fail(safeMessage(exception));
        } finally {
            activeTaskId.compareAndSet(task.taskId(), null);
        }
    }

    private void resumeCommittedTask(TaskState task) {
        String branch = task.status == DevelopmentTaskStatus.DEPLOYING
                ? properties.githubAgent().branch() : task.temporaryBranch;
        WorkflowRun run = task.workflowRunId == null
                ? waitForRun(task, task.commitSha, branch)
                : github.workflowRun(task.workflowRunId);
        run = waitForCompletion(task, run, task.status == DevelopmentTaskStatus.DEPLOYING);
        if (!"success".equals(run.conclusion())) {
            task.fail("服务恢复后 Actions 结论: " + run.conclusion());
            return;
        }
        if (properties.githubAgent().branch().equals(branch)) {
            task.update(DevelopmentTaskStatus.SUCCEEDED, "生产发布和健康检查全部通过");
            cleanupBranch(task.temporaryBranch);
            return;
        }
        release(task, task.temporaryBranch, task.targetHead, task.commitSha);
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

    private List<RepositoryFile> readFiles(List<String> paths, String ref) {
        List<RepositoryFile> files = new ArrayList<>();
        for (String path : paths.stream().limit(DevelopmentModelService.MAX_SELECTED_FILES).toList()) {
            try {
                files.add(github.readFile(path, ref));
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

    private List<RepositoryFile> readRepairFiles(
            List<String> selectedPaths,
            List<String> changedFiles,
            String ref) {
        Set<String> paths = new LinkedHashSet<>(selectedPaths);
        paths.addAll(changedFiles);
        return readFiles(paths.stream().limit(DevelopmentModelService.MAX_SELECTED_FILES).toList(), ref);
    }

    private WorkflowRun waitForRun(TaskState task, String commitSha, String branch) {
        Instant deadline = Instant.now().plus(RUN_DISCOVERY_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            task.checkCancelled();
            WorkflowRun run = github.findWorkflowRun(commitSha, branch);
            if (run != null) {
                task.setWorkflow(run.id(), run.htmlUrl());
                return run;
            }
            sleep();
        }
        throw new IllegalStateException("未在限定时间内找到对应的 GitHub Actions 运行");
    }

    private WorkflowRun waitForCompletion(TaskState task, WorkflowRun initial, boolean deployment) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(
                Math.max(5, properties.githubAgent().workflowTimeoutMinutes())));
        WorkflowRun run = initial;
        while (Instant.now().isBefore(deadline)) {
            task.checkCancelled();
            run = github.workflowRun(run.id());
            task.setWorkflow(run.id(), run.htmlUrl());
            if ("completed".equals(run.status())) {
                return run;
            }
            if (deployment) {
                String progress = github.deployJobStarted(run.id())
                        ? "镜像构建通过，正在部署并检查健康状态"
                        : "生产流水线正在构建和验证待发布镜像";
                task.update(DevelopmentTaskStatus.DEPLOYING, progress);
            } else {
                task.update(DevelopmentTaskStatus.VERIFYING,
                        "GitHub Actions 正在完整验证第 " + task.buildAttempt() + " 版代码");
            }
            sleep();
        }
        throw new IllegalStateException("GitHub Actions 超过最大等待时间");
    }

    private void release(TaskState task, String temporaryBranch, String targetHead, String commitSha) {
        task.checkCancelled();
        task.update(DevelopmentTaskStatus.RELEASING, "临时分支验证通过，正在校验目标分支并统一发布");
        if (!commitSha.equals(github.branchSnapshot().headSha())) {
            github.promoteBranch(temporaryBranch, targetHead, commitSha);
        }
        task.clearWorkflow();
        task.update(DevelopmentTaskStatus.DEPLOYING, "目标分支已更新，等待生产发布和健康检查");
        WorkflowRun run = waitForRun(task, commitSha, properties.githubAgent().branch());
        run = waitForCompletion(task, run, true);
        if (!"success".equals(run.conclusion())) {
            task.fail("生产发布失败，Actions 结论: " + run.conclusion());
            return;
        }
        task.update(DevelopmentTaskStatus.SUCCEEDED, "完整验证、生产发布和健康检查全部通过");
        cleanupBranch(temporaryBranch);
    }

    private void cleanupBranch(String branch) {
        try {
            if (github.branchExists(branch)) {
                github.deleteBranch(branch);
            }
        } catch (RuntimeException ignored) {
            // Cleanup failure does not change a verified production result.
        }
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

    private boolean terminal(DevelopmentTaskStatus status) {
        return status == DevelopmentTaskStatus.SUCCEEDED
                || status == DevelopmentTaskStatus.FAILED
                || status == DevelopmentTaskStatus.CANCELLED
                || status == DevelopmentTaskStatus.PLANNED;
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

    private final class TaskState {
        private final String taskId;
        private final String sessionId;
        private final String requirement;
        private final String createdAt;
        private volatile DevelopmentTaskStatus status = DevelopmentTaskStatus.ANALYZING;
        private volatile int buildAttempt;
        private volatile List<String> changedFiles = List.of();
        private volatile String commitSha;
        private volatile Long workflowRunId;
        private volatile String workflowUrl;
        private volatile String progress = "任务已创建";
        private volatile String failureMessage;
        private volatile String temporaryBranch;
        private volatile String targetHead;
        private volatile boolean cancelled;
        private volatile String updatedAt;

        private TaskState(String taskId, String sessionId, String requirement) {
            this.taskId = taskId;
            this.sessionId = sessionId;
            this.requirement = requirement;
            this.createdAt = Instant.now().toString();
            this.updatedAt = createdAt;
            persist();
        }

        private TaskState(StoredDevelopmentTask saved) {
            this.taskId = saved.taskId();
            this.sessionId = saved.sessionId();
            this.requirement = saved.requirement();
            this.status = saved.status();
            this.buildAttempt = saved.buildAttempt();
            this.changedFiles = saved.changedFiles() == null ? List.of() : List.copyOf(saved.changedFiles());
            this.commitSha = saved.commitSha();
            this.workflowRunId = saved.workflowRunId();
            this.workflowUrl = saved.workflowUrl();
            this.progress = saved.progress();
            this.failureMessage = saved.failureMessage();
            this.temporaryBranch = saved.temporaryBranch();
            this.targetHead = saved.targetHead();
            this.cancelled = saved.cancelled();
            this.createdAt = saved.createdAt();
            this.updatedAt = saved.updatedAt();
        }

        private synchronized void update(DevelopmentTaskStatus status, String progress) {
            checkCancelled();
            this.status = status;
            this.progress = progress;
            this.updatedAt = Instant.now().toString();
            persist();
        }

        private synchronized void fail(String failureMessage) {
            if (cancelled) {
                return;
            }
            this.status = DevelopmentTaskStatus.FAILED;
            this.progress = "自动开发任务失败";
            this.failureMessage = failureMessage;
            this.updatedAt = Instant.now().toString();
            persist();
        }

        private synchronized void cancel() {
            if (status == DevelopmentTaskStatus.SUCCEEDED || status == DevelopmentTaskStatus.FAILED
                    || status == DevelopmentTaskStatus.CANCELLED || status == DevelopmentTaskStatus.PLANNED) {
                return;
            }
            cancelled = true;
            status = DevelopmentTaskStatus.CANCELLED;
            progress = "任务已停止，后续模型和 GitHub 写操作不会继续";
            updatedAt = Instant.now().toString();
            persist();
        }

        private synchronized void setBranch(String temporaryBranch, String targetHead) {
            this.temporaryBranch = temporaryBranch;
            this.targetHead = targetHead;
            this.updatedAt = Instant.now().toString();
            persist();
        }

        private synchronized void setCommit(String commitSha, List<String> changedFiles) {
            this.commitSha = commitSha;
            this.changedFiles = List.copyOf(changedFiles);
            this.workflowRunId = null;
            this.workflowUrl = null;
            this.updatedAt = Instant.now().toString();
            persist();
        }

        private synchronized void setPlan(List<String> changedFiles) {
            this.changedFiles = List.copyOf(changedFiles);
            this.updatedAt = Instant.now().toString();
            persist();
        }

        private synchronized void setWorkflow(long workflowRunId, String workflowUrl) {
            this.workflowRunId = workflowRunId;
            this.workflowUrl = workflowUrl;
            this.updatedAt = Instant.now().toString();
            persist();
        }

        private synchronized void clearWorkflow() {
            this.workflowRunId = null;
            this.workflowUrl = null;
            this.updatedAt = Instant.now().toString();
            persist();
        }

        private void setBuildAttempt(int buildAttempt) {
            this.buildAttempt = buildAttempt;
            this.updatedAt = Instant.now().toString();
            persist();
        }

        private void checkCancelled() {
            if (cancelled) {
                throw new TaskCancelledException();
            }
        }

        private void persist() {
            store.save(new StoredDevelopmentTask(
                    taskId, sessionId, requirement, status, buildAttempt, changedFiles, commitSha,
                    workflowRunId, workflowUrl, progress, failureMessage, temporaryBranch, targetHead,
                    cancelled, createdAt, updatedAt));
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

    private static final class TaskCancelledException extends RuntimeException {
    }
}
// AI_GENERATE_END --------
