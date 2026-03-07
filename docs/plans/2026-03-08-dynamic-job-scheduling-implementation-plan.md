# 动态任务调度系统 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现后端动态任务调度系统，使用 `JobConfig` 持久化配置并通过 `JobSchedulerService` 管理调度，替换现有 `@Scheduled` 任务，并提供管理 API。

**Architecture:** 新增 `com.stock.job` 包包含实体、仓库、调度服务与管理控制器；调度服务启动加载启用任务并维护任务注册表；旧调度类改为可被调用的方法集合并由调度服务触发。

**Tech Stack:** Spring Boot 3.2, Spring Data JPA, ThreadPoolTaskScheduler

---

### Task 1: 新增 JobConfig 实体与仓库

**Files:**
- Create: `backend/src/main/java/com/stock/job/entity/JobConfig.java`
- Create: `backend/src/main/java/com/stock/job/repository/JobConfigRepository.java`

**Step 1: Write the failing test**

```java
// backend/src/test/java/com/stock/job/JobConfigRepositoryTest.java
// TODO: 添加简单的 Repository 测试（如 findByJobKey）
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=JobConfigRepositoryTest`
Expected: FAIL（类不存在）

**Step 3: Write minimal implementation**

```java
@Entity
@Table(name = "job_config")
public class JobConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String jobKey;
    private String jobName;
    private String cron;
    private boolean enabled;
    private String handler;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

```java
public interface JobConfigRepository extends JpaRepository<JobConfig, Long> {
    Optional<JobConfig> findByJobKey(String jobKey);
    List<JobConfig> findByEnabledTrue();
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=JobConfigRepositoryTest`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/stock/job/entity/JobConfig.java backend/src/main/java/com/stock/job/repository/JobConfigRepository.java backend/src/test/java/com/stock/job/JobConfigRepositoryTest.java
git commit -m "feat: add job config entity and repository"
```

---

### Task 2: 实现 JobSchedulerService

**Files:**
- Create: `backend/src/main/java/com/stock/job/service/JobSchedulerService.java`
- Modify: `backend/src/main/java/com/stock/dataCollector/scheduled/DataSyncScheduler.java`
- Modify: `backend/src/main/java/com/stock/strategyAnalysis/scheduled/StrategyScheduler.java`

**Step 1: Write the failing test**

```java
// backend/src/test/java/com/stock/job/JobSchedulerServiceTest.java
// TODO: 使用 @SpringBootTest 验证 jobKey 映射与 runOnce 逻辑
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=JobSchedulerServiceTest`
Expected: FAIL（类不存在）

**Step 3: Write minimal implementation**

核心能力：
- 初始化 `ThreadPoolTaskScheduler`
- 启动加载 `enabled=true` 任务并注册
- 维护 `jobKey -> ScheduledFuture`
- `scheduleJob`、`cancelJob`、`reloadJob`、`reloadAll`、`runOnce`
- 固定映射到 `DataSyncScheduler` / `StrategyScheduler`

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=JobSchedulerServiceTest`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/stock/job/service/JobSchedulerService.java backend/src/main/java/com/stock/dataCollector/scheduled/DataSyncScheduler.java backend/src/main/java/com/stock/strategyAnalysis/scheduled/StrategyScheduler.java backend/src/test/java/com/stock/job/JobSchedulerServiceTest.java
git commit -m "feat: add job scheduler service and refactor schedulers"
```

---

### Task 3: 新增 JobAdminController 并删除旧 JobController

**Files:**
- Create: `backend/src/main/java/com/stock/job/controller/JobAdminController.java`
- Delete: `backend/src/main/java/com/stock/dataCollector/controller/JobController.java`

**Step 1: Write the failing test**

```java
// backend/src/test/java/com/stock/job/JobAdminControllerTest.java
// TODO: 使用 MockMvc 验证 list/run/pause/resume 接口
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=JobAdminControllerTest`
Expected: FAIL（类不存在）

**Step 3: Write minimal implementation**

接口：
- `GET /api/job/admin/list`
- `POST /api/job/admin/create`
- `PUT /api/job/admin/update`
- `POST /api/job/admin/run`
- `POST /api/job/admin/pause`
- `POST /api/job/admin/resume`

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=JobAdminControllerTest`
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/stock/job/controller/JobAdminController.java backend/src/main/java/com/stock/dataCollector/controller/JobController.java backend/src/test/java/com/stock/job/JobAdminControllerTest.java
git commit -m "feat: add job admin controller and remove old job controller"
```

---

### Task 4: 编译验证

**Files:**
- None

**Step 1: Run compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

**Step 2: Commit**

```bash
git status
```
