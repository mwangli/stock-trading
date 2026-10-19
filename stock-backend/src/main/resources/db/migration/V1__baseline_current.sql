-- AI_GENERATE_START --
-- 当前数据库结构基线快照；业务历史数据不写入迁移文件，保留在数据库备份中。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- stock_trading@account_snapshot.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: account_snapshot
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `account_snapshot`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `account_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `snapshot_id` varchar(64) NOT NULL,
  `broker_code` varchar(32) NOT NULL,
  `account_id` varchar(64) NOT NULL,
  `total_assets` decimal(20,4) DEFAULT NULL,
  `available_cash` decimal(20,4) DEFAULT NULL,
  `frozen_cash` decimal(20,4) DEFAULT NULL,
  `market_value` decimal(20,4) DEFAULT NULL,
  `daily_profit_loss` decimal(20,4) DEFAULT NULL,
  `captured_at` datetime(3) NOT NULL,
  `source` varchar(32) NOT NULL,
  `raw_event_id` varchar(64) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_snapshot_id` (`snapshot_id`),
  UNIQUE KEY `uk_account_snapshot_capture` (`account_id`,`captured_at`,`source`)
) ENGINE=InnoDB AUTO_INCREMENT=467 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@agent_memory.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: agent_memory
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `agent_memory`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `agent_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `confidence` double NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `memory_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `memory_type` enum('EPISODIC','PREFERENCE','SEMANTIC','SESSION','TRADING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_ref` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_type` enum('MODEL_INFERRED','SYSTEM_FACT','TRADE_RESULT','USER_CONFIRMED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','CORRECTED','EXPIRED','FORGOTTEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_scope` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_8wjrcd94bfyl3wv5163fpxywx` (`memory_id`),
  KEY `idx_agent_memory_scope_status` (`user_scope`,`status`),
  KEY `idx_agent_memory_expire` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@agent_session.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: agent_session
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `agent_session`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `agent_session` (
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `status` enum('ACTIVE','CLOSED','EXPIRED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_scope` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@agent_task.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: agent_task
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `agent_task`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `agent_task` (
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `evidence_summary` text COLLATE utf8mb4_unicode_ci,
  `evidence_refs` text COLLATE utf8mb4_unicode_ci,
  `uncertainty` text COLLATE utf8mb4_unicode_ci,
  `question` text COLLATE utf8mb4_unicode_ci,
  `result_summary` text COLLATE utf8mb4_unicode_ci,
  `result_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `structured_result` text COLLATE utf8mb4_unicode_ci,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_type` enum('DIAGNOSTIC','REPORT','RESEARCH','TRADE_PLAN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`task_id`),
  KEY `idx_agent_task_session_time` (`session_id`,`created_at`),
  KEY `idx_agent_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@agent_tool_call.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: agent_tool_call
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `agent_tool_call`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `agent_tool_call` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `task_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tool_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tool_version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `parameter_summary` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result_summary` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_tool_call_task_time` (`task_id`,`started_at`),
  KEY `idx_agent_tool_call_tool_time` (`tool_name`,`started_at`)
) ENGINE=InnoDB AUTO_INCREMENT=68 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@agent_trade_plan.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: agent_trade_plan
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `agent_trade_plan`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `agent_trade_plan` (
  `plan_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `candidate_batch_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `prediction_batch_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `decision_message` text COLLATE utf8mb4_unicode_ci,
  `execution_trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `risk_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `executed_at` datetime(6) DEFAULT NULL,
  `direction` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `evidence_refs` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `planned_amount` decimal(18,4) NOT NULL,
  `planned_quantity` int DEFAULT NULL,
  `status` enum('EXECUTED','EXECUTING','EXPIRED','FAILED','PENDING_REVALIDATION','RISK_REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `stock_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `target_trade_date` date DEFAULT NULL,
  `strategy_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `price_constraint` text COLLATE utf8mb4_unicode_ci,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `risk_warnings` text COLLATE utf8mb4_unicode_ci,
  `uncertainty` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`plan_id`),
  UNIQUE KEY `UK_6aqp7g4ruowoovw1rau8balb4` (`idempotency_key`),
  KEY `idx_agent_trade_plan_status_expire` (`status`,`expires_at`),
  KEY `idx_agent_trade_plan_candidate` (`candidate_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@automatic_trade_execution_batch.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: automatic_trade_execution_batch
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `automatic_trade_execution_batch`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `automatic_trade_execution_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` varchar(64) NOT NULL,
  `idempotency_key` varchar(96) NOT NULL,
  `execution_type` varchar(16) NOT NULL,
  `trade_date` date NOT NULL,
  `candidate_batch_id` varchar(64) DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `processed_count` int NOT NULL DEFAULT '0',
  `success_count` int NOT NULL DEFAULT '0',
  `message` varchar(500) DEFAULT NULL,
  `started_at` datetime(3) NOT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auto_trade_batch_id` (`batch_id`),
  UNIQUE KEY `uk_auto_trade_idempotency` (`idempotency_key`),
  KEY `idx_auto_trade_date_type` (`trade_date`,`execution_type`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@backtest_run.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: backtest_run
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `backtest_run`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `backtest_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `result_id` varchar(64) NOT NULL,
  `baseline_type` varchar(32) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `status` varchar(24) NOT NULL,
  `degraded` tinyint(1) NOT NULL DEFAULT '1',
  `data_version` varchar(255) NOT NULL,
  `stock_pool_version` varchar(255) NOT NULL,
  `feature_version` varchar(128) NOT NULL,
  `fee_version` varchar(128) NOT NULL,
  `code_version` varchar(128) NOT NULL,
  `total_return` decimal(20,10) NOT NULL,
  `annualized_return` decimal(20,10) NOT NULL,
  `max_drawdown` decimal(20,10) NOT NULL,
  `net_return_1000` decimal(20,10) NOT NULL,
  `net_return_5000` decimal(20,10) NOT NULL,
  `net_return_10000` decimal(20,10) NOT NULL,
  `total_trades` int NOT NULL,
  `win_trades` int NOT NULL,
  `win_rate` decimal(20,10) NOT NULL,
  `avg_profit` decimal(20,10) NOT NULL,
  `avg_loss` decimal(20,10) NOT NULL,
  `blocked_trades` int NOT NULL,
  `skipped_trades` int NOT NULL,
  `cost_time_ms` bigint NOT NULL,
  `request_json` json NOT NULL,
  `limitations_json` json NOT NULL,
  `calculated_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_backtest_run_result_id` (`result_id`),
  KEY `idx_backtest_run_range_type` (`start_date`,`end_date`,`baseline_type`),
  KEY `idx_backtest_run_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@backtest_trade.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: backtest_trade
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `backtest_trade`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `backtest_trade` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `result_id` varchar(64) NOT NULL,
  `sequence_no` int NOT NULL,
  `stock_code` varchar(16) NOT NULL,
  `signal_date` date NOT NULL,
  `entry_date` date NOT NULL,
  `planned_exit_date` date NOT NULL,
  `actual_exit_date` date DEFAULT NULL,
  `raw_entry_price` decimal(18,4) DEFAULT NULL,
  `raw_exit_price` decimal(18,4) DEFAULT NULL,
  `executed_entry_price` decimal(18,4) DEFAULT NULL,
  `executed_exit_price` decimal(18,4) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '0',
  `quantity_1000` int NOT NULL DEFAULT '0',
  `quantity_10000` int NOT NULL DEFAULT '0',
  `gross_return` decimal(20,10) NOT NULL,
  `total_fee` decimal(18,4) NOT NULL,
  `net_profit` decimal(20,4) NOT NULL,
  `net_profit_1000` decimal(20,4) NOT NULL,
  `net_profit_10000` decimal(20,4) NOT NULL,
  `status` varchar(32) NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_backtest_trade_sequence` (`result_id`,`sequence_no`),
  KEY `idx_backtest_trade_stock_date` (`stock_code`,`entry_date`),
  KEY `idx_backtest_trade_result_status` (`result_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@broker_order.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: broker_order
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `broker_order`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `broker_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `command_id` varchar(64) DEFAULT NULL,
  `attempt_no` int NOT NULL,
  `broker_code` varchar(32) NOT NULL,
  `account_id` varchar(64) NOT NULL,
  `stock_code` varchar(16) DEFAULT NULL,
  `stock_name` varchar(64) DEFAULT NULL,
  `market` varchar(16) DEFAULT NULL,
  `side` varchar(16) DEFAULT NULL,
  `trade_date` date DEFAULT NULL,
  `broker_order_no` varchar(64) DEFAULT NULL,
  `client_order_no` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `raw_status` varchar(64) DEFAULT NULL,
  `order_price` decimal(18,4) NOT NULL,
  `order_quantity` int NOT NULL,
  `filled_quantity` int NOT NULL DEFAULT '0',
  `average_fill_price` decimal(18,4) DEFAULT NULL,
  `submitted_at` datetime(3) DEFAULT NULL,
  `last_queried_at` datetime(3) DEFAULT NULL,
  `terminal_at` datetime(3) DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_broker_order_attempt` (`command_id`,`attempt_no`),
  UNIQUE KEY `uk_broker_order_broker_no` (`broker_code`,`account_id`,`trade_date`,`broker_order_no`),
  KEY `idx_broker_order_status_query` (`status`,`last_queried_at`),
  KEY `idx_broker_order_stock_date` (`stock_code`,`trade_date`),
  CONSTRAINT `chk_broker_order_attempt` CHECK ((`attempt_no` > 0)),
  CONSTRAINT `chk_broker_order_filled` CHECK (((`filled_quantity` >= 0) and (`filled_quantity` <= `order_quantity`))),
  CONSTRAINT `chk_broker_order_quantity` CHECK ((`order_quantity` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=3748 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@broker_sync_batch.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: broker_sync_batch
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `broker_sync_batch`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `broker_sync_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` varchar(64) NOT NULL,
  `broker_code` varchar(32) NOT NULL,
  `account_id` varchar(64) NOT NULL,
  `sync_type` varchar(32) NOT NULL,
  `window_start` date DEFAULT NULL,
  `window_end` date DEFAULT NULL,
  `cursor_value` varchar(128) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `request_count` int NOT NULL DEFAULT '0',
  `received_count` int NOT NULL DEFAULT '0',
  `inserted_count` int NOT NULL DEFAULT '0',
  `updated_count` int NOT NULL DEFAULT '0',
  `skipped_count` int NOT NULL DEFAULT '0',
  `last_error` varchar(500) DEFAULT NULL,
  `started_at` datetime(3) DEFAULT NULL,
  `finished_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_broker_sync_batch_id` (`batch_id`),
  UNIQUE KEY `uk_broker_sync_window` (`broker_code`,`account_id`,`sync_type`,`window_start`,`window_end`)
) ENGINE=InnoDB AUTO_INCREMENT=187 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@execution_audit.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: execution_audit
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `execution_audit`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `execution_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `audit_id` varchar(64) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `actor_type` varchar(32) NOT NULL,
  `actor_id` varchar(64) DEFAULT NULL,
  `account_id` varchar(64) DEFAULT NULL,
  `command_id` varchar(64) DEFAULT NULL,
  `broker_order_no` varchar(64) DEFAULT NULL,
  `trading_mode` varchar(24) DEFAULT NULL,
  `result` varchar(32) NOT NULL,
  `detail_json` json DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `occurred_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_audit_id` (`audit_id`),
  KEY `idx_execution_audit_command_time` (`command_id`,`occurred_at`),
  KEY `idx_execution_audit_event_time` (`event_type`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@historical_stock_universe.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: historical_stock_universe
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `historical_stock_universe`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `historical_stock_universe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stock_code` varchar(16) NOT NULL,
  `stock_name` varchar(64) DEFAULT NULL,
  `market` varchar(16) NOT NULL,
  `board_code` varchar(32) DEFAULT NULL,
  `industry_code` varchar(64) DEFAULT NULL,
  `listed_date` date DEFAULT NULL,
  `delisted_date` date DEFAULT NULL,
  `st_status` varchar(32) NOT NULL DEFAULT 'NORMAL',
  `effective_from` date NOT NULL,
  `effective_to` date DEFAULT NULL,
  `source` varchar(64) NOT NULL,
  `source_version` varchar(128) NOT NULL,
  `captured_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_universe_code_effective_source` (`stock_code`,`effective_from`,`source`),
  KEY `idx_universe_effective_range` (`effective_from`,`effective_to`),
  KEY `idx_universe_market_board` (`market`,`board_code`,`effective_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@job_config.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: job_config
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `job_config`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `job_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) DEFAULT NULL,
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `job_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` int NOT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `module` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sync_mode` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_5mnhc0tocoiov6bbev5s7wk1b` (`job_name`)
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@job_execution_record.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: job_execution_record
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `job_execution_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `job_execution_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `error_type` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `finished_time` datetime(6) DEFAULT NULL,
  `host_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `job_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_summary` text COLLATE utf8mb4_unicode_ci,
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scheduled_time` datetime(6) DEFAULT NULL,
  `started_time` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','FAILED','QUEUED','REJECTED','RUNNING','SKIPPED','SUCCESS') COLLATE utf8mb4_unicode_ci NOT NULL,
  `trigger_source` enum('MANUAL','RECOVERY','SCHEDULED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `triggered_by` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active_lock_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_job_execution_run_id` (`run_id`),
  UNIQUE KEY `UK_lwdw0r14bqickrfimph25dluc` (`active_lock_key`),
  KEY `idx_job_execution_job_time` (`job_name`,`created_at`),
  KEY `idx_job_execution_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=539 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@model_training_record.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: model_training_record
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `model_training_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `model_training_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `last_duration_seconds` bigint DEFAULT NULL,
  `last_epochs` int DEFAULT NULL,
  `last_model_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_train_loss` double DEFAULT NULL,
  `last_train_time` datetime(6) DEFAULT NULL,
  `last_val_loss` double DEFAULT NULL,
  `stock_code` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `stock_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trained` bit(1) NOT NULL,
  `training` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_3gpyetjlm9el02creiv8nsywg` (`stock_code`),
  KEY `idx_stock_code` (`stock_code`),
  KEY `idx_last_train_time` (`last_train_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2070 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@position_snapshot_item.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: position_snapshot_item
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `position_snapshot_item`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `position_snapshot_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `snapshot_id` varchar(64) NOT NULL,
  `stock_code` varchar(16) NOT NULL,
  `stock_name` varchar(64) DEFAULT NULL,
  `market` varchar(16) DEFAULT NULL,
  `shareholder_account` varchar(64) NOT NULL DEFAULT '',
  `total_quantity` int NOT NULL DEFAULT '0',
  `available_quantity` int NOT NULL DEFAULT '0',
  `frozen_quantity` int NOT NULL DEFAULT '0',
  `average_cost` decimal(18,4) DEFAULT NULL,
  `current_price` decimal(18,4) DEFAULT NULL,
  `market_value` decimal(20,4) DEFAULT NULL,
  `first_buy_date` date DEFAULT NULL,
  `last_buy_date` date DEFAULT NULL,
  `captured_at` datetime(3) NOT NULL,
  `raw_event_id` varchar(64) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_snapshot_item` (`snapshot_id`,`stock_code`,`shareholder_account`),
  KEY `idx_position_snapshot_item_stock` (`stock_code`,`captured_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@position_snapshot.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: position_snapshot
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `position_snapshot`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `position_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `snapshot_id` varchar(64) NOT NULL,
  `broker_code` varchar(32) NOT NULL,
  `account_id` varchar(64) NOT NULL,
  `captured_at` datetime(3) NOT NULL,
  `source` varchar(32) NOT NULL,
  `raw_event_id` varchar(64) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_snapshot_id` (`snapshot_id`),
  KEY `idx_position_snapshot_account_capture` (`account_id`,`captured_at`)
) ENGINE=InnoDB AUTO_INCREMENT=467 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_blob_triggers.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_blob_triggers
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_blob_triggers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_blob_triggers` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `BLOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `FK_QRTZ_BLOB_TRIGGER` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_calendars.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_calendars
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_calendars`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_calendars` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `CALENDAR_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `CALENDAR` blob NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`CALENDAR_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_cron_triggers.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_cron_triggers
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_cron_triggers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_cron_triggers` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `CRON_EXPRESSION` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TIME_ZONE_ID` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `FK_QRTZ_CRON_TRIGGER` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_fired_triggers.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_fired_triggers
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_fired_triggers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_fired_triggers` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ENTRY_ID` varchar(95) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `INSTANCE_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `FIRED_TIME` bigint NOT NULL,
  `SCHED_TIME` bigint NOT NULL,
  `PRIORITY` int NOT NULL,
  `STATE` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `JOB_NAME` varchar(190) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `JOB_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `IS_NONCONCURRENT` varchar(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `REQUESTS_RECOVERY` varchar(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`ENTRY_ID`),
  KEY `IDX_QRTZ_FT_TRIG_INST_NAME` (`SCHED_NAME`,`INSTANCE_NAME`),
  KEY `IDX_QRTZ_FT_INST_JOB_REQ_RCVRY` (`SCHED_NAME`,`INSTANCE_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_FT_J_G` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_FT_T_G` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_FT_TG` (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_job_details.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_job_details
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_job_details`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_job_details` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `JOB_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `JOB_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `DESCRIPTION` varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `JOB_CLASS_NAME` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `IS_DURABLE` varchar(1) COLLATE utf8mb4_unicode_ci NOT NULL,
  `IS_NONCONCURRENT` varchar(1) COLLATE utf8mb4_unicode_ci NOT NULL,
  `IS_UPDATE_DATA` varchar(1) COLLATE utf8mb4_unicode_ci NOT NULL,
  `REQUESTS_RECOVERY` varchar(1) COLLATE utf8mb4_unicode_ci NOT NULL,
  `JOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_J_REQ_RECOVERY` (`SCHED_NAME`,`REQUESTS_RECOVERY`),
  KEY `IDX_QRTZ_J_GRP` (`SCHED_NAME`,`JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_locks.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_locks
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_locks`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_locks` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `LOCK_NAME` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`LOCK_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_paused_trigger_grps.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_paused_trigger_grps
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_paused_trigger_grps`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_paused_trigger_grps` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_scheduler_state.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_scheduler_state
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_scheduler_state`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_scheduler_state` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `INSTANCE_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `LAST_CHECKIN_TIME` bigint NOT NULL,
  `CHECKIN_INTERVAL` bigint NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`INSTANCE_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_simple_triggers.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_simple_triggers
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_simple_triggers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_simple_triggers` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `REPEAT_COUNT` bigint NOT NULL,
  `REPEAT_INTERVAL` bigint NOT NULL,
  `TIMES_TRIGGERED` bigint NOT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `FK_QRTZ_SIMPLE_TRIGGER` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_simprop_triggers.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_simprop_triggers
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_simprop_triggers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_simprop_triggers` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `STR_PROP_1` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `STR_PROP_2` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `STR_PROP_3` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `INT_PROP_1` int DEFAULT NULL,
  `INT_PROP_2` int DEFAULT NULL,
  `LONG_PROP_1` bigint DEFAULT NULL,
  `LONG_PROP_2` bigint DEFAULT NULL,
  `DEC_PROP_1` decimal(13,4) DEFAULT NULL,
  `DEC_PROP_2` decimal(13,4) DEFAULT NULL,
  `BOOL_PROP_1` varchar(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `BOOL_PROP_2` varchar(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  CONSTRAINT `FK_QRTZ_SIMPROP_TRIGGER` FOREIGN KEY (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`) REFERENCES `qrtz_triggers` (`SCHED_NAME`, `TRIGGER_NAME`, `TRIGGER_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@qrtz_triggers.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: qrtz_triggers
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `qrtz_triggers`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `qrtz_triggers` (
  `SCHED_NAME` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `JOB_NAME` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `JOB_GROUP` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `DESCRIPTION` varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `NEXT_FIRE_TIME` bigint DEFAULT NULL,
  `PREV_FIRE_TIME` bigint DEFAULT NULL,
  `PRIORITY` int DEFAULT NULL,
  `TRIGGER_STATE` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TRIGGER_TYPE` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `START_TIME` bigint NOT NULL,
  `END_TIME` bigint DEFAULT NULL,
  `CALENDAR_NAME` varchar(190) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `MISFIRE_INSTR` smallint DEFAULT NULL,
  `JOB_DATA` blob,
  PRIMARY KEY (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_J` (`SCHED_NAME`,`JOB_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_JG` (`SCHED_NAME`,`JOB_GROUP`),
  KEY `IDX_QRTZ_T_C` (`SCHED_NAME`,`CALENDAR_NAME`),
  KEY `IDX_QRTZ_T_G` (`SCHED_NAME`,`TRIGGER_GROUP`),
  KEY `IDX_QRTZ_T_STATE` (`SCHED_NAME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_STATE` (`SCHED_NAME`,`TRIGGER_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_N_G_STATE` (`SCHED_NAME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NEXT_FIRE_TIME` (`SCHED_NAME`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST` (`SCHED_NAME`,`TRIGGER_STATE`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_STATE`),
  KEY `IDX_QRTZ_T_NFT_ST_MISFIRE_GRP` (`SCHED_NAME`,`MISFIRE_INSTR`,`NEXT_FIRE_TIME`,`TRIGGER_GROUP`,`TRIGGER_STATE`),
  CONSTRAINT `FK_QRTZ_TRIGGER_JOB` FOREIGN KEY (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`) REFERENCES `qrtz_job_details` (`SCHED_NAME`, `JOB_NAME`, `JOB_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@risk_decision.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: risk_decision
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `risk_decision`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `risk_decision` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `decision_id` varchar(64) NOT NULL,
  `command_id` varchar(64) NOT NULL,
  `passed` tinyint(1) NOT NULL,
  `rule_version` varchar(64) NOT NULL,
  `violations_json` json DEFAULT NULL,
  `account_snapshot_id` varchar(64) DEFAULT NULL,
  `position_snapshot_id` varchar(64) DEFAULT NULL,
  `evaluated_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_decision_id` (`decision_id`),
  KEY `idx_risk_decision_command_time` (`command_id`,`evaluated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@sentiment_evaluation.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: sentiment_evaluation
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `sentiment_evaluation`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sentiment_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `evaluation_run_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model_version` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dataset_version` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sample_count` int NOT NULL,
  `correct_count` int NOT NULL,
  `accuracy` double NOT NULL,
  `macro_f1` double NOT NULL,
  `negative_recall` double NOT NULL,
  `positive_recall` double NOT NULL DEFAULT '0',
  `predicted_neutral_ratio` double NOT NULL DEFAULT '0',
  `event_type_coverage` double NOT NULL DEFAULT '0',
  `actionable_news_ratio` double NOT NULL DEFAULT '0',
  `direction_accuracy` double NOT NULL DEFAULT '0',
  `impact_magnitude_mae` double NOT NULL DEFAULT '0',
  `expected_calibration_error` double NOT NULL,
  `p95_latency_ms` double NOT NULL,
  `conclusion` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `evaluated_at` datetime(6) NOT NULL,
  `event_type_macro_f1` double NOT NULL,
  `lstm_incremental_return` double DEFAULT NULL,
  `max_drawdown` double DEFAULT NULL,
  `net_return_after_costs` double DEFAULT NULL,
  `t1_excess_return_ic` double DEFAULT NULL,
  `t3_excess_return_ic` double DEFAULT NULL,
  `t5_excess_return_ic` double DEFAULT NULL,
  `top_k_news_hit_rate` double DEFAULT NULL,
  `top_k_portfolio_return` double DEFAULT NULL,
  `turnover_rate` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sentiment_evaluation_run` (`evaluation_run_id`),
  KEY `idx_sentiment_evaluation_time` (`evaluated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@stock_info.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: stock_info
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `stock_info`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `stock_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `change_amount` decimal(10,3) DEFAULT NULL,
  `change_percent` decimal(10,4) DEFAULT NULL,
  `code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `industry_code` int DEFAULT NULL,
  `market` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `price` decimal(10,3) DEFAULT NULL,
  `total_market_value` decimal(20,2) DEFAULT NULL,
  `turnover_rate` decimal(10,4) DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `volume_ratio` decimal(10,4) DEFAULT NULL,
  `news_last_attempt_at` datetime(6) DEFAULT NULL,
  `news_last_success_at` datetime(6) DEFAULT NULL,
  `news_last_publish_time` datetime(6) DEFAULT NULL,
  `news_sync_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEVER_SYNCED',
  `news_sync_batch_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `news_sync_error_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `news_consecutive_no_change_count` int NOT NULL DEFAULT '0',
  `news_next_sync_at` datetime(6) DEFAULT NULL,
  `listed_date` date DEFAULT NULL,
  `volume` decimal(24,4) DEFAULT NULL,
  `bid_price` decimal(10,3) DEFAULT NULL,
  `ask_price` decimal(10,3) DEFAULT NULL,
  `security_category` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `market_code` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `momentum_strength` decimal(12,6) DEFAULT NULL,
  `volume_price_signal` decimal(12,6) DEFAULT NULL,
  `turnover_anomaly` decimal(12,6) DEFAULT NULL,
  `abnormal_volume_flag` tinyint(1) NOT NULL DEFAULT '0',
  `bid_ask_spread` decimal(10,3) DEFAULT NULL,
  `bid_ask_spread_rate` decimal(12,8) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_stock_code` (`code`),
  KEY `idx_stock_name` (`name`),
  KEY `idx_market` (`market`),
  KEY `idx_stock_info_news_sync_due` (`news_sync_status`,`news_next_sync_at`)
) ENGINE=InnoDB AUTO_INCREMENT=4620 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@strategy_config.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: strategy_config
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `strategy_config`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `strategy_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bollinger_breakout_threshold` double NOT NULL,
  `bollinger_weight` double NOT NULL,
  `config_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `high_return_threshold` int NOT NULL,
  `indicator_enabled` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `loss_return_threshold` int NOT NULL,
  `low_return_threshold` int NOT NULL,
  `lstm_weight` double NOT NULL,
  `min_score` double NOT NULL,
  `mode` enum('AGGRESSIVE','BALANCED','CONSERVATIVE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `normal_return_threshold` int NOT NULL,
  `rsi_overbought_threshold` double NOT NULL,
  `rsi_weight` double NOT NULL,
  `sentiment_weight` double NOT NULL,
  `top_n` int NOT NULL,
  `trailing_stop_tolerance` double NOT NULL,
  `trailing_stop_weight` double NOT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `volume_shrink_threshold` double NOT NULL,
  `volume_weight` double NOT NULL,
  `minimum_listing_years` double NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@strategy_evaluation.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: strategy_evaluation
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `strategy_evaluation`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `strategy_evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `evaluation_run_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `strategy_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `strategy_config_version` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_version` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sentiment_model_version` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `window_start` date DEFAULT NULL,
  `window_end` date DEFAULT NULL,
  `validation_start` date DEFAULT NULL,
  `validation_end` date DEFAULT NULL,
  `sample_count` int NOT NULL,
  `batch_count` int NOT NULL,
  `net_return` double DEFAULT NULL,
  `max_drawdown` double DEFAULT NULL,
  `win_rate` double DEFAULT NULL,
  `profit_loss_ratio` double DEFAULT NULL,
  `stability_score` double DEFAULT NULL,
  `out_of_sample` bit(1) NOT NULL,
  `data_complete` bit(1) NOT NULL,
  `sample_sufficient` bit(1) NOT NULL,
  `conclusion` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `calculated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_strategy_evaluation_run_mode` (`evaluation_run_id`,`strategy_mode`),
  KEY `idx_strategy_evaluation_time` (`calculated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@strategy_runtime_state.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: strategy_runtime_state
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `strategy_runtime_state`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `strategy_runtime_state` (
  `state_key` varchar(64) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `current_mode` varchar(32) NOT NULL DEFAULT 'BALANCED',
  `disabled_indicators_json` text NOT NULL,
  `last_switch_time` datetime(3) DEFAULT NULL,
  `last_switch_reason` varchar(500) DEFAULT NULL,
  `config_version` varchar(64) NOT NULL DEFAULT '1',
  `auto_switch_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `last_auto_switch_time` datetime(3) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`state_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@sync_checkpoint.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: sync_checkpoint
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `sync_checkpoint`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sync_checkpoint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataset` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_success_cursor` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_success_date` date DEFAULT NULL,
  `scope_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sync_checkpoint_scope` (`dataset`,`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@sync_gap.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: sync_gap
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `sync_gap`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sync_gap` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `dataset` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_error` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `retry_count` int NOT NULL,
  `scope_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `window_end` date NOT NULL,
  `window_start` date NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@sys_user_preference.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: sys_user_preference
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `sys_user_preference`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_user_preference` (
  `user_id` bigint NOT NULL,
  `menu_order` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_sys_user_preference_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@sys_user.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: sys_user
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `sys_user`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@system_runtime_config.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: system_runtime_config
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `system_runtime_config`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `system_runtime_config` (
  `node_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scheduler_enabled` tinyint(1) NOT NULL,
  `data_collection_enabled` tinyint(1) NOT NULL,
  `news_collection_enabled` tinyint(1) NOT NULL,
  `startup_data_sync_enabled` tinyint(1) NOT NULL,
  `startup_history_sync_enabled` tinyint(1) NOT NULL,
  `startup_news_sync_enabled` tinyint(1) NOT NULL,
  `model_training_enabled` tinyint(1) NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@trade_authorization.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: trade_authorization
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `trade_authorization`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `trade_authorization` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `authorization_id` varchar(64) NOT NULL,
  `account_id` varchar(64) NOT NULL,
  `allowed_mode` varchar(24) NOT NULL,
  `max_single_amount` decimal(20,4) NOT NULL,
  `max_daily_amount` decimal(20,4) NOT NULL,
  `max_daily_trade_count` int NOT NULL,
  `effective_at` datetime(3) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `status` varchar(24) NOT NULL,
  `created_by` varchar(64) NOT NULL,
  `revoked_at` datetime(3) DEFAULT NULL,
  `revoke_reason` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_authorization_id` (`authorization_id`),
  KEY `idx_trade_authorization_account_status` (`account_id`,`status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@trade_calendar.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: trade_calendar
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `trade_calendar`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `trade_calendar` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `market` varchar(16) NOT NULL,
  `trade_date` date NOT NULL,
  `trading_day` tinyint(1) NOT NULL,
  `day_status` varchar(16) NOT NULL DEFAULT 'UNKNOWN',
  `closure_type` varchar(16) NOT NULL DEFAULT 'UNKNOWN',
  `verification_status` varchar(16) NOT NULL DEFAULT 'UNVERIFIED',
  `previous_trading_day` date DEFAULT NULL,
  `next_trading_day` date DEFAULT NULL,
  `source` varchar(64) NOT NULL,
  `source_version` varchar(128) NOT NULL,
  `source_fetched_at` datetime(6) DEFAULT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_calendar_market_date` (`market`,`trade_date`),
  KEY `idx_trade_calendar_date_trading` (`trade_date`,`trading_day`),
  KEY `idx_trade_calendar_market_status_date` (`market`,`verification_status`,`day_status`,`trade_date`)
) ENGINE=InnoDB AUTO_INCREMENT=670 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@trade_command.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: trade_command
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `trade_command`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `trade_command` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `command_id` varchar(64) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` varchar(64) DEFAULT NULL,
  `account_id` varchar(64) NOT NULL,
  `broker_code` varchar(32) NOT NULL,
  `stock_code` varchar(16) NOT NULL,
  `market` varchar(16) DEFAULT NULL,
  `side` varchar(16) NOT NULL,
  `order_type` varchar(16) NOT NULL DEFAULT 'LIMIT',
  `requested_price` decimal(18,4) NOT NULL,
  `requested_quantity` int NOT NULL,
  `estimated_amount` decimal(20,4) DEFAULT NULL,
  `trading_mode` varchar(24) NOT NULL,
  `status` varchar(32) NOT NULL,
  `trade_date` date NOT NULL,
  `valid_until` datetime(3) DEFAULT NULL,
  `authorization_id` varchar(64) DEFAULT NULL,
  `model_version` varchar(128) DEFAULT NULL,
  `feature_snapshot_id` varchar(64) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_command_command_id` (`command_id`),
  UNIQUE KEY `uk_trade_command_idempotency_key` (`idempotency_key`),
  KEY `idx_trade_command_date_status` (`trade_date`,`status`),
  KEY `idx_trade_command_stock_created` (`stock_code`,`created_at`),
  CONSTRAINT `chk_trade_command_quantity` CHECK ((`requested_quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@trade_event_outbox.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: trade_event_outbox
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `trade_event_outbox`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `trade_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `aggregate_type` varchar(64) NOT NULL,
  `aggregate_id` varchar(64) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `payload_json` json NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` datetime(3) DEFAULT NULL,
  `last_error` varchar(500) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_event_outbox_id` (`event_id`),
  KEY `idx_trade_event_outbox_delivery` (`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@trade_performance_snapshot.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: trade_performance_snapshot
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `trade_performance_snapshot`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `trade_performance_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `broker_code` varchar(32) NOT NULL,
  `account_id` varchar(64) NOT NULL,
  `as_of_date` date NOT NULL,
  `cumulative_profit` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `cumulative_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `daily_profit` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `daily_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `monthly_profit` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `monthly_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `total_trade_count` bigint NOT NULL DEFAULT '0',
  `closed_trade_count` bigint NOT NULL DEFAULT '0',
  `winning_trade_count` bigint NOT NULL DEFAULT '0',
  `losing_trade_count` bigint NOT NULL DEFAULT '0',
  `win_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `average_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `best_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `worst_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `ranking_json` longtext,
  `daily_returns_json` longtext,
  `monthly_returns_json` longtext,
  `generated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_performance_account_date` (`broker_code`,`account_id`,`as_of_date`),
  KEY `idx_trade_performance_latest` (`broker_code`,`account_id`,`as_of_date`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- stock_trading@trade_record.sql
-- MySQLShell dump 2.0.1  Distrib Ver 8.4.5 for Win64 on x86_64 - for MySQL 8.4.5 (MySQL Community Server (GPL)), for Win64 (x86_64)
--
-- Host: 117.72.216.67    Database: stock_trading    Table: trade_record
-- ------------------------------------------------------
-- Server version	8.0.43

--
-- Table structure for table `trade_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `trade_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `broker_code` varchar(32) NOT NULL,
  `account_id` varchar(64) NOT NULL,
  `stock_code` varchar(16) NOT NULL,
  `stock_name` varchar(64) DEFAULT NULL,
  `market` varchar(16) DEFAULT NULL,
  `buy_order_no` varchar(64) NOT NULL,
  `sell_order_nos` text,
  `status` varchar(16) NOT NULL,
  `buy_time` datetime(3) NOT NULL,
  `first_sell_time` datetime(3) DEFAULT NULL,
  `last_sell_time` datetime(3) DEFAULT NULL,
  `buy_quantity` int NOT NULL,
  `sold_quantity` int NOT NULL DEFAULT '0',
  `remaining_quantity` int NOT NULL,
  `buy_price` decimal(18,4) NOT NULL,
  `average_sell_price` decimal(18,4) DEFAULT NULL,
  `buy_amount` decimal(20,4) NOT NULL,
  `sold_cost_amount` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `sell_amount` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `buy_fee` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `allocated_buy_fee` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `sell_fee` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `gross_profit` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `net_profit` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `gross_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `holding_days` decimal(12,4) DEFAULT NULL,
  `daily_return_rate` decimal(18,8) NOT NULL DEFAULT '0.00000000',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_record_buy_order` (`broker_code`,`account_id`,`buy_time`,`buy_order_no`),
  KEY `idx_trade_record_account_buy_time` (`account_id`,`buy_time`),
  KEY `idx_trade_record_stock_status` (`stock_code`,`status`,`buy_time`)
) ENGINE=InnoDB AUTO_INCREMENT=90020 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

-- 当前固定任务目录的初始化数据。
INSERT INTO system_runtime_config (
    node_id, scheduler_enabled, data_collection_enabled, news_collection_enabled,
    startup_data_sync_enabled, startup_history_sync_enabled, startup_news_sync_enabled,
    model_training_enabled, updated_at
) VALUES (
    'stock-backend', TRUE, TRUE, TRUE, TRUE, FALSE, FALSE, TRUE, CURRENT_TIMESTAMP
);

INSERT INTO strategy_runtime_state (
    state_key, enabled, current_mode, disabled_indicators_json,
    last_switch_reason, config_version, auto_switch_enabled, updated_at
) VALUES (
    'strategy:state', TRUE, 'BALANCED', '[]', '初始化', '1', FALSE, CURRENT_TIMESTAMP(3)
);

INSERT INTO job_config (
    job_name, cron_expression, status, description, module, sync_mode, create_time, update_time
) VALUES
    ('stockListSync', '0 0 1 ? * SUN', 1, '每周同步证券 API 股票代码和名称', 'data', 'FULL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('dailyStockDataSync', '0 0 18 ? * MON-FRI', 1, '每日交易日线同步：先采集当前交易日，再递归向前回补最近三年缺失交易日', 'data', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('newsDailySync', '0 0 5 ? * MON-FRI', 1, '每日同步股票新闻和公告：以水位和已存在标识跳过重复数据', 'data', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('tradeCalendarMonthlySync', '0 10 4 1 * ?', 1, '每月回补交易日历缺口并维持未来一年滚动窗口', 'data', 'BACKFILL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('lstmOfflineTraining', '0 0 19 ? * MON-FRI', 1, '收盘后检查并更新缺失或过期的共享 LSTM 模型', 'model', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('dailyModelPrediction', '0 15 6 ? * MON-FRI', 1, '工作日开盘前使用最近完整日线生成全市场预测', 'model', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('modelPredictionEvaluation', '0 30 20 ? * *', 1, '每日回填所有待评估预测批次', 'model', 'BACKFILL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('stockSelection', '0 30 6 ? * MON-FRI', 1, '使用已训练模型与近期新闻情感生成交易候选', 'strategy', 'FULL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('realTradingAutoBuy', '0 35 9 ? * MON-FRI', 1, '执行当日真实模型候选买入检查，仍受真实写入门禁和风控约束', 'trading', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('realTradingT1Exit', '0 50 14 ? * MON-FRI', 1, '检查真实持仓止损、止盈和 T+1 退出', 'trading', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('broker-current-facts-sync', '0 0/5 9-15 ? * MON-FRI', 1, '同步券商账户、持仓、当日委托和成交只读事实', 'trading', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
-- 当前基线只保留统一同步状态和单表持仓快照。
CREATE TABLE IF NOT EXISTS `sync_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dataset` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_success_cursor` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_success_date` date DEFAULT NULL,
  `gap_start_date` date DEFAULT NULL,
  `gap_end_date` date DEFAULT NULL,
  `gap_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RESOLVED',
  `retry_count` int NOT NULL DEFAULT '0',
  `last_error` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sync_state_scope` (`dataset`,`scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `position_snapshot`
  DROP INDEX `uk_position_snapshot_id`,
  ADD COLUMN `stock_code` varchar(16) DEFAULT NULL AFTER `account_id`,
  ADD COLUMN `stock_name` varchar(64) DEFAULT NULL AFTER `stock_code`,
  ADD COLUMN `market` varchar(16) DEFAULT NULL AFTER `stock_name`,
  ADD COLUMN `shareholder_account` varchar(64) NOT NULL DEFAULT '' AFTER `market`,
  ADD COLUMN `total_quantity` int NOT NULL DEFAULT '0' AFTER `shareholder_account`,
  ADD COLUMN `available_quantity` int NOT NULL DEFAULT '0' AFTER `total_quantity`,
  ADD COLUMN `frozen_quantity` int NOT NULL DEFAULT '0' AFTER `available_quantity`,
  ADD COLUMN `average_cost` decimal(18,4) DEFAULT NULL AFTER `frozen_quantity`,
  ADD COLUMN `current_price` decimal(18,4) DEFAULT NULL AFTER `average_cost`,
  ADD COLUMN `market_value` decimal(20,4) DEFAULT NULL AFTER `current_price`,
  ADD COLUMN `first_buy_date` date DEFAULT NULL AFTER `market_value`,
  ADD COLUMN `last_buy_date` date DEFAULT NULL AFTER `first_buy_date`,
  ADD KEY `idx_position_snapshot_id_stock` (`snapshot_id`,`stock_code`);

DROP TABLE IF EXISTS `position_snapshot_item`;
DROP TABLE IF EXISTS `sync_checkpoint`;
DROP TABLE IF EXISTS `sync_gap`;
DROP TABLE IF EXISTS `trade_performance_snapshot`;
DROP TABLE IF EXISTS `execution_audit`;
DROP TABLE IF EXISTS `risk_decision`;
DROP TABLE IF EXISTS `trade_authorization`;
DROP TABLE IF EXISTS `trade_command`;
DROP TABLE IF EXISTS `trade_event_outbox`;
DROP TABLE IF EXISTS `sentiment_evaluation`;
DROP TABLE IF EXISTS `strategy_evaluation`;
SET FOREIGN_KEY_CHECKS = 1;
-- AI_GENERATE_END --
