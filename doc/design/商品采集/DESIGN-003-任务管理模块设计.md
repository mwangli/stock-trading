# DESIGN-003: 任务管理模块设计

## 基本信息
- **文档编号**: DESIGN-003
- **版本**: v1.0
- **状态**: 评审中
- **作者**: AI Assistant
- **最后更新**: 2026-02-16

## 变更日志
| 版本 | 日期 | 作者 | 变更内容 |
|------|------|------|----------|
| v1.0 | 2026-02-16 | AI Assistant | 初始版本 |

## 1. 设计概述

### 1.1 设计目标
构建任务管理模块，提供统一的商品采集任务管理能力，支持手动触发和定时调度。

### 1.2 设计原则
- **简洁高效**: 使用Spring原生Scheduler，避免引入额外依赖
- **解耦设计**: 任务调度与任务执行分离
- **可观测性**: 完整的任务状态和日志记录

## 2. 方案设计

### 2.1 模块架构

```
┌─────────────────────────────────────────────────────────────┐
│                     任务管理模块                              │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    前端层                              │   │
│  │  ┌─────────────────────────────────────────────┐    │   │
│  │  │         任务管理页面 (CollectionTasks)       │    │   │
│  │  │  - 任务列表                                   │    │   │
│  │  │  - 创建任务                                   │    │   │
│  │  │  - 手动执行                                   │    │   │
│  │  │  - 执行日志                                   │    │   │
│  │  └─────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP API
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    后端层                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              CollectionTaskController               │   │
│  │    POST /api/collection-tasks        创建任务        │   │
│  │    GET  /api/collection-tasks        获取任务列表    │   │
│  │    GET  /api/collection-tasks/{id}   获取任务详情    │   │
│  │    POST /api/collection-tasks/{id}/execute  执行任务 │   │
│  │    POST /api/collection-tasks/{id}/stop  停止任务    │   │
│  │    POST /api/collection-tasks/{id}/toggle 启用/禁用  │   │
│  │    POST /api/collection-tasks/quick-start 快速执行    │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              CollectionTaskService                   │   │
│  │    - 任务CRUD操作                                    │   │
│  │    - 任务状态管理                                     │   │
│  │    - 执行结果记录                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              CollectionScheduler                     │   │
│  │    - 每分钟检查待执行定时任务                          │   │
│  │    - 按Cron表达式计算下次执行时间                      │   │
│  │    - 触发任务执行                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           ProductCollectorService                   │   │
│  │    - 实际采集逻辑                                     │   │
│  │    - 品牌过滤                                         │   │
│  │    - 数据保存                                         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    数据层                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  collection_tasks 表 (采集任务)                       │   │
│  │  collection_logs 表 (采集日志)                        │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 触发机制

#### 2.2.1 手动触发
```
用户点击"执行"按钮
        │
        ▼
CollectionTaskController.execute(id)
        │
        ▼
CollectionTaskService.startTask(id)  → 更新状态为RUNNING
        │
        ▼
ProductCollectorService.collectProducts(task)  → 异步执行采集
        │
        ▼
CollectionTaskService.completeTask(id, result)  → 更新结果
```

#### 2.2.2 定时触发
```
Spring Scheduler (每分钟)
        │
        ▼
CollectionScheduler.checkScheduledTasks()
        │
        ▼
查询待执行的定时任务 (nextRunTime <= now AND status=PENDING)
        │
        ▼
遍历执行每个定时任务
        │
        ▼
更新任务状态 → 异步执行 → 记录结果
```

### 2.3 数据库设计

#### 2.3.1 采集任务表 (collection_tasks)
```sql
CREATE TABLE collection_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(50) NOT NULL COMMENT '任务类型: MANUAL/SCHEDULED',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/COMPLETED/FAILED',
    category_filter VARCHAR(255) COMMENT '分类过滤',
    max_products INT DEFAULT 100 COMMENT '最大采集数量',
    cron_expression VARCHAR(100) COMMENT 'Cron表达式(SCHEDULED类型)',
    next_run_time DATETIME COMMENT '下次执行时间',
    actual_count INT DEFAULT 0 COMMENT '实际采集数量',
    success_count INT DEFAULT 0 COMMENT '成功数量',
    failed_count INT DEFAULT 0 COMMENT '失败数量',
    filtered_count INT DEFAULT 0 COMMENT '过滤数量',
    duration_seconds INT DEFAULT 0 COMMENT '执行耗时(秒)',
    error_message TEXT COMMENT '错误信息',
    is_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    started_at DATETIME COMMENT '开始时间',
    completed_at DATETIME COMMENT '完成时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间'
);
```

#### 2.3.2 采集日志表 (collection_logs)
```sql
CREATE TABLE collection_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '关联任务ID',
    task_name VARCHAR(255) NOT NULL COMMENT '任务名称',
    status VARCHAR(50) NOT NULL COMMENT '执行状态',
    actual_count INT DEFAULT 0 COMMENT '实际采集数量',
    success_count INT DEFAULT 0 COMMENT '成功数量',
    failed_count INT DEFAULT 0 COMMENT '失败数量',
    filtered_count INT DEFAULT 0 COMMENT '过滤数量',
    duration_seconds INT DEFAULT 0 COMMENT '执行耗时',
    error_message TEXT COMMENT '错误信息',
    executed_at DATETIME NOT NULL COMMENT '执行时间'
);
```

### 2.4 API接口设计

#### 2.4.1 任务管理接口
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/collection-tasks | 获取任务列表 |
| POST | /api/collection-tasks | 创建任务 |
| GET | /api/collection-tasks/{id} | 获取任务详情 |
| DELETE | /api/collection-tasks/{id} | 删除任务 |
| POST | /api/collection-tasks/{id}/execute | 执行任务 |
| POST | /api/collection-tasks/{id}/stop | 停止任务 |
| POST | /api/collection-tasks/{id}/toggle | 启用/禁用任务 |
| POST | /api/collection-tasks/quick-start | 快速执行采集 |

#### 2.4.2 请求/响应示例

**创建任务**
```json
// POST /api/collection-tasks
// Request
{
  "taskName": "每日采集任务",
  "taskType": "SCHEDULED",
  "categoryFilter": "electronics",
  "maxProducts": 100,
  "cronExpression": "0 0 2 * * ?"
}

// Response
{
  "success": true,
  "message": "任务创建成功",
  "data": {
    "id": 1,
    "taskName": "每日采集任务",
    "taskType": "SCHEDULED",
    "status": "PENDING",
    "cronExpression": "0 0 2 * * ?",
    "nextRunTime": "2026-02-17T02:00:00"
  }
}
```

**执行任务**
```json
// POST /api/collection-tasks/1/execute
// Response
{
  "success": true,
  "message": "任务已启动",
  "data": {
    "id": 1,
    "status": "RUNNING",
    "startedAt": "2026-02-16T14:00:00"
  }
}
```

**任务列表**
```json
// GET /api/collection-tasks
// Response
{
  "success": true,
  "data": [
    {
      "id": 1,
      "taskName": "每日采集任务",
      "taskType": "SCHEDULED",
      "status": "COMPLETED",
      "cronExpression": "0 0 2 * * ?",
      "nextRunTime": "2026-02-17T02:00:00",
      "actualCount": 85,
      "successCount": 80,
      "failedCount": 2,
      "filteredCount": 3,
      "durationSeconds": 120,
      "isEnabled": true,
      "createdAt": "2026-02-16T10:00:00"
    }
  ],
  "total": 1
}
```

## 3. 详细设计

### 3.1 实体类

#### CollectionTask
```java
@Data
@TableName("collection_tasks")
public class CollectionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("task_name")
    private String taskName;
    
    @TableField("task_type")
    private String taskType;  // MANUAL / SCHEDULED
    
    @TableField("status")
    private String status;    // PENDING / RUNNING / COMPLETED / FAILED
    
    @TableField("category_filter")
    private String categoryFilter;
    
    @TableField("max_products")
    private Integer maxProducts;
    
    @TableField("cron_expression")
    private String cronExpression;
    
    @TableField("next_run_time")
    private LocalDateTime nextRunTime;
    
    @TableField("actual_count")
    private Integer actualCount;
    
    @TableField("success_count")
    private Integer successCount;
    
    @TableField("failed_count")
    private Integer failedCount;
    
    @TableField("filtered_count")
    private Integer filteredCount;
    
    @TableField("duration_seconds")
    private Integer durationSeconds;
    
    @TableField("error_message")
    private String errorMessage;
    
    @TableField("is_enabled")
    private Boolean isEnabled;
    
    @TableField("started_at")
    private LocalDateTime startedAt;
    
    @TableField("completed_at")
    private LocalDateTime completedAt;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

### 3.2 定时调度设计

```java
@Component
@Slf4j
public class CollectionScheduler {
    
    @Autowired
    private CollectionTaskService collectionTaskService;
    
    @Autowired
    private ProductCollectorService productCollectorService;
    
    /**
     * 每分钟检查并执行到期的定时任务
     */
    @Scheduled(cron = "0 * * * * ?")
    public void checkScheduledTasks() {
        log.debug("检查定时采集任务...");
        
        // 查询已到期的定时任务
        List<CollectionTask> pendingTasks = collectionTaskService.findPendingScheduledTasks();
        
        for (CollectionTask task : pendingTasks) {
            try {
                // 启动任务
                collectionTaskService.startTask(task.getId());
                
                // 异步执行采集
                productCollectorService.collectProducts(task).thenAccept(result -> {
                    // 更新执行结果
                    collectionTaskService.completeTask(task.getId(),
                            result.getSuccessCount(),
                            result.getFailedCount(),
                            result.getFilteredCount());
                    
                    // 更新下次执行时间
                    collectionTaskService.updateNextRunTime(task.getId());
                });
                
                log.info("已启动定时采集任务: {}", task.getTaskName());
            } catch (Exception e) {
                log.error("执行定时任务失败: {}", task.getId(), e);
                collectionTaskService.failTask(task.getId(), e.getMessage());
            }
        }
        
        log.info("本次检查到 {} 个待执行定时任务", pendingTasks.size());
    }
    
    /**
     * 每小时清理过期日志
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupOldLogs() {
        log.info("开始清理过期采集日志...");
        // 清理30天前的日志
        // collectionLogService.cleanupOldLogs(30);
        log.info("采集日志清理完成");
    }
}
```

### 3.3 前端页面设计

#### 3.3.1 任务管理页面
- **路径**: /collection-tasks
- **布局**: ProLayout + ProTable

#### 3.3.2 页面结构
```
┌─────────────────────────────────────────────────────────────┐
│  任务管理                                                    │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐    │
│  │ [新建任务] [快速执行]                                 │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ #    任务名称    类型    状态    Cron表达式  下次执行  │    │
│  │ 1    每日采集   定时    已完成  0 0 2 * * ?  02:00   │    │
│  │ 2    手动采集   手动    等待中  -        -          │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 操作: [执行] [停止] [编辑] [删除] [启用/禁用]          │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

#### 3.3.3 组件说明
| 组件 | 说明 |
|------|------|
| ProTable | 任务列表展示，支持分页、筛选、排序 |
| Modal | 新建/编辑任务弹窗 |
| Button | 执行、停止、删除操作 |
| Tag | 任务状态标签（进行中/已完成/失败） |

## 4. 配置说明

### 4.1 配置文件 (application.yml)
```yaml
# 采集任务配置
collection:
  task:
    default-max: 100  # 默认最大采集数量
  log:
    retention-days: 30  # 日志保留天数

# Scheduler配置
spring:
  scheduler:
    enabled: true  # 启用定时任务
```

## 5. 风险评估

### 5.1 技术风险
| 风险 | 影响 | 可能性 | 缓解措施 |
|------|------|--------|----------|
| 定时任务漏触发 | 中 | 低 | 任务检查状态补偿机制 |
| 任务执行超时 | 中 | 中 | 设置执行超时限制 |
| 并发执行冲突 | 低 | 低 | 任务状态检查防止重复执行 |

## 6. 附录

### 6.1 相关文档
- [PRD-002: 任务管理系统需求](../requirements/商品采集/PRD-002-任务管理系统.md)
- [DESIGN-001: 系统架构设计](./DESIGN-001-系统架构设计.md)
- [DESIGN-002: 数据库设计](./DESIGN-002-数据库设计.md)

### 6.2 参考资料
- Spring Scheduler: https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling
- Ant Design Pro: https://pro.ant.design/

---

**文档结束**
