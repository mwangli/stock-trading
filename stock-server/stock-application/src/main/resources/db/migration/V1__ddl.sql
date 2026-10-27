-- 股票交易系统 MySQL 最终结构基线
-- 执行顺序：调度与平台 -> 策略 -> 交易执行 -> 模型。
-- 本文件只包含 DDL；初始化数据统一维护在 V2__dml.sql。

-- 固定任务调度配置
CREATE TABLE IF NOT EXISTS `job_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务配置数据库主键',
  `create_time` datetime(6) DEFAULT NULL COMMENT '任务配置创建时间',
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Quartz Cron 表达式',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务业务用途说明',
  `job_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固定任务唯一名称，与 JobSchedulerService 任务目录一致',
  `status` int NOT NULL COMMENT '任务启停状态，1 表示启用，0 表示暂停',
  `update_time` datetime(6) DEFAULT NULL COMMENT '任务配置最后更新时间',
  `module` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务所属业务模块',
  `sync_mode` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务同步模式，如 FULL、INCREMENTAL 或 BACKFILL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_5mnhc0tocoiov6bbev5s7wk1b` (`job_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='固定任务调度配置';

-- 任务执行审计记录
CREATE TABLE IF NOT EXISTS `job_execution_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务执行记录数据库主键',
  `created_at` datetime(6) NOT NULL COMMENT '记录创建时间。',
  `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时，单位为毫秒。',
  `error_message` longtext COLLATE utf8mb4_unicode_ci COMMENT '任务执行失败详细原因',
  `error_type` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '脱敏后的异常类型。',
  `finished_time` datetime(6) DEFAULT NULL COMMENT '实际结束时间。',
  `host_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行节点名称。',
  `job_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固定任务名称。',
  `result_summary` longtext COLLATE utf8mb4_unicode_ci COMMENT '任务执行结果摘要',
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对外查询使用的唯一运行标识。',
  `scheduled_time` datetime(6) DEFAULT NULL COMMENT 'Quartz 计划触发时间。',
  `started_time` datetime(6) DEFAULT NULL COMMENT '实际开始时间。',
  `status` enum('CANCELLED','FAILED','QUEUED','REJECTED','RUNNING','SKIPPED','SUCCESS') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务运行状态。',
  `trigger_source` enum('MANUAL','RECOVERY','SCHEDULED') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务触发来源。',
  `triggered_by` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发操作人或系统标识。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_job_execution_run_id` (`run_id`),
  KEY `idx_job_execution_job_time` (`job_name`,`created_at`),
  KEY `idx_job_execution_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务执行审计记录';

-- 系统登录用户
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统用户数据库主键',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名。',
  `password_hash` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BCrypt 密码摘要。',
  `display_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户显示名称。',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否允许登录。',
  `created_at` datetime NOT NULL COMMENT '创建时间。',
  `updated_at` datetime NOT NULL COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统登录用户';

-- 系统用户界面偏好
CREATE TABLE IF NOT EXISTS `sys_user_preference` (
  `user_id` bigint NOT NULL COMMENT '关联的系统用户主键',
  `menu_order` longtext COLLATE utf8mb4_unicode_ci COMMENT '左侧菜单顺序，使用 JSON 数组文本保存。',
  `updated_at` datetime NOT NULL COMMENT '偏好最后更新时间。',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_sys_user_preference_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户界面偏好';

-- 运行节点动态控制配置
CREATE TABLE IF NOT EXISTS `system_runtime_config` (
  `node_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '运行节点唯一标识',
  `startup_data_sync_enabled` tinyint(1) NOT NULL COMMENT '是否在应用启动后执行股票基础数据初始化。',
  `automatic_trading_paused` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已通过紧急停止阻止后续自动交易。',
  `emergency_stop_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次紧急停止原因。',
  `emergency_stopped_at` datetime(6) DEFAULT NULL COMMENT '最近一次紧急停止时间。',
  `emergency_stop_operator` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次紧急停止操作者。',
  `emergency_stop_strategy_version` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '紧急停止时生效的策略版本。',
  `emergency_stop_result` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次紧急停止执行结果。',
  `updated_at` datetime NOT NULL COMMENT '配置最后更新时间。',
  PRIMARY KEY (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行节点动态控制配置';

-- 数据同步水位与缺口状态
CREATE TABLE IF NOT EXISTS `sync_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据同步状态数据库主键',
  `dataset` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集名称。',
  `scope_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务范围，例如股票代码或账户标识。',
  `last_success_cursor` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近确认成功的供应商游标。',
  `last_success_date` date DEFAULT NULL COMMENT '最近确认成功的业务日期。',
  `gap_start_date` date DEFAULT NULL COMMENT '缺口开始日期。',
  `gap_end_date` date DEFAULT NULL COMMENT '缺口结束日期。',
  `gap_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RESOLVED' COMMENT '缺口状态，OPEN 或 RESOLVED。',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '缺口重试次数。',
  `last_error` longtext COLLATE utf8mb4_unicode_ci COMMENT '最近一次失败摘要。',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间。',
  `updated_at` datetime(6) NOT NULL COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sync_state_scope` (`dataset`,`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据同步水位与缺口状态';

-- 股票选择策略版本配置
CREATE TABLE IF NOT EXISTS `strategy_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '策略配置数据库主键',
  `bollinger_breakout_threshold` double NOT NULL COMMENT '布林带突破阈值',
  `bollinger_weight` double NOT NULL COMMENT '布林带指标权重',
  `config_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '策略配置业务标识',
  `create_time` datetime(6) DEFAULT NULL COMMENT '策略配置创建时间',
  `enabled` bit(1) NOT NULL COMMENT '策略配置是否启用',
  `high_return_threshold` int NOT NULL COMMENT '高收益分档阈值，单位为百分比',
  `indicator_enabled` longtext COLLATE utf8mb4_unicode_ci COMMENT '指标启用状态 JSON',
  `loss_return_threshold` int NOT NULL COMMENT '亏损分档阈值，单位为百分比',
  `low_return_threshold` int NOT NULL COMMENT '低收益分档阈值，单位为百分比',
  `lstm_weight` double NOT NULL COMMENT 'LSTM 模型得分权重',
  `min_score` double NOT NULL COMMENT '候选股票最低综合得分',
  `mode` enum('AGGRESSIVE','BALANCED','CONSERVATIVE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '策略运行模式',
  `normal_return_threshold` int NOT NULL COMMENT '正常收益分档阈值，单位为百分比',
  `rsi_overbought_threshold` double NOT NULL COMMENT 'RSI 超买阈值',
  `rsi_weight` double NOT NULL COMMENT 'RSI 指标权重',
  `sentiment_weight` double NOT NULL COMMENT '新闻情感得分权重',
  `top_n` int NOT NULL COMMENT '最终保留候选股票数量',
  `trailing_stop_tolerance` double NOT NULL COMMENT '移动止损容忍阈值',
  `trailing_stop_weight` double NOT NULL COMMENT '移动止损指标权重',
  `update_time` datetime(6) DEFAULT NULL COMMENT '策略配置最后更新时间',
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '策略配置版本号',
  `volume_shrink_threshold` double NOT NULL COMMENT '成交量缩量阈值',
  `volume_weight` double NOT NULL COMMENT '成交量指标权重',
  `minimum_listing_years` double NOT NULL COMMENT '候选股票最小上市年限，单位为年；0 表示不限制',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='股票选择策略版本配置';

-- 策略运行状态
CREATE TABLE IF NOT EXISTS `strategy_runtime_state` (
  `state_key` varchar(64) NOT NULL COMMENT '策略运行状态唯一键',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '策略总开关。',
  `current_mode` varchar(32) NOT NULL DEFAULT 'BALANCED' COMMENT '当前策略模式。',
  `disabled_indicators_json` longtext COMMENT '被禁用指标列表的 JSON。',
  `last_switch_time` datetime(3) DEFAULT NULL COMMENT '最近一次切换时间。',
  `last_switch_reason` varchar(500) DEFAULT NULL COMMENT '最近一次切换原因。',
  `config_version` varchar(64) NOT NULL DEFAULT '1' COMMENT '当前策略配置版本。',
  `auto_switch_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否允许规则自动切换。',
  `last_auto_switch_time` datetime(3) DEFAULT NULL COMMENT '最近一次自动切换时间。',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近更新时间。',
  PRIMARY KEY (`state_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略运行状态';

-- 券商委托事实
CREATE TABLE IF NOT EXISTS `broker_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '券商委托事实数据库主键',
  `command_id` varchar(64) DEFAULT NULL COMMENT '本地交易指令编号；纯券商同步记录允许为空。',
  `attempt_no` int NOT NULL COMMENT '同一交易指令的提交尝试序号。',
  `broker_code` varchar(32) NOT NULL COMMENT '券商编码。',
  `account_id` varchar(64) NOT NULL COMMENT '账户哈希标识。',
  `stock_code` varchar(16) DEFAULT NULL COMMENT '股票代码。',
  `stock_name` varchar(64) DEFAULT NULL COMMENT '股票名称。',
  `market` varchar(16) DEFAULT NULL COMMENT '市场或交易类别。',
  `side` varchar(16) DEFAULT NULL COMMENT '买卖方向。',
  `trade_date` date DEFAULT NULL COMMENT '委托所属交易日。',
  `broker_order_no` varchar(64) DEFAULT NULL COMMENT '券商委托编号。',
  `client_order_no` varchar(64) DEFAULT NULL COMMENT '本地客户端委托编号。',
  `status` varchar(32) NOT NULL COMMENT '标准委托状态。',
  `raw_status` varchar(64) DEFAULT NULL COMMENT '券商原始状态。',
  `order_price` decimal(18,4) NOT NULL COMMENT '委托价格，单位为元。',
  `order_quantity` int NOT NULL COMMENT '委托数量。',
  `filled_quantity` int NOT NULL DEFAULT '0' COMMENT '累计成交数量。',
  `average_fill_price` decimal(18,4) DEFAULT NULL COMMENT '成交均价，单位为元。',
  `submitted_at` datetime(3) DEFAULT NULL COMMENT '委托提交时间。',
  `last_queried_at` datetime(3) DEFAULT NULL COMMENT '最近查询时间。',
  `terminal_at` datetime(3) DEFAULT NULL COMMENT '进入事实终态的时间。',
  `error_code` varchar(64) DEFAULT NULL COMMENT '券商错误码。',
  `error_message` longtext COMMENT '脱敏错误摘要。',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本。',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间。',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_broker_order_attempt` (`command_id`,`attempt_no`),
  UNIQUE KEY `uk_broker_order_broker_no` (`broker_code`,`account_id`,`trade_date`,`broker_order_no`),
  KEY `idx_broker_order_status_query` (`status`,`last_queried_at`),
  KEY `idx_broker_order_stock_date` (`stock_code`,`trade_date`),
  CONSTRAINT `chk_broker_order_attempt` CHECK ((`attempt_no` > 0)),
  CONSTRAINT `chk_broker_order_filled` CHECK (((`filled_quantity` >= 0) and (`filled_quantity` <= `order_quantity`))),
  CONSTRAINT `chk_broker_order_quantity` CHECK ((`order_quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='券商委托事实';

-- 券商数据同步批次
CREATE TABLE IF NOT EXISTS `broker_sync_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '券商同步批次数据库主键',
  `batch_id` varchar(64) NOT NULL COMMENT '全局同步批次编号。',
  `broker_code` varchar(32) NOT NULL COMMENT '券商编码。',
  `account_id` varchar(64) NOT NULL COMMENT '账户哈希标识。',
  `sync_type` varchar(32) NOT NULL COMMENT '同步类型。',
  `window_start` date DEFAULT NULL COMMENT '同步窗口开始日期。',
  `window_end` date DEFAULT NULL COMMENT '同步窗口结束日期。',
  `cursor_value` varchar(128) DEFAULT NULL COMMENT '分页或续传游标。',
  `status` varchar(32) NOT NULL COMMENT 'CREATED、RUNNING、SUCCESS、PARTIAL_SUCCESS 或 FAILED。',
  `request_count` int NOT NULL DEFAULT '0' COMMENT '请求次数。',
  `received_count` int NOT NULL DEFAULT '0' COMMENT '接收记录数。',
  `inserted_count` int NOT NULL DEFAULT '0' COMMENT '新增记录数。',
  `updated_count` int NOT NULL DEFAULT '0' COMMENT '更新记录数。',
  `skipped_count` int NOT NULL DEFAULT '0' COMMENT '跳过记录数。',
  `last_error` longtext COMMENT '脱敏后的最近错误摘要。',
  `started_at` datetime(3) DEFAULT NULL COMMENT '同步开始时间。',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '同步结束时间。',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间。',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_broker_sync_batch_id` (`batch_id`),
  UNIQUE KEY `uk_broker_sync_window` (`broker_code`,`account_id`,`sync_type`,`window_start`,`window_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='券商数据同步批次';

-- 已完成交易汇总记录
CREATE TABLE IF NOT EXISTS `trade_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '交易记录数据库主键',
  `broker_code` varchar(32) NOT NULL COMMENT '券商编码。',
  `account_id` varchar(64) NOT NULL COMMENT '账户哈希标识。',
  `stock_code` varchar(16) NOT NULL COMMENT '股票代码。',
  `stock_name` varchar(64) DEFAULT NULL COMMENT '股票名称。',
  `market` varchar(16) DEFAULT NULL COMMENT '市场或交易类别。',
  `buy_order_no` varchar(64) NOT NULL COMMENT '买入委托编号。',
  `sell_order_nos` longtext COMMENT '已匹配卖出委托编号，按时间顺序使用逗号分隔。',
  `status` varchar(16) NOT NULL COMMENT '交易状态：OPEN、PARTIAL 或 CLOSED。',
  `buy_time` datetime(3) NOT NULL COMMENT '买入成交时间。',
  `first_sell_time` datetime(3) DEFAULT NULL COMMENT '首次卖出成交时间。',
  `last_sell_time` datetime(3) DEFAULT NULL COMMENT '最后一次卖出成交时间。',
  `buy_quantity` int NOT NULL COMMENT '买入成交数量。',
  `sold_quantity` int NOT NULL DEFAULT '0' COMMENT '已卖出数量。',
  `remaining_quantity` int NOT NULL COMMENT '尚未卖出数量。',
  `buy_price` decimal(18,4) NOT NULL COMMENT '买入成交均价，单位为元。',
  `average_sell_price` decimal(18,4) DEFAULT NULL COMMENT '已卖出部分的加权成交均价，单位为元。',
  `buy_amount` decimal(20,4) NOT NULL COMMENT '买入总金额，不含费用。',
  `sold_cost_amount` decimal(20,4) NOT NULL DEFAULT '0.0000' COMMENT '已卖出数量对应的买入成本，不含费用。',
  `sell_amount` decimal(20,4) NOT NULL DEFAULT '0.0000' COMMENT '累计卖出金额，不含费用。',
  `buy_fee` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '整笔买入委托的估算佣金。',
  `allocated_buy_fee` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已卖出数量分摊的买入估算佣金。',
  `sell_fee` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已匹配卖出委托分摊的估算佣金。',
  `gross_profit` decimal(20,4) NOT NULL DEFAULT '0.0000' COMMENT '毛收益金额，不扣除费用。',
  `net_profit` decimal(20,4) NOT NULL DEFAULT '0.0000' COMMENT '净收益金额，扣除已分摊的买卖佣金。',
  `gross_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000' COMMENT '毛收益率，小数形式，0.1 表示 10%。',
  `return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000' COMMENT '净收益率，小数形式，0.1 表示 10%。',
  `holding_days` decimal(12,4) DEFAULT NULL COMMENT '按各次卖出数量加权的持仓自然日数。',
  `daily_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000' COMMENT '按加权持仓天数折算的复合日净收益率。',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间。',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_record_buy_order` (`broker_code`,`account_id`,`buy_time`,`buy_order_no`),
  KEY `idx_trade_record_account_buy_time` (`account_id`,`buy_time`),
  KEY `idx_trade_record_stock_status` (`stock_code`,`status`,`buy_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已完成交易汇总记录';

-- 自动真实交易执行批次
CREATE TABLE IF NOT EXISTS `automatic_trade_execution_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自动交易执行批次数据库主键',
  `batch_id` varchar(64) NOT NULL COMMENT '执行批次业务 ID。',
  `idempotency_key` varchar(96) NOT NULL COMMENT '交易日和执行类型组成的幂等键。',
  `execution_type` varchar(16) NOT NULL COMMENT '执行类型：BUY 或 EXIT。',
  `trade_date` date NOT NULL COMMENT '对应交易日。',
  `candidate_batch_id` varchar(64) DEFAULT NULL COMMENT '买入任务使用的候选批次 ID。',
  `status` varchar(16) NOT NULL COMMENT '执行状态：RUNNING、SUCCESS 或 FAILED。',
  `processed_count` int NOT NULL DEFAULT '0' COMMENT '实际尝试处理数量。',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功委托数量。',
  `message` longtext COMMENT '执行结果或失败摘要。',
  `started_at` datetime(3) NOT NULL COMMENT '执行开始时间。',
  `finished_at` datetime(3) DEFAULT NULL COMMENT '执行结束时间。',
  `updated_at` datetime(3) NOT NULL COMMENT '最近更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auto_trade_batch_id` (`batch_id`),
  UNIQUE KEY `uk_auto_trade_idempotency` (`idempotency_key`),
  KEY `idx_auto_trade_date_type` (`trade_date`,`execution_type`),
  KEY `idx_auto_trade_candidate_batch` (`candidate_batch_id`,`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动真实交易执行批次';

-- LSTM 模型不可变版本元数据
CREATE TABLE IF NOT EXISTS `lstm_model` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LSTM 模型版本唯一标识',
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型逻辑名称',
  `model_version` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型业务版本号',
  `parent_model_version_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父模型版本标识，首个版本为空',
  `training_config_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '训练参数配置 JSON',
  `input_contract_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '模型输入特征契约 JSON',
  `metrics_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '模型评估指标 JSON',
  `feature_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '特征工程版本号',
  `label_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '训练标签版本号',
  `engine_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型运行引擎名称',
  `engine_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型运行引擎版本',
  `djl_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DJL 框架版本',
  `parameter_sha256` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型参数文件 SHA-256 摘要',
  `parameter_size` bigint NOT NULL COMMENT '模型参数文件大小，单位为字节',
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型版本状态',
  `epoch` int NOT NULL COMMENT '完成训练轮次',
  `created_at` datetime(6) DEFAULT NULL COMMENT '模型版本创建时间',
  `parameter_file_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'GridFS 模型参数文件标识',
  `normalization_params` longtext COLLATE utf8mb4_unicode_ci COMMENT '输入特征归一化参数 JSON',
  `train_loss` double DEFAULT NULL COMMENT '训练集损失值',
  `val_loss` double DEFAULT NULL COMMENT '验证集损失值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lstm_model_version` (`model_name`,`model_version`),
  KEY `idx_model_created` (`model_name`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LSTM 模型不可变版本元数据';

-- 模型生产激活指针
CREATE TABLE IF NOT EXISTS `model_activation` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型激活记录唯一标识',
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型逻辑名称',
  `active_model_version_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前生产激活模型版本标识',
  `previous_model_version_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上一个生产模型版本标识，用于回滚',
  `activated_at` datetime(6) DEFAULT NULL COMMENT '模型最近激活时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_activation_name` (`model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型生产激活指针';

-- 外部模型制品元数据
CREATE TABLE IF NOT EXISTS `model_metadata` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型元数据唯一标识',
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型逻辑名称',
  `model_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型业务版本号',
  `artifact_uri` longtext COLLATE utf8mb4_unicode_ci COMMENT '模型制品访问位置',
  `artifact_storage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型制品存储方式',
  `artifact_file_ids_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '模型制品文件标识列表 JSON',
  `config_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '模型运行配置 JSON',
  `metrics_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '模型评估指标 JSON',
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型制品状态',
  `engine_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型运行引擎名称',
  `last_error` longtext COLLATE utf8mb4_unicode_ci COMMENT '最近一次模型操作失败原因',
  `updated_at` datetime(6) DEFAULT NULL COMMENT '模型元数据最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_metadata_name` (`model_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部模型制品元数据';

-- 模型运维操作审计
CREATE TABLE IF NOT EXISTS `model_operation_audit` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型操作审计记录唯一标识',
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型逻辑名称',
  `operation_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型操作类型',
  `operator_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人标识',
  `source_version_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作前模型版本标识',
  `target_version_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作目标模型版本标识',
  `success` bit(1) NOT NULL COMMENT '模型操作是否成功',
  `reason` longtext COLLATE utf8mb4_unicode_ci COMMENT '模型操作原因或失败说明',
  `created_at` datetime(6) DEFAULT NULL COMMENT '模型操作发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_model_created` (`model_name`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型运维操作审计';

-- 全市场模型预测批次
CREATE TABLE IF NOT EXISTS `model_prediction_batch` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型预测批次唯一标识',
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型逻辑名称。',
  `model_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '本批次固定使用的模型文档 ID。',
  `rerun_source_batch_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '历史重算来源批次 ID；普通预测批次为空。',
  `model_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型业务版本号。',
  `base_trade_date` date DEFAULT NULL COMMENT '预测基准交易日。',
  `target_trade_date` date DEFAULT NULL COMMENT '预测目标交易日。',
  `information_cutoff_at` datetime(6) DEFAULT NULL COMMENT '本批次允许使用信息的自然时间截止点。',
  `calendar_fallback` bit(1) DEFAULT NULL COMMENT '目标交易日是否由工作日规则降级推算。',
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次状态。',
  `total_stocks` int NOT NULL COMMENT '股票池总数。',
  `processed_stocks` int NOT NULL COMMENT '已处理股票数。',
  `success_count` int NOT NULL COMMENT '成功预测数量。',
  `failed_count` int NOT NULL COMMENT '失败预测数量。',
  `evaluated_count` int NOT NULL COMMENT '已评估数量。',
  `no_market_data_count` int NOT NULL COMMENT '缺少目标日行情数量。',
  `evaluation_queue_status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评估队列状态：WAITING、QUEUED、RUNNING、COMPLETED 或 FAILED。',
  `evaluation_wait_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评估等待原因代码：预测执行中、目标日未到达或目标日行情覆盖不足。',
  `evaluation_queued_at` datetime(6) DEFAULT NULL COMMENT '评估任务进入队列的时间。',
  `evaluation_started_at` datetime(6) DEFAULT NULL COMMENT '评估任务开始执行的时间。',
  `evaluation_finished_at` datetime(6) DEFAULT NULL COMMENT '评估任务完成的时间。',
  `evaluation_error_message` longtext COLLATE utf8mb4_unicode_ci COMMENT '最近一次评估队列执行失败原因。',
  `coverage_rate` double DEFAULT NULL COMMENT '预测覆盖率。',
  `evaluation_rate` double DEFAULT NULL COMMENT '评估完成率。',
  `direction_accuracy` double DEFAULT NULL COMMENT '方向准确率。',
  `actual_up_rate` double DEFAULT NULL COMMENT '实际上涨比例，用于构造常数方向基线。',
  `constant_baseline_accuracy` double DEFAULT NULL COMMENT '始终预测多数方向时的准确率基线。',
  `direction_auc` double DEFAULT NULL COMMENT '方向上涨概率的 ROC AUC；实际标签只有单一类别时为空。',
  `brier_score` double DEFAULT NULL COMMENT '上涨概率 Brier Score，越低越好。',
  `brier_baseline` double DEFAULT NULL COMMENT '固定使用实际上涨比例的 Brier 基线，越低越好。',
  `expected_calibration_error` double DEFAULT NULL COMMENT '上涨概率期望校准误差，越低越好。',
  `calibration_sample_count` int DEFAULT NULL COMMENT '参与概率校准指标计算的有效样本数。',
  `direction_probability_mean` double DEFAULT NULL COMMENT '上涨概率均值。',
  `direction_probability_std` double DEFAULT NULL COMMENT '上涨概率标准差，用于检测输出塌缩。',
  `direction_probability_min` double DEFAULT NULL COMMENT '上涨概率最小值。',
  `direction_probability_max` double DEFAULT NULL COMMENT '上涨概率最大值。',
  `predicted_return_std` double DEFAULT NULL COMMENT '预测收益率标准差，用于衡量横截面区分度。',
  `rolling_window_batch_count` int DEFAULT NULL COMMENT '最近滚动评估窗口包含的批次数。',
  `rolling_window_sample_count` int DEFAULT NULL COMMENT '最近滚动评估窗口的有效样本数。',
  `rolling_average_spearman_rank_ic` double DEFAULT NULL COMMENT '最近滚动窗口平均排序 IC。',
  `rolling_average_top_decile_return` double DEFAULT NULL COMMENT '最近滚动窗口 Top 10% 平均收益。',
  `return_mae` double DEFAULT NULL COMMENT '收益率平均绝对误差。',
  `return_rmse` double DEFAULT NULL COMMENT '收益率均方根误差。',
  `pearson_ic` double DEFAULT NULL COMMENT 'Pearson 横截面相关系数。',
  `spearman_rank_ic` double DEFAULT NULL COMMENT 'Spearman 排序相关系数。',
  `top_decile_average_return` double DEFAULT NULL COMMENT '预测收益最高 10% 股票的实际平均收益率。',
  `top_decile_direction_accuracy` double DEFAULT NULL COMMENT '预测收益最高 10% 股票的上涨命中率。',
  `high_confidence_accuracy` double DEFAULT NULL COMMENT '高置信度方向准确率。',
  `downside_risk_hit_rate` double DEFAULT NULL COMMENT '高下行风险样本的实际下跌识别率。',
  `ability_level` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型能力等级。',
  `iteration_recommended` bit(1) NOT NULL COMMENT '是否建议进入模型优化评估。',
  `ability_message` longtext COLLATE utf8mb4_unicode_ci COMMENT '能力结论说明。',
  `trading_eligibility` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交易门禁状态：DISABLED、OBSERVE_ONLY 或 TRADE_ELIGIBLE。',
  `trading_eligibility_reason` longtext COLLATE utf8mb4_unicode_ci COMMENT '交易门禁原因，禁止仅以能力等级代替交易准入结论。',
  `progress_message` longtext COLLATE utf8mb4_unicode_ci COMMENT '批次进度说明。',
  `triggered_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人。',
  `started_at` datetime(6) DEFAULT NULL COMMENT '执行开始时间。',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '执行完成时间。',
  `error_message` longtext COLLATE utf8mb4_unicode_ci COMMENT '失败原因。',
  `created_at` datetime(6) DEFAULT NULL COMMENT '记录创建时间。',
  `updated_at` datetime(6) DEFAULT NULL COMMENT '最近更新时间。',
  `version` bigint DEFAULT NULL COMMENT '乐观锁版本。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prediction_batch_rerun_source` (`rerun_source_batch_id`),
  KEY `idx_prediction_batch_model_date` (`model_id`,`base_trade_date`),
  KEY `idx_prediction_queue_order` (`status`,`target_trade_date`,`created_at`),
  KEY `idx_evaluation_queue_order` (`evaluation_queue_status`,`target_trade_date`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全市场模型预测批次';

-- 模型训练运行记录
CREATE TABLE IF NOT EXISTS `model_training_run` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型训练运行记录唯一标识',
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型逻辑名称',
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '训练运行状态',
  `trigger_source` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '训练触发来源',
  `triggered_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '训练触发人',
  `requested_config_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '请求使用的训练配置 JSON',
  `effective_config_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '门禁校验后实际生效的训练配置 JSON',
  `gate_result_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '训练业务门禁校验结果 JSON',
  `started_at` datetime(6) DEFAULT NULL COMMENT '训练开始时间',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '训练完成时间',
  `candidate_model_version_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '训练生成的候选模型版本标识',
  `dataset_snapshot_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '本次训练使用的数据集快照标识',
  `error_message` longtext COLLATE utf8mb4_unicode_ci COMMENT '训练失败原因',
  `trained_stocks` int DEFAULT NULL COMMENT '实际参与训练并产生有效样本的股票数量。',
  `progress_stage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前训练阶段。',
  `progress_message` longtext COLLATE utf8mb4_unicode_ci COMMENT '当前训练阶段说明。',
  `progress_percent` int DEFAULT NULL COMMENT '当前完成比例，范围 0-100。',
  `current_epoch` int DEFAULT NULL COMMENT '当前已完成训练轮次。',
  `total_epochs` int DEFAULT NULL COMMENT '本次训练计划总轮次。',
  `elapsed_seconds` bigint DEFAULT NULL COMMENT '当前已耗时，单位秒。',
  `estimated_remaining_seconds` bigint DEFAULT NULL COMMENT '预计剩余时间，单位秒。',
  `last_progress_at` datetime(6) DEFAULT NULL COMMENT '最近一次进度更新时间。',
  `train_samples` int DEFAULT NULL COMMENT '训练集样本数量',
  `val_samples` int DEFAULT NULL COMMENT '验证集样本数量',
  `train_loss` double DEFAULT NULL COMMENT '训练集损失值',
  `val_loss` double DEFAULT NULL COMMENT '验证集损失值',
  `created_at` datetime(6) DEFAULT NULL COMMENT '训练运行记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_training_model_created` (`model_name`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型训练运行记录';

-- 模型训练数据集快照
CREATE TABLE IF NOT EXISTS `training_dataset_snapshot` (
  `id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '训练数据集快照唯一标识',
  `model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型逻辑名称。',
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '快照状态，如 BUILDING、READY、FAILED、EXPIRED。',
  `contract_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '数据契约摘要，用于判断是否可以复用。',
  `feature_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '特征定义版本。',
  `dataset_end_date` date DEFAULT NULL COMMENT '数据截止日期。',
  `days` int NOT NULL COMMENT '每只股票读取的交易日数量。',
  `stock_count` int NOT NULL COMMENT '快照包含的股票数量。',
  `trained_stocks` int NOT NULL COMMENT '有效训练股票数量。',
  `train_samples` int NOT NULL COMMENT '训练样本数量。',
  `val_samples` int NOT NULL COMMENT '验证样本数量。',
  `test_samples` int NOT NULL DEFAULT '0' COMMENT '独立测试样本数量。',
  `walk_forward_fold_count` int NOT NULL DEFAULT '0' COMMENT '主策略 Walk-Forward 折叠数量。',
  `strict_isolation_fold_count` int NOT NULL DEFAULT '0' COMMENT '60 日严格隔离敏感性折叠数量。',
  `split_policy_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '全市场日期切分策略版本。',
  `data_source_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型数据来源版本。',
  `train_start_date` date DEFAULT NULL COMMENT '训练集开始日期。',
  `train_end_date` date DEFAULT NULL COMMENT '训练集结束日期。',
  `validation_start_date` date DEFAULT NULL COMMENT '验证集开始日期。',
  `validation_end_date` date DEFAULT NULL COMMENT '验证集结束日期。',
  `test_start_date` date DEFAULT NULL COMMENT '独立测试集开始日期。',
  `test_end_date` date DEFAULT NULL COMMENT '独立测试集结束日期。',
  `artifact_storage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工件存储类型，如 MONGO_GRIDFS 或 LOCAL_FILE。',
  `artifact_file_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MongoDB GridFS 中的清单或单文件工件 ID。',
  `artifact_sha256` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '快照文件 SHA-256。',
  `artifact_size` bigint NOT NULL COMMENT '快照文件大小，单位字节。',
  `artifact_format` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '快照制品格式，如 CHUNKED_JSON_GZIP_V1。',
  `chunk_count` int NOT NULL COMMENT '快照分片数量，历史单文件快照为 0。',
  `created_at` datetime(6) DEFAULT NULL COMMENT '创建时间。',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '完成时间。',
  `error_message` longtext COLLATE utf8mb4_unicode_ci COMMENT '失败原因。',
  PRIMARY KEY (`id`),
  KEY `idx_snapshot_model_created` (`model_name`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型训练数据集快照';
