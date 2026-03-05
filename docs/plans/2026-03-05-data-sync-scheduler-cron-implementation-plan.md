# Data Sync Scheduler Cron Update Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Update DataSyncScheduler cron schedules to run stock data sync at 18:00 on trading days and stock list sync weekly on Sunday 01:00.

**Architecture:** Make a minimal, localized change in `DataSyncScheduler.java` by editing `@Scheduled` cron expressions and aligning related Javadoc/log text if needed. No changes to service logic or other modules.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring `@Scheduled` (Quartz cron format).

---

### Task 1: 更新定时任务 cron 表达式

**Files:**
- Modify: `backend/src/main/java/com/stock/dataCollector/scheduled/DataSyncScheduler.java`

**Step 1: Write the failing test**

不新增测试（调度 cron 变更无合适的自动化测试入口，风险较低）。

**Step 2: Run test to verify it fails**

跳过（无新增测试）。

**Step 3: Write minimal implementation**

将两处 `@Scheduled` 更新为：

```java
@Scheduled(cron = "0 0 18 * * MON-FRI")
public void syncDailyStockData() {
    ...
}

@Scheduled(cron = "0 0 1 ? * SUN")
public void syncStockListDaily() {
    ...
}
```

如发现注释/日志时间描述不一致，顺带更新为“交易日 18:00”“每周日 01:00”。

**Step 4: Run test to verify it passes**

跳过（无新增测试）。

**Step 5: Static checks**

运行 LSP 诊断：

```bash
# 对修改文件执行 LSP 诊断
```

预期：无诊断问题。

**Step 6: Build**

```bash
cd backend
mvn compile -pl data-collector
```

预期：编译成功。

**Step 7: Commit**

```bash
git add backend/src/main/java/com/stock/dataCollector/scheduled/DataSyncScheduler.java
git commit -m "fix(data-collector): update data sync scheduler cron"
```

---

### Task 2: 输出修改后的文件内容给用户

**Files:**
- Read: `backend/src/main/java/com/stock/dataCollector/scheduled/DataSyncScheduler.java`

**Step 1: Read file**

```bash
# 使用 Read 工具输出修改后文件内容
```

**Step 2: Respond**

在响应中粘贴（或概述）修改后的完整文件内容。
