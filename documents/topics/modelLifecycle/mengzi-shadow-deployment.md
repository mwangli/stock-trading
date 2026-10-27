---
id: mengzi-shadow-deployment
title: Mengzi 影子部署
sidebar_position: 3
owner: model
type: design
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /topics/modelLifecycle/mengzi-shadow-deployment/
source_paths:
  - stock-server/stock-model/src/main/java/com/stock/model/service/SentimentShadowDeploymentService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/MengziModelRuntimeService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/SentimentModelRouterService.java
---

<DocMetadata />

# Mengzi 影子部署

:::warning 验证边界
第一阶段数据 Gate、模型推理和影子评估均未执行。
:::

### 目标与资源边界

Mengzi 与 FinBERT 读取同一批 `stock_news_event_facts`，并通过 `SentimentInputAssembler` 使用相同的标题优先、关键句和最多三块输入。FinBERT 保持生产基线，Mengzi 先只写影子预测；影子样本、覆盖率、稳定性和收益门槛全部通过后由确定性规则自动激活。运行故障允许自动回滚 FinBERT，Java 继续负责事实筛选、版本追踪、风险门禁和最终交易执行。

官方 `Langboat/mengzi-bert-base-fin` 是 `BertForMaskedLM` 基础编码器，不是三分类情感模型。基础制品使用 `encoder-artifact-v1` 独立保存；候选分类器必须采用冻结编码器加 `CLS 768 -> Linear 256 -> GELU -> Dropout 0.1 -> Linear 3`，并在 `sentiment-artifact-v1` 中记录 `FINE_TUNED_CLASSIFIER`、基础 revision、金标样本数、标签映射和校准参数。没有合格人工金标时，只允许校验编码器和开发训练原型，禁止配置为生产或影子分类制品。

FinBERT 当前缺陷包括上游约八千条分析师报告句子带来的领域偏差、私有评估集不可复核、负类召回率和 ECE 缺失、128 token 长度较短以及最大 Softmax 概率未校准。Mengzi 的主要风险是仅有金融编码器而没有本项目三分类头和金标质量证据。两者都必须使用人工金标评估集报告 Macro-F1、各类 Precision/Recall、ECE、弃权覆盖率、否定/反转和长文本分组结果；弱标注只能用于预训练或样本扩充，不能替代金标验收。

### 成对结果契约

输入仅包含 `TRADE_SIGNAL` 事件事实，使用 `canonicalEventId` 幂等。输出统一保存三类概率、原始和校准置信度、熵、概率间隔、弃权状态、分块数量和文本覆盖率。`RISK_ONLY` 不进入 Alpha 新闻特征，也不由 `TRADE_SIGNAL` 情感分数代理，继续由独立 Java 风险门禁消费。

### 影子候选与回滚门槛

- 影子期不少于 20 个交易日。
- 有效评估样本不少于配置阈值。
- 按股票和目标交易日聚合事件，以目标日开盘到下一交易日开盘收益评估。
- Rank IC 必须按每日横截面计算后再跨日平均，不能把所有事件混在一个 Spearman 样本中。
- Rank IC、方向准确率和扣成本 Top 10% 收益不低于 FinBERT 同事件基线。
- 推理失败率、输出稳定性、非弃权覆盖率和长文本覆盖率达到门槛。

任一门槛未满足时保持 `SHADOW`；全部通过后自动切换为 `ACTIVE` 并记录决策原因和激活时间。运行故障或滚动指标跌破门槛时自动恢复 FinBERT。状态迁移只改变情感模型路由，不绕过交易权限、资金、持仓和券商执行。

影子任务通过 `JobConfig + JobSchedulerService` 注册，默认任务名 `sentimentMengziShadow`，默认 Cron 为工作日 `01:00`。`MENGZI_SHADOW_ENABLED` 与任务状态必须同时开启，单批按配置数量幂等处理，且禁止在交易时段执行大批量推理。

当前源码和本地制品最多证明统一输入输出、官方编码器校验、三分类头结构、日级快照契约、后验评估、自动激活门槛和自动回滚路径存在。没有人工金标训练、校准和完整影子期证据前，交付状态最高为“编码器制品完整、分类器待训练”，不能描述为 Mengzi 预测效果通过。
