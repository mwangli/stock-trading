# DESIGN-001: 系统架构设计

## 基本信息
- **文档编号**: DESIGN-001
- **版本**: v2.0
- **状态**: 已更新
- **作者**: mwangli
- **最后更新**: 2026-02-16

## 变更日志
| 版本 | 日期 | 作者 | 变更内容 |
|------|------|------|----------|
| v2.0 | 2026-02-16 | mwangli | 更新为商品数据采集系统架构 |
| v0.1 | 2026-02-16 | mwangli | 初始版本 |

## 1. 设计概述

### 1.1 设计目标
构建一个商品数据采集和管理系统，支持：
- 自动化采集第三方电商平台商品数据
- 数据持久化存储和管理
- REST API接口提供数据服务
- 前端可视化展示商品信息
- 定时自动采集任务调度

### 1.2 设计原则
- **高内聚低耦合**: 模块间职责清晰，采集、存储、展示分离
- **可扩展性**: 支持多平台采集扩展，易于添加新的数据源
- **可维护性**: 代码结构清晰，文档完善
- **可靠性**: 支持异常处理和任务重试机制

### 1.3 约束条件
- 开发周期短，需要快速迭代
- 遵守目标网站的robots.txt和使用条款
- 预算有限，优先使用开源方案

## 2. 方案设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端层 (Frontend)                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  React 18 + Ant Design Pro + Umi 4                      │   │
│  │  - 商品列表页面                                          │   │
│  │  - 商品详情页面                                          │   │
│  │  - 采集任务管理页面                                       │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP/REST API
                              │
┌─────────────────────────────────────────────────────────────────┐
│                        后端层 (Backend)                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Spring Boot 3.0 + JDK 17                               │   │
│  │  ├─ Controller 层 (API接口)                              │   │
│  │  │   └─ ProductController, CollectionTaskController     │   │
│  │  ├─ Service 层 (业务逻辑)                                │   │
│  │  │   └─ ProductService, CollectionTaskService           │   │
│  │  ├─ Repository 层 (数据访问)                             │   │
│  │  │   └─ ProductRepository, CollectionTaskRepository     │   │
│  │  ├─ Entity 层 (数据模型)                                 │   │
│  │  │   └─ Product, Category, CollectionTask               │   │
│  │  ├─ Scheduler 层 (定时任务)                              │   │
│  │  │   └─ CollectionScheduler                             │   │
│  │  └─ Config 层 (配置类)                                   │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ JDBC
                              │
┌─────────────────────────────────────────────────────────────────┐
│                        数据层 (Database)                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  H2 (开发环境) / MySQL 8.0 (生产环境)                     │   │
│  │  ├─ products 表 (商品数据)                               │   │
│  │  ├─ categories 表 (分类数据)                             │   │
│  │  ├─ collection_tasks 表 (采集任务)                       │   │
│  │  └─ collection_logs 表 (采集日志)                        │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              ▲
                              │ (数据写入)
┌─────────────────────────────────────────────────────────────────┐
│                      数据采集层 (Collector)                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Python + Selenium + BeautifulSoup                      │   │
│  │  ├─ takealot_collector.py (Takealot采集器)              │   │
│  │  ├─ brand_filter.py (品牌过滤器)                         │   │
│  │  └─ proxy_manager.py (代理管理器)                        │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 技术选型

#### 前端技术栈
| 技术 | 版本 | 用途 |
|------|------|------|
| React | 18.x | UI框架 |
| Ant Design Pro | 5.x | UI组件库 |
| Umi | 4.x | 前端框架 |
| TypeScript | 5.x | 类型安全 |

#### 后端技术栈
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.0.x | 应用框架 |
| JDK | 17 | Java运行时 |
| MyBatis-Plus | 3.5.5 | ORM框架（数据访问） |
| Spring Scheduler | 3.0.x | 定时任务 |
| MySQL/H2 | - | 数据库 |

#### 采集技术栈
| 技术 | 版本 | 用途 |
|------|------|------|
| Python | 3.x | 采集脚本语言 |
| Selenium | 4.x | 浏览器自动化 |
| BeautifulSoup | 4.x | HTML解析 |
| Requests | 2.x | HTTP请求 |

### 2.3 模块划分

```
ai-shopping/
├── backend/                     # 后端模块 (Spring Boot)
│   ├── controller/              # API控制器
│   │   ├── ProductController         # 商品API
│   │   └── CollectionTaskController  # 采集任务API
│   ├── service/                 # 业务服务 (extends ServiceImpl)
│   │   ├── ProductService
│   │   ├── CollectionTaskService
│   │   └── ProductCollectorService   # 采集服务
│   ├── mapper/                  # MyBatis-Plus数据访问 (extends BaseMapper)
│   │   ├── ProductMapper
│   │   ├── CategoryMapper
│   │   └── CollectionTaskMapper
│   ├── entity/                  # MyBatis-Plus实体类 (@TableName)
│   │   ├── Product
│   │   ├── Category
│   │   ├── CollectionTask
│   │   └── CollectionLog
│   ├── scheduler/               # 定时任务
│   │   └── CollectionScheduler
│   ├── dto/                     # 数据传输对象
│   └── config/                  # 配置类
│       └── MyBatisPlusConfig    # MyBatis-Plus配置
│
├── collector/                   # 数据采集模块 (Python)
│   ├── takealot_collector.py    # Takealot采集器
│   ├── brand_filter.py          # 品牌过滤器
│   ├── proxy_manager.py         # 代理管理器
│   └── utils/                   # 工具类
│
├── frontend/                    # 前端模块 (React)
│   └── src/
│       ├── pages/               # 页面组件
│       │   ├── ProductList      # 商品列表
│       │   ├── ProductDetail    # 商品详情
│       │   └── CollectionTasks  # 采集任务管理
│       ├── services/            # API服务
│       └── components/          # 公共组件
│
└── doc/                         # 项目文档
    ├── requirements/
    └── design/
```

## 3. 详细设计

### 3.1 数据模型

#### 商品实体 (Product)
```java
@Data
@TableName("products")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("tsin")
    private String tsin;                  // Takealot唯一标识
    
    @TableField("product_title")
    private String productTitle;          // 商品标题
    
    @TableField("main_category")
    private String mainCategory;          // 主分类
    
    @TableField("brand")
    private String brand;                 // 品牌
    
    @TableField("price")
    private BigDecimal price;             // 价格
    
    @TableField("image_urls")
    private String imageUrls;             // 图片URL数组 (JSON)
    
    @TableField("collected_at")
    private LocalDateTime collectedAt;    // 采集时间
    
    @TableField("task_id")
    private Long taskId;                  // 关联采集任务ID
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

#### 采集任务实体 (CollectionTask)
```java
@Data
@TableName("collection_tasks")
public class CollectionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("task_name")
    private String taskName;              // 任务名称
    
    @TableField("task_type")
    private String taskType;              // 任务类型：MANUAL/SCHEDULED
    
    @TableField("status")
    private String status;                // 状态：PENDING/RUNNING/COMPLETED/FAILED
    
    @TableField("cron_expression")
    private String cronExpression;        // 定时表达式
    
    @TableField("actual_count")
    private Integer actualCount;          // 实际采集数量
    
    @TableField("success_count")
    private Integer successCount;         // 成功数量
}
```

#### MyBatis-Plus Mapper示例
```java
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    // 自定义查询方法
    List<Product> selectByCategory(@Param("category") String category);
}
```

#### MyBatis-Plus Service示例
```java
public interface ProductService extends IService<Product> {
    // 自定义业务方法
    Page<Product> getProductList(Page<Product> page, QueryWrapper<Product> wrapper);
}

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> 
        implements ProductService {
    @Override
    public Page<Product> getProductList(Page<Product> page, QueryWrapper<Product> wrapper) {
        return baseMapper.selectPage(page, wrapper);
    }
}
```

### 3.2 API 接口设计

#### 商品接口
```
GET    /api/products               # 获取商品列表（支持分页、筛选）
GET    /api/products/{id}          # 获取商品详情
GET    /api/products/search        # 搜索商品
```

**请求参数示例**:
```
GET /api/products?page=1&size=20&category=Electronics&brand=Unknown
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "tsin": "12345678",
        "productTitle": "Smartphone XYZ",
        "mainCategory": "Electronics",
        "brand": "Unknown Brand",
        "price": 2999.00,
        "collectedAt": "2024-02-16T10:30:00"
      }
    ],
    "totalElements": 100,
    "totalPages": 5
  }
}
```

#### 采集任务接口
```
GET    /api/collection-tasks       # 获取任务列表
POST   /api/collection-tasks       # 创建采集任务
GET    /api/collection-tasks/{id}  # 获取任务详情
POST   /api/collection-tasks/{id}/execute  # 手动执行任务
```

### 3.3 数据流

#### 采集流程
```
定时触发/手动触发
      │
      ▼
┌─────────────┐
│  Scheduler  │  (Spring Scheduler)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Python    │  (Selenium采集器)
│  Collector  │
└──────┬──────┘
       │ HTTP/API
       ▼
┌─────────────┐
│   Spring    │  (数据写入)
│   Boot API  │
└──────┬──────┘
       │ JDBC
       ▼
┌─────────────┐
│   MySQL     │  (持久化存储)
└─────────────┘
```

#### 查询流程
```
用户请求
    │
    ▼
┌─────────┐
│  React  │  (前端页面)
└────┬────┘
     │ HTTP
     ▼
┌─────────┐
│Controller│ (API接口)
└────┬────┘
     │
     ▼
┌─────────┐
│ Service │  (业务逻辑)
└────┬────┘
     │
     ▼
┌─────────┐
│Repository│ (数据查询)
└────┬────┘
     │ SQL
     ▼
┌─────────┐
│  MySQL  │  (返回数据)
└─────────┘
```

### 3.4 定时任务设计

```java
@Component
public class CollectionScheduler {
    
    @Autowired
    private CollectionTaskService taskService;
    
    // 每分钟检查是否有待执行的定时任务
    @Scheduled(cron = "0 * * * * ?")
    public void checkScheduledTasks() {
        List<CollectionTask> pendingTasks = taskService.findPendingScheduledTasks();
        for (CollectionTask task : pendingTasks) {
            executeTask(task);
        }
    }
    
    // 每小时清理过期日志
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupOldLogs() {
        // 清理7天前的采集日志
    }
}
```

## 4. 风险评估

### 4.1 技术风险
| 风险 | 影响 | 可能性 | 缓解措施 |
|------|------|--------|----------|
| 目标网站反爬升级 | 高 | 中 | 使用代理池、控制请求频率、实现重试机制 |
| 页面结构变更 | 中 | 高 | 定期维护更新选择器、配置化解析规则 |
| 数据库性能瓶颈 | 中 | 低 | 合理索引设计、分页查询、读写分离 |
| 定时任务执行失败 | 中 | 中 | 异常告警、自动重试、任务状态监控 |

### 4.2 业务风险
| 风险 | 影响 | 可能性 | 缓解措施 |
|------|------|--------|----------|
| 品牌过滤不准确 | 中 | 低 | 建立白名单复查机制、人工抽检 |
| 法律合规风险 | 高 | 低 | 仅采集公开数据、遵守robots.txt |
| 数据质量问题 | 中 | 中 | 数据校验规则、完整性检查 |

## 5. 附录

### 5.1 参考资料
- [Spring Boot 3.0 官方文档](https://spring.io/projects/spring-boot)
- [Spring Scheduler 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling)
- [Ant Design Pro 官方文档](https://pro.ant.design/)
- [Selenium 文档](https://selenium-python.readthedocs.io/)

### 5.2 相关文档
- [商品数据采集需求文档](../requirements/PRD-001-商品数据采集系统.md)
- [数据库设计](./DESIGN-002-数据库设计.md)

---

**文档结束**
