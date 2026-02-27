# DESIGN-002: 数据库设计

## 基本信息
- **文档编号**: DESIGN-002
- **版本**: v2.0
- **状态**: 已更新
- **作者**: mwangli
- **最后更新**: 2026-02-16

## 变更日志
| 版本 | 日期 | 作者 | 变更内容 |
|------|------|------|----------|
| v2.0 | 2026-02-16 | mwangli | 增加商品采集相关表结构（商品表、分类表、采集任务表、采集日志表） |
| v0.1 | 2026-02-16 | mwangli | 初始版本（订单相关表） |

## 1. 设计概述

### 1.1 设计目标
- 支持商品数据采集和持久化
- 支持商品分类管理
- 支持采集任务调度和监控
- 保证数据一致性和完整性
- 预留扩展空间

### 1.2 数据库选型
- **开发环境**: H2 (内存数据库，便于开发和测试)
- **生产环境**: MySQL 8.0 (关系型数据库，成熟稳定)

### 1.3 命名规范
- 表名: 小写下划线命名，如 `products`, `collection_tasks`
- 字段名: 小写下划线命名，如 `created_at`, `updated_at`
- 主键: `id` (自增)
- 外键: `表名_id`，如 `task_id`
- 时间字段: `created_at`, `updated_at`, `collected_at`

## 2. 数据模型

### 2.1 实体关系图 (ER图)

```
┌──────────────────┐         ┌──────────────────┐
│   categories     │         │     products     │
├──────────────────┤         ├──────────────────┤
│ PK id            │         │ PK id            │
│    name          │         │ FK category_id   │
│    parent_id     │◄────────┤    tsin          │
│    level         │         │    product_title │
│    url           │         │    brand         │
│    created_at    │         │    price         │
└──────────────────┘         │    collected_at  │
                             │    created_at    │
                             └──────────────────┘
                                       │
                                       │
┌──────────────────┐                   │
│ collection_tasks │                   │
├──────────────────┤                   │
│ PK id            │                   │
│    task_name     │                   │
│    status        │◄──────────────────┘
│    actual_count  │          FK task_id
│    started_at    │
│    completed_at  │
└──────────────────┘
         │
         │
         ▼
┌──────────────────┐
│ collection_logs  │
├──────────────────┤
│ PK id            │
│ FK task_id       │
│    tsin          │
│    status        │
│    created_at    │
└──────────────────┘
```

### 2.2 表结构设计

#### 2.2.1 商品表 (products)

**设计说明**: 存储采集的商品信息，支持按TSIN去重，记录采集时间

```sql
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tsin VARCHAR(50) NOT NULL COMMENT 'Takealot商品唯一标识',
    product_url VARCHAR(500) COMMENT '商品页面URL',
    main_category VARCHAR(100) COMMENT '主分类',
    lowest_category VARCHAR(100) COMMENT '最低分类',
    category_id BIGINT COMMENT '关联分类ID',
    product_title VARCHAR(500) NOT NULL COMMENT '商品标题',
    subtitle VARCHAR(500) COMMENT '副标题',
    description TEXT COMMENT '商品描述',
    whats_in_box TEXT COMMENT '包装清单',
    brand VARCHAR(100) COMMENT '品牌',
    warranty_type VARCHAR(50) COMMENT '保修类型',
    warranty_period INT COMMENT '保修期限（月）',
    image_urls JSON COMMENT '商品图片URL数组（JSON格式，最多5张）',
    rating DECIMAL(2,1) COMMENT '评分（1-5星）',
    review_count INT COMMENT '评论数量',
    price DECIMAL(10,2) COMMENT '价格',
    currency VARCHAR(10) DEFAULT 'ZAR' COMMENT '货币单位',
    video_url VARCHAR(500) COMMENT '视频链接',
    collected_at TIMESTAMP NOT NULL COMMENT '数据采集时间',
    task_id BIGINT COMMENT '关联采集任务ID',
    is_filtered BOOLEAN DEFAULT FALSE COMMENT '是否被品牌过滤',
    filter_reason VARCHAR(200) COMMENT '过滤原因',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_tsin (tsin),
    INDEX idx_category (main_category, lowest_category),
    INDEX idx_brand (brand),
    INDEX idx_collected_at (collected_at),
    INDEX idx_task_id (task_id),
    INDEX idx_price (price),
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';
```

#### 2.2.2 分类表 (categories)

**设计说明**: 存储商品分类信息，支持层级结构

```sql
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    parent_id BIGINT COMMENT '父分类ID（顶级分类为NULL）',
    level INT COMMENT '分类层级（1-3）',
    url VARCHAR(500) COMMENT '分类URL',
    source VARCHAR(50) DEFAULT 'takealot' COMMENT '数据来源',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态：active-启用，inactive-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_parent (parent_id),
    INDEX idx_level (level),
    INDEX idx_status (status),
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';
```

#### 2.2.3 采集任务表 (collection_tasks)

**设计说明**: 存储采集任务信息，支持手动和定时两种类型

```sql
CREATE TABLE collection_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(20) NOT NULL COMMENT '任务类型：MANUAL-手动，SCHEDULED-定时',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING-待执行，RUNNING-执行中，COMPLETED-完成，FAILED-失败，CANCELLED-已取消',
    category_filter JSON COMMENT '分类筛选条件（JSON数组）',
    brand_filter TEXT COMMENT '品牌过滤规则（JSON格式）',
    max_products INT DEFAULT 100 COMMENT '最大采集数量',
    actual_count INT DEFAULT 0 COMMENT '实际采集数量',
    success_count INT DEFAULT 0 COMMENT '成功数量',
    failed_count INT DEFAULT 0 COMMENT '失败数量',
    filtered_count INT DEFAULT 0 COMMENT '被过滤数量',
    started_at TIMESTAMP COMMENT '开始时间',
    completed_at TIMESTAMP COMMENT '完成时间',
    duration_seconds INT COMMENT '执行时长（秒）',
    error_message TEXT COMMENT '错误信息',
    cron_expression VARCHAR(100) COMMENT '定时表达式（仅定时任务）',
    next_run_time TIMESTAMP COMMENT '下次执行时间（仅定时任务）',
    is_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用（仅定时任务）',
    created_by VARCHAR(100) COMMENT '创建人',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_type (task_type),
    INDEX idx_created_at (created_at),
    INDEX idx_next_run (next_run_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集任务表';
```

#### 2.2.4 采集日志表 (collection_logs)

**设计说明**: 记录每个商品的采集详细日志，便于排查问题

```sql
CREATE TABLE collection_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '关联任务ID',
    tsin VARCHAR(50) COMMENT '商品TSIN',
    product_title VARCHAR(500) COMMENT '商品标题',
    product_url VARCHAR(500) COMMENT '商品URL',
    status VARCHAR(20) NOT NULL COMMENT '状态：SUCCESS-成功，FAILED-失败，SKIPPED-跳过，FILTERED-被过滤',
    message TEXT COMMENT '日志信息/错误详情',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    response_time_ms INT COMMENT '响应时间（毫秒）',
    proxy_used VARCHAR(200) COMMENT '使用的代理',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_status (status),
    INDEX idx_tsin (tsin),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (task_id) REFERENCES collection_tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集日志表';
```

#### 2.2.5 品牌黑名单表 (brand_blacklist)

**设计说明**: 维护品牌黑名单，支持动态配置

```sql
CREATE TABLE brand_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    brand_name VARCHAR(100) NOT NULL COMMENT '品牌名称（模糊匹配关键词）',
    brand_type VARCHAR(50) COMMENT '品牌类型：ELECTRONICS-电子产品，FASHION-时尚，OTHER-其他',
    reason VARCHAR(200) COMMENT '加入黑名单原因',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否生效',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_brand_name (brand_name),
    INDEX idx_type (brand_type),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌黑名单表';
```

#### 2.2.6 订单相关表（保留）

订单相关表结构保持不变，详见v0.1版本：
- `orders` - 订单表
- `order_items` - 订单明细表

### 2.3 初始数据

```sql
-- 初始化品牌黑名单数据
INSERT INTO brand_blacklist (brand_name, brand_type, reason) VALUES
('apple', 'ELECTRONICS', '知名品牌，避免侵权'),
('samsung', 'ELECTRONICS', '知名品牌，避免侵权'),
('huawei', 'ELECTRONICS', '知名品牌，避免侵权'),
('xiaomi', 'ELECTRONICS', '知名品牌，避免侵权'),
('dell', 'ELECTRONICS', '知名品牌，避免侵权'),
('hp', 'ELECTRONICS', '知名品牌，避免侵权'),
('canon', 'ELECTRONICS', '知名品牌，避免侵权'),
('nikon', 'ELECTRONICS', '知名品牌，避免侵权'),
('nike', 'FASHION', '知名品牌，避免侵权'),
('adidas', 'FASHION', '知名品牌，避免侵权'),
('gucci', 'FASHION', '知名品牌，避免侵权'),
('lego', 'OTHER', '知名品牌，避免侵权'),
('disney', 'OTHER', '知名品牌，避免侵权');

-- 初始化示例分类数据
INSERT INTO categories (name, parent_id, level, url, source) VALUES
('Electronics', NULL, 1, 'https://www.takealot.com/all?filter=CategoryId:', 'takealot'),
('Cellphones', 1, 2, 'https://www.takealot.com/all?filter=CategoryId:...', 'takealot'),
('Laptops', 1, 2, 'https://www.takealot.com/all?filter=CategoryId:...', 'takealot'),
('Cameras', 1, 2, 'https://www.takealot.com/all?filter=CategoryId:...', 'takealot');
```

## 3. 索引设计

### 3.1 索引列表

| 表名 | 索引名 | 字段 | 类型 | 说明 |
|------|--------|------|------|------|
| products | PRIMARY | id | 主键 | 唯一标识 |
| products | uk_tsin | tsin | 唯一 | 商品TSIN唯一 |
| products | idx_category | main_category, lowest_category | 普通 | 按分类查询 |
| products | idx_brand | brand | 普通 | 按品牌查询 |
| products | idx_collected_at | collected_at | 普通 | 按采集时间查询 |
| products | idx_task_id | task_id | 普通 | 按任务查询 |
| categories | PRIMARY | id | 主键 | 唯一标识 |
| categories | idx_parent | parent_id | 普通 | 按父分类查询 |
| categories | idx_level | level | 普通 | 按层级查询 |
| collection_tasks | PRIMARY | id | 主键 | 唯一标识 |
| collection_tasks | idx_status | status | 普通 | 按状态查询 |
| collection_tasks | idx_type | task_type | 普通 | 按类型查询 |
| collection_tasks | idx_next_run | next_run_time | 普通 | 按下次执行时间查询 |
| collection_logs | PRIMARY | id | 主键 | 唯一标识 |
| collection_logs | idx_task_id | task_id | 普通 | 按任务查询 |
| collection_logs | idx_status | status | 普通 | 按状态查询 |
| brand_blacklist | PRIMARY | id | 主键 | 唯一标识 |
| brand_blacklist | uk_brand_name | brand_name | 唯一 | 品牌名唯一 |

## 4. 数据字典

### 4.1 商品相关

#### products.status（预留字段）
| 值 | 含义 | 说明 |
|----|------|------|
| active | 可用 | 正常展示的商品 |
| inactive | 不可用 | 暂停展示的商品 |

#### products.is_filtered
| 值 | 含义 | 说明 |
|----|------|------|
| FALSE | 未过滤 | 正常商品 |
| TRUE | 已过滤 | 被品牌黑名单过滤 |

### 4.2 采集任务相关

#### collection_tasks.task_type
| 值 | 含义 | 说明 |
|----|------|------|
| MANUAL | 手动任务 | 用户手动触发 |
| SCHEDULED | 定时任务 | 系统自动触发 |

#### collection_tasks.status
| 值 | 含义 | 说明 |
|----|------|------|
| PENDING | 待执行 | 任务创建，等待执行 |
| RUNNING | 执行中 | 正在采集 |
| COMPLETED | 已完成 | 采集成功完成 |
| FAILED | 失败 | 采集过程中出错 |
| CANCELLED | 已取消 | 用户取消任务 |

### 4.3 采集日志相关

#### collection_logs.status
| 值 | 含义 | 说明 |
|----|------|------|
| SUCCESS | 成功 | 商品采集成功并入库 |
| FAILED | 失败 | 采集过程中出错 |
| SKIPPED | 跳过 | 重复数据或其他原因跳过 |
| FILTERED | 已过滤 | 被品牌黑名单过滤 |

## 5. 设计决策

### 5.1 为什么使用JSON类型字段
- **image_urls**: 存储多张图片URL，使用JSON数组便于扩展
- **category_filter**: 支持灵活的多分类筛选配置
- **brand_filter**: 支持复杂的品牌过滤规则配置

### 5.2 为什么分离采集日志表
- 便于追踪每个商品的采集详情
- 支持采集问题排查和统计
- 不影响商品表查询性能

### 5.3 采集时间字段设计
- **collected_at**: 记录实际采集时间，用于数据新鲜度判断
- **created_at**: 记录入库时间
- **updated_at**: 记录数据更新时间

## 6. 附录

### 6.1 相关文档
- [系统架构设计](./DESIGN-001-系统架构设计.md)
- [商品数据采集需求文档](../requirements/PRD-001-商品数据采集系统.md)

### 6.2 参考资料
- [MySQL 8.0 JSON类型文档](https://dev.mysql.com/doc/refman/8.0/en/json.html)
- [阿里巴巴Java开发手册-MySQL数据库](https://github.com/alibaba/p3c)

---

**文档结束**
