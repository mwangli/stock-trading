-- 模块8: 交易执行 - MySQL表结构
-- 创建时间: 2026-02-22
-- V2.0架构: Python写入, Java读取

-- 1. 委托订单表 (主表)
CREATE TABLE IF NOT EXISTS trade_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL UNIQUE COMMENT '券商订单号',
    stock_code VARCHAR(20) NOT NULL COMMENT '股票代码',
    stock_name VARCHAR(50) COMMENT '股票名称',
    order_type VARCHAR(10) NOT NULL COMMENT '订单类型 BUY/SELL',
    order_side VARCHAR(10) NOT NULL COMMENT '订单方向 OPEN/CLOSE',
    quantity INT NOT NULL COMMENT '委托数量',
    price DECIMAL(10,2) COMMENT '委托价格',
    filled_quantity INT DEFAULT 0 COMMENT '成交数量',
    filled_price DECIMAL(10,2) COMMENT '成交均价',
    amount DECIMAL(15,2) COMMENT '成交金额',
    commission DECIMAL(10,2) DEFAULT 0 COMMENT '手续费',
    status VARCHAR(20) NOT NULL COMMENT '状态 CREATE/SUBMIT/PENDING/FILLED/PARTIAL/CANCELLED/FAILED',
    order_date DATE NOT NULL COMMENT '委托日期',
    order_time TIME COMMENT '委托时间',
    cancel_time TIME COMMENT '撤单时间',
    error_message VARCHAR(500) COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_stock_code (stock_code),
    INDEX idx_order_date (order_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委托订单表';

-- 2. 持仓表
CREATE TABLE IF NOT EXISTS positions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL COMMENT '股票代码',
    stock_name VARCHAR(50) COMMENT '股票名称',
    quantity INT DEFAULT 0 COMMENT '持仓数量',
    available_quantity INT DEFAULT 0 COMMENT '可用数量',
    frozen_quantity INT DEFAULT 0 COMMENT '冻结数量',
    avg_cost DECIMAL(10,4) COMMENT '持仓成本价',
    current_price DECIMAL(10,4) COMMENT '当前价格',
    market_value DECIMAL(15,2) COMMENT '市值',
    unrealized_pnl DECIMAL(15,2) DEFAULT 0 COMMENT '未实现盈亏',
    unrealized_pnl_ratio DECIMAL(10,4) DEFAULT 0 COMMENT '盈亏比例',
    open_date DATE COMMENT '建仓日期',
    position_side VARCHAR(10) COMMENT '持仓方向 LONG/SHORT',
    status VARCHAR(20) DEFAULT 'HOLDING' COMMENT '状态 HOLDING/SOLD/STOP_LOSS',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stock_code (stock_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='持仓表';

-- 3. 账户资金表
CREATE TABLE IF NOT EXISTS account_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    total_asset DECIMAL(15,2) DEFAULT 0 COMMENT '总资产',
    available_cash DECIMAL(15,2) DEFAULT 0 COMMENT '可用资金',
    market_value DECIMAL(15,2) DEFAULT 0 COMMENT '持仓市值',
    frozen_amount DECIMAL(15,2) DEFAULT 0 COMMENT '冻结金额',
    daily_pnl DECIMAL(15,2) DEFAULT 0 COMMENT '当日盈亏',
    daily_pnl_ratio DECIMAL(10,4) DEFAULT 0 COMMENT '当日盈亏比例',
    monthly_pnl DECIMAL(15,2) DEFAULT 0 COMMENT '当月盈亏',
    monthly_pnl_ratio DECIMAL(10,4) DEFAULT 0 COMMENT '当月盈亏比例',
    total_pnl DECIMAL(15,2) DEFAULT 0 COMMENT '累计盈亏',
    total_pnl_ratio DECIMAL(10,4) DEFAULT 0 COMMENT '累计盈亏比例',
    position_count INT DEFAULT 0 COMMENT '持仓数量',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户资金表';

-- 4. 资金流水表
CREATE TABLE IF NOT EXISTS capital_flows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flow_type VARCHAR(20) NOT NULL COMMENT '流水类型 DEPOSIT/WITHDRAW/BUY/SELL/FEE/DIVIDEND',
    amount DECIMAL(15,2) NOT NULL COMMENT '金额',
    balance DECIMAL(15,2) COMMENT '余额',
    stock_code VARCHAR(20) COMMENT '股票代码',
    stock_name VARCHAR(50) COMMENT '股票名称',
    quantity INT COMMENT '数量',
    price DECIMAL(10,2) COMMENT '价格',
    order_id VARCHAR(100) COMMENT '订单号',
    remark VARCHAR(200) COMMENT '备注',
    flow_date DATE NOT NULL COMMENT '流水日期',
    flow_time TIME COMMENT '流水时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_flow_type (flow_type),
    INDEX idx_flow_date (flow_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水表';

-- 5. 交易执行日志表
CREATE TABLE IF NOT EXISTS trade_execution_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    module_id VARCHAR(20) COMMENT '模块ID',
    action VARCHAR(50) NOT NULL COMMENT '操作类型',
    stock_code VARCHAR(20) COMMENT '股票代码',
    quantity INT COMMENT '数量',
    price DECIMAL(10,2) COMMENT '价格',
    status VARCHAR(20) NOT NULL COMMENT '状态 SUCCESS/FAILED',
    error_message VARCHAR(500) COMMENT '错误信息',
    records_processed INT DEFAULT 0 COMMENT '处理记录数',
    duration INT DEFAULT 0 COMMENT '执行耗时(毫秒)',
    execute_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_name (task_name),
    INDEX idx_execute_time (execute_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易执行日志表';

-- 6. 止损触发记录表
CREATE TABLE IF NOT EXISTS stop_loss_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL COMMENT '股票代码',
    stock_name VARCHAR(50) COMMENT '股票名称',
    quantity INT NOT NULL COMMENT '持仓数量',
    cost_price DECIMAL(10,4) COMMENT '成本价',
    current_price DECIMAL(10,4) COMMENT '当前价格',
    loss_percent DECIMAL(10,4) COMMENT '亏损比例',
    trigger_price DECIMAL(10,4) COMMENT '触发价格',
    stop_loss_price DECIMAL(10,4) COMMENT '止损价格',
    status VARCHAR(20) DEFAULT 'TRIGGERED' COMMENT '状态 TRIGGERED/EXECUTED/FAILED',
    order_id VARCHAR(100) COMMENT '订单号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_stock_code (stock_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='止损触发记录表';
