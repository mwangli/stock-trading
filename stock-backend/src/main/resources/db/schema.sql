-- ========================================================
-- 股票交易系统数据库初始化脚本
-- 版本: 1.0
-- 创建日期: 2026-02-19
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- ========================================================

-- ========================================================
-- 1. 数据采集模块表结构
-- ========================================================

-- 股票基本信息表
CREATE TABLE IF NOT EXISTS `stock_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) NOT NULL COMMENT '股票名称',
  `code` varchar(10) NOT NULL COMMENT '股票代码',
  `market` varchar(2) DEFAULT NULL COMMENT '所属市场(SH/SZ)',
  `industry` varchar(50) DEFAULT NULL COMMENT '所属行业',
  `listing_date` date DEFAULT NULL COMMENT '上市日期',
  `is_st` tinyint(1) DEFAULT '0' COMMENT '是否ST(0-否,1-是)',
  `is_tradable` tinyint(1) DEFAULT '1' COMMENT '是否可交易(0-否,1-是)',
  `increase` double DEFAULT NULL COMMENT '涨跌幅',
  `price` double DEFAULT NULL COMMENT '当前价格',
  `predict_price` double DEFAULT NULL COMMENT '预测价格',
  `score` double DEFAULT NULL COMMENT '综合评分',
  `permission` varchar(10) DEFAULT '1' COMMENT '交易权限(0-禁止,1-允许)',
  `buy_sale_count` int(11) DEFAULT '0' COMMENT '交易次数',
  `selected` varchar(1) DEFAULT '0' COMMENT '是否自选(0-否,1-是)',
  `deleted` varchar(1) DEFAULT '0' COMMENT '是否删除(0-否,1-是)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_market` (`market`),
  KEY `idx_industry` (`industry`),
  KEY `idx_selected` (`selected`),
  KEY `idx_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票基本信息表';

-- 定时任务配置表
CREATE TABLE IF NOT EXISTS `quartz_job` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '任务名称',
  `description` varchar(255) DEFAULT NULL COMMENT '任务描述',
  `class_name` varchar(255) NOT NULL COMMENT '执行类全名',
  `cron` varchar(50) NOT NULL COMMENT 'Cron表达式',
  `status` varchar(10) DEFAULT '1' COMMENT '状态(0-禁用,1-启用)',
  `running` varchar(1) DEFAULT '0' COMMENT '运行状态(0-停止,1-运行中)',
  `deleted` varchar(1) DEFAULT '0' COMMENT '是否删除(0-否,1-是)',
  `sort` int(11) DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务配置表';

-- ========================================================
-- 2. 交易执行模块表结构
-- ========================================================

-- 账户资金信息表
CREATE TABLE IF NOT EXISTS `account_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `available_amount` double DEFAULT '0' COMMENT '可用资金',
  `used_amount` double DEFAULT '0' COMMENT '已用资金',
  `total_amount` double DEFAULT '0' COMMENT '总资金',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户资金信息表';

-- 订单信息表
CREATE TABLE IF NOT EXISTS `order_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `answer_no` varchar(50) DEFAULT NULL COMMENT '券商委托编号',
  `code` varchar(10) NOT NULL COMMENT '股票代码',
  `name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `status` varchar(20) DEFAULT NULL COMMENT '订单状态(已报/已成交/已撤单等)',
  `date` varchar(10) DEFAULT NULL COMMENT '交易日期(YYYY-MM-DD)',
  `time` varchar(10) DEFAULT NULL COMMENT '交易时间(HH:mm:ss)',
  `type` varchar(10) DEFAULT NULL COMMENT '交易类型(买入/卖出)',
  `number` double DEFAULT NULL COMMENT '委托数量',
  `price` double DEFAULT NULL COMMENT '委托价格',
  `amount` double DEFAULT NULL COMMENT '委托金额',
  `peer` double DEFAULT NULL COMMENT '成交数量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_answer_no_code_date` (`answer_no`,`code`,`date`),
  KEY `idx_code` (`code`),
  KEY `idx_date` (`date`),
  KEY `idx_status` (`status`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单信息表';

-- 交易记录表
CREATE TABLE IF NOT EXISTS `trading_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(10) NOT NULL COMMENT '股票代码',
  `name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `buy_date` date DEFAULT NULL COMMENT '买入日期',
  `buy_date_string` varchar(20) DEFAULT NULL COMMENT '买入日期字符串',
  `buy_no` varchar(50) DEFAULT NULL COMMENT '买入委托编号',
  `buy_price` double DEFAULT NULL COMMENT '买入价格',
  `buy_number` double DEFAULT NULL COMMENT '买入数量',
  `buy_amount` double DEFAULT NULL COMMENT '买入金额',
  `sale_date` date DEFAULT NULL COMMENT '卖出日期',
  `sale_date_string` varchar(20) DEFAULT NULL COMMENT '卖出日期字符串',
  `sale_no` varchar(50) DEFAULT NULL COMMENT '卖出委托编号',
  `sale_price` double DEFAULT NULL COMMENT '卖出价格',
  `sale_number` double DEFAULT NULL COMMENT '卖出数量',
  `sale_amount` double DEFAULT NULL COMMENT '卖出金额',
  `income` double DEFAULT NULL COMMENT '收益金额',
  `income_rate` double DEFAULT NULL COMMENT '收益率(%)',
  `hold_days` int(11) DEFAULT NULL COMMENT '持有天数',
  `daily_income_rate` double DEFAULT NULL COMMENT '日均收益率(%)',
  `sold` varchar(1) DEFAULT '0' COMMENT '是否已卖出(0-否,1-是)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_code` (`code`),
  KEY `idx_buy_date` (`buy_date`),
  KEY `idx_sale_date` (`sale_date`),
  KEY `idx_sold` (`sold`),
  KEY `idx_buy_no` (`buy_no`),
  KEY `idx_sale_no` (`sale_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易记录表';

-- 持仓记录表
CREATE TABLE IF NOT EXISTS `position` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_code` varchar(10) NOT NULL COMMENT '股票代码',
  `stock_name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `quantity` int(11) DEFAULT '0' COMMENT '持仓数量',
  `avg_cost` double DEFAULT '0' COMMENT '平均成本价',
  `current_price` double DEFAULT '0' COMMENT '当前价格',
  `market_value` double DEFAULT '0' COMMENT '市值',
  `unrealized_pnl` double DEFAULT '0' COMMENT '未实现盈亏',
  `unrealized_pnl_ratio` double DEFAULT '0' COMMENT '未实现盈亏比例(%)',
  `status` varchar(20) DEFAULT 'HOLDING' COMMENT '持仓状态(HOLDING-持有,CLOSED-已平仓)',
  `open_date` date DEFAULT NULL COMMENT '开仓日期',
  `close_date` date DEFAULT NULL COMMENT '平仓日期',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_code` (`stock_code`),
  KEY `idx_status` (`status`),
  KEY `idx_open_date` (`open_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='持仓记录表';

-- ========================================================
-- 3. LSTM预测模块表结构
-- ========================================================

-- LSTM模型信息表
CREATE TABLE IF NOT EXISTS `model_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(10) NOT NULL COMMENT '股票代码',
  `name` varchar(50) DEFAULT NULL COMMENT '股票名称',
  `train_period` varchar(50) DEFAULT NULL COMMENT '训练周期(如: 2020-01-01~2024-01-01)',
  `train_times` int(11) DEFAULT '0' COMMENT '训练次数',
  `test_deviation` double DEFAULT NULL COMMENT '测试偏差率(%)',
  `score` double DEFAULT NULL COMMENT '模型评分',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '模型状态(ACTIVE-活跃,INACTIVE-停用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`),
  KEY `idx_score` (`score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSTM模型信息表';

-- ========================================================
-- 4. 初始化数据
-- ========================================================

-- 初始化账户资金(默认0)
INSERT INTO `account_info` (`id`, `available_amount`, `used_amount`, `total_amount`) 
VALUES (1, 0, 0, 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 初始化定时任务配置示例
INSERT INTO `quartz_job` (`name`, `description`, `class_name`, `cron`, `status`, `sort`) VALUES
('股票列表同步', '每日盘前同步股票列表', 'online.mwang.stockTrading.modules.datacollection.jobs.StockSyncJob', '0 0 9 * * ?', '1', 1),
('实时行情更新', '交易时间内每分钟更新行情', 'online.mwang.stockTrading.modules.datacollection.jobs.QuoteUpdateJob', '0 0/1 9-14 * * ?', '1', 2),
('数据全量同步', '每日盘后全量同步历史数据', 'online.mwang.stockTrading.modules.datacollection.jobs.FullSyncJob', '0 30 14 * * ?', '1', 3)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- ========================================================
-- MongoDB集合创建脚本(需要在MongoDB中执行)
-- ========================================================

/*
-- 创建股票历史价格集合
use stock_trading;

db.createCollection("stock_prices");

-- 创建复合唯一索引(股票代码+日期)
db.stock_prices.createIndex({ "code": 1, "date": 1 }, { unique: true });

-- 创建查询索引
db.stock_prices.createIndex({ "code": 1, "date": -1 });
db.stock_prices.createIndex({ "code": 1 });
db.stock_prices.createIndex({ "date": -1 });

-- 示例文档结构
{
  "code": "600519",
  "name": "贵州茅台",
  "date": "2024-01-15",
  "price1": 1800.00,             // 开盘价
  "price2": 1850.00,             // 最高价
  "price3": 1790.00,             // 最低价
  "price4": 1820.00,             // 收盘价
  "increaseRate": 1.11,          // 涨跌幅(%)
  "tradingVolume": 1000000,      // 成交量
  "tradingAmount": 1820000000,   // 成交额
  "exchangeRate": 0.50           // 换手率(%)
}
*/

-- ========================================================
-- 常用查询SQL示例
-- ========================================================

-- 查询股票列表(带评分)
-- SELECT * FROM stock_info WHERE deleted = '0' ORDER BY score DESC;

-- 查询当日订单
-- SELECT * FROM order_info WHERE date = CURDATE() ORDER BY create_time DESC;

-- 查询持仓列表
-- SELECT * FROM position WHERE status = 'HOLDING';

-- 查询交易统计
-- SELECT 
--   SUM(CASE WHEN income > 0 THEN 1 ELSE 0 END) as win_count,
--   SUM(CASE WHEN income <= 0 THEN 1 ELSE 0 END) as loss_count,
--   SUM(income) as total_income
-- FROM trading_record WHERE sold = '1';

-- 查询账户盈亏
-- SELECT 
--   available_amount + used_amount as total,
--   available_amount,
--   used_amount,
--   ROUND((used_amount / (available_amount + used_amount)) * 100, 2) as position_ratio
-- FROM account_info;
