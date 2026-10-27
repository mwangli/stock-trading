-- 股票交易系统 MySQL 初始化数据
-- 本文件只包含 DML，并在 V1__ddl.sql 完成后执行。

INSERT INTO job_config (
    job_name, cron_expression, status, description, module, sync_mode, create_time, update_time
) VALUES
    ('stockListSync', '0 0 1 ? * SUN', 1, '每周同步证券 API 股票代码和名称', 'data', 'FULL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('dailyStockDataSync', '0 0 18 ? * MON-FRI', 1, '每日仅同步当前已确认交易日的全市场日线增量', 'data', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('stockPriceHistoryBackfill', '0 0 3 ? * SAT', 1, '每周回补最近三年已确认交易日的股票历史日线缺口', 'data', 'BACKFILL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('newsDailySync', '0 0 5 ? * MON-FRI', 1, '每日同步股票新闻和公告', 'data', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('newsCollectionBackfill', '0 0 2 ? * *', 0, '分批回填历史新闻采集治理和事件事实', 'data', 'BACKFILL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('sentimentMengziShadow', '0 0 1 ? * MON-FRI', 0, '顺序执行 Mengzi 情感影子推理和自动路由评估', 'model', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('tradeCalendarMonthlySync', '0 10 4 1 * ?', 1, '每月回补交易日历缺口并维持未来一年滚动窗口', 'data', 'BACKFILL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('lstmOfflineTraining', '0 0 19 ? * MON-FRI', 1, '收盘后检查并更新缺失或过期的共享 LSTM 模型', 'model', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('dailyModelPrediction', '0 15 6 ? * MON-FRI', 1, '工作日开盘前使用最近完整日线生成全市场预测', 'model', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('masterShadowPrediction', '0 20 6 ? * MON-FRI', 1, '对生产预测批次执行 MASTER 影子预测和动态权重评估', 'model', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('realTradingMorningSell', '0 35 9 ? * MON-FRI', 1, '上午分阶段卖出全部可卖持仓', 'trading', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('realTradingAfternoonBuy', '0 5 13 ? * MON-FRI', 1, '下午模型前三名竞争唯一买入执行权', 'trading', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('broker-current-facts-sync', '0 0/5 9-15 ? * MON-FRI', 0, '同步券商账户、持仓、委托和成交事实', 'trading', 'INCREMENTAL', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    cron_expression = VALUES(cron_expression),
    status = VALUES(status),
    description = VALUES(description),
    module = VALUES(module),
    sync_mode = VALUES(sync_mode),
    update_time = CURRENT_TIMESTAMP(6);
