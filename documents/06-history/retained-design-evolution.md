---
id: history-design-evolution
title: 历史设计演进与保留结论
sidebar_position: 10
owner: history
type: history
status: HISTORICAL
last_verified: 2026-11-06
slug: /history/design-evolution/
source_paths:
  - stock-trading@README.md
  - stock-trading@src/stock_trading
  - stock-trading@.github/config/stock-trading.cron
  - stock-server/src/main/java/com/stock/console
  - stock-client/src
---

# 历史设计演进与保留结论

## 文档处理原则

历史版本覆盖 Java 多模块交易、MySQL/MongoDB、Redis、Quartz、多个深度学习模型、新闻情绪、Agent/RAG、纯 Python 重建和只读 Web 控制台。当前文档只保留两类内容：仍在源码中运行的实现，以及虽已删除但仍能解释当前决策的原则。

## 架构收口

| 历史方向 | 当前结论 |
|---|---|
| Java 四层与七模块 Maven 交易后台 | 交易实现已删除；单一职责、统一响应和审计原则保留 |
| MySQL、MongoDB、Redis Pub/Sub | 不再是主链；当前使用 Parquet、文件制品和 SQLite |
| Quartz 与应用内任务控制 | 已删除；当前使用独立 CLI、Cron、`scheduler.sh` 和 `flock` |
| LSTM、MASTER、DJL、PyTorch、Mengzi | 不再运行；当前基线为 Alpha158 + LightGBM Regression |
| Top5 预测制品 | 已被完整横截面排名替代；Top5 只用于买入入口和摘要 |
| 提交后不等待的直记策略 | 已被轮询、撤单确认、分阶段重报价和恢复逻辑替代 |
| 分散的买卖、同步、绩效和评估命令 | 已收敛为 `stock-trading`、`stock-order` 和 `stock-review` |
| 只依赖登录 Shell 的生产变量 | 已改为项目根目录按文件名加载 `*.env` |
| `prediction` 状态表 | Schema v4 无损迁移为语义更明确的 `prediction_rank` |
| 独立 Agent/RAG/Memory 平台 | 已删除；控制台仅保留进程内会话、文档检索和只读工具 |
| 单一响应式前端 | 已改为 PC 与移动端独立组件树 |

## 仍然有效的业务原则

- 原始行情不可静默覆盖，缺失、真实空值和数据源错误必须区分。
- 训练、验证、样本外回测和真实交易收益不能混用。
- 模型制品、预测日期、股票、分数、排名和模型版本必须可追溯。
- 模型只排序，交易约束由确定性业务代码执行。
- 委托尝试、券商事实和已实现交易必须分层记录。
- 未确认的网络结果必须进入可解释状态，不能默认成功。
- 真实收益只使用成交闭环，并区分毛收益与净收益。
- 控制台和 Agent 只能观察或解释，不能成为第二套交易权威。

## 近期主线演进

纯 Python 重建后，项目继续完成了以下关键改造：

1. 预测由 Top5 扩展为完整、连续、唯一的横截面排名。
2. 交易策略收敛为 Rank5 买入、Rank15 持仓轮换。
3. 委托执行增加 15 分钟窗口、最多 12 次尝试、三阶段定价、撤单终态确认和重启恢复。
4. 低内存环境增加 Alpha158 分批特征、磁盘检查点和内存映射训练。
5. 数据采集改为按缺失清单补齐，并拒绝把异常空股票快照覆盖到有效文件。
6. 数据质量阈值统一为默认 0.8，并在采集、准备、训练和排名阶段复用。
7. 交易入口统一为先卖后买，收盘后入口统一为订单同步/结算和收益/模型复盘。
8. 生产发布收敛为 Wheel、Python 3.12、`config/` 调度文件、Cron 和 logrotate。
9. 配置改为按文件名加载项目根目录 `*.env`，缺失仅告警，功能使用时再校验。
10. 新增 `stock-sync`，显式区分行情、Qlib、模型和只允许下拉的生产状态库。
11. SQLite 增加账户快照，并把预测表迁移为 `prediction_rank`；Java 只读查询同步升级。

## 明确删除的文档方向

- 旧模块编号、旧类清单和已删除 API 的逐项说明。
- 把新闻、情绪模型、向量库或长期记忆描述为当前依赖。
- 把 Java Agent 计划或审批描述为真实交易入口。
- 把旧 `var/`、MySQL/MongoDB 或独立模型服务描述为生产数据源。
- 把已删除的 `stock-sell`、`stock-buy`、`stock-broker`、`stock-perform`、`stock-evaluate` 描述为当前入口。
- 把旧阶段性运行结果描述为当前持续有效的生产证明。

删除这些内容不是否定历史工作，而是避免使用失效实现指导当前开发。需要追溯细节时应查看对应 Git 提交，不在现行文档中复制过时接口。
