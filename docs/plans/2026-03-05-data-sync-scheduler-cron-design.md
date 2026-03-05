## 目标与范围
- 调整 DataSyncScheduler 中两个定时任务的 cron 触发时间：
  - syncDailyStockData：交易日 18:00 触发
  - syncStockListDaily：每周日 01:00 触发
- 仅涉及定时调度表达式与相关注释/日志（若需要保持描述一致）。
- 不修改业务逻辑与其他模块。

## 现状简述
- syncDailyStockData 当前在交易日 16:00 触发。
- syncStockListDaily 当前为每日 01:00 触发。

## 方案选型
**推荐方案：最小改动（A）**
- 仅修改两处 @Scheduled 的 cron 字符串。
- 同步修正方法注释/日志的时间描述（若发现不一致）。

备选方案：
- 将 cron 抽取到配置文件（application.yml）并通过配置注入。
  - 不推荐：超出当前需求范围，改动更大。

## 设计细节
- syncDailyStockData
  - cron：`0 0 18 * * MON-FRI`
  - 描述：交易日 18:00 执行，确保收盘数据完整。
- syncStockListDaily
  - cron：`0 0 1 ? * SUN`
  - 描述：每周日 01:00 执行，降低频率。

## 数据流与错误处理
- 数据流与现状一致，不新增依赖或数据变更路径。
- 错误处理与日志行为保持原样。

## 验证与风险
- 验证：
  - 对修改文件运行 lsp_diagnostics 确保无诊断问题。
  - 按模块规范仅编译 data-collector：`mvn compile -pl data-collector`（如构建适用）。
- 风险：
  - cron 表达式格式不匹配导致任务不触发。
  - 规避：保持 Quartz 6 段格式，与现有 @Scheduled 使用一致。
