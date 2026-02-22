-- 模块4: 模型迭代 - MySQL表结构
-- 创建时间: 2026-02-22

-- 1. 表现记录表
CREATE TABLE IF NOT EXISTS performance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trade_date DATETIME NOT NULL COMMENT '交易日期',
    daily_return FLOAT COMMENT '日收益率',
    cumulative_return FLOAT COMMENT '累计收益率',
    win_count INT DEFAULT 0 COMMENT '盈利次数',
    loss_count INT DEFAULT 0 COMMENT '亏损次数',
    total_trades INT DEFAULT 0 COMMENT '总交易次数',
    max_drawdown FLOAT COMMENT '最大回撤',
    model_version VARCHAR(50) COMMENT '模型版本',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型表现记录';

-- 2. 模型版本表
CREATE TABLE IF NOT EXISTS model_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_type VARCHAR(50) NOT NULL COMMENT '模型类型 LSTM/FinBERT',
    version VARCHAR(50) NOT NULL COMMENT '版本号',
    file_path VARCHAR(255) NOT NULL COMMENT '模型文件路径',
    train_date DATETIME NOT NULL COMMENT '训练日期',
    accuracy FLOAT COMMENT '准确率',
    is_active TINYINT DEFAULT 0 COMMENT '是否当前活跃',
    train_params TEXT COMMENT '训练参数(JSON)',
    performance_stats JSON COMMENT '表现统计',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_model_type (model_type),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型版本管理';

-- 3. 训练任务表
CREATE TABLE IF NOT EXISTS training_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(100) NOT NULL UNIQUE COMMENT '任务ID',
    model_type VARCHAR(50) NOT NULL COMMENT '模型类型',
    status VARCHAR(20) NOT NULL COMMENT '任务状态 PENDING/RUNNING/COMPLETED/FAILED',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    error_message VARCHAR(2000) COMMENT '错误信息',
    new_version VARCHAR(50) COMMENT '新版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练任务记录';

-- 4. 模型评估结果表
CREATE TABLE IF NOT EXISTS model_evaluation_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_type VARCHAR(50) NOT NULL COMMENT '模型类型',
    eval_date DATETIME NOT NULL COMMENT '评估日期',
    period_days INT DEFAULT 30 COMMENT '评估周期天数',
    total_return FLOAT COMMENT '总收益率',
    win_rate FLOAT COMMENT '胜率',
    max_drawdown FLOAT COMMENT '最大回撤',
    consecutive_loss_days INT DEFAULT 0 COMMENT '连续亏损天数',
    score INT DEFAULT 0 COMMENT '综合得分',
    need_retrain TINYINT DEFAULT 0 COMMENT '是否需要重训练',
    reason VARCHAR(255) COMMENT '触发原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_model_type (model_type),
    INDEX idx_eval_date (eval_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型评估结果';
