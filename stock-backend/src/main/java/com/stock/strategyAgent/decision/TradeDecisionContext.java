// AI_GENERATE_START --
package com.stock.strategyAgent.decision;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单个候选的不可变交易决策上下文。
 * 所有字段由 Java 领域服务固化，模型不能修改上下文、阈值或执行门禁。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionContext {

    /** 上下文唯一标识。 */
    String contextId;

    /** 上下文规范化内容的 SHA-256。 */
    String contextHash;

    /** 候选批次标识。 */
    String candidateBatchId;

    /** 预测批次标识。 */
    String predictionBatchId;

    /** 股票代码。 */
    String stockCode;

    /** 股票名称。 */
    String stockName;

    /** 模型版本。 */
    String modelVersion;

    /** 情感模型版本。 */
    String sentimentModelVersion;

    /** 策略配置版本。 */
    String strategyVersion;

    /** 模型输入基准交易日。 */
    LocalDate baseTradeDate;

    /** 目标交易日。 */
    LocalDate targetTradeDate;

    /** 允许进入上下文的信息截止时间。 */
    LocalDateTime informationCutoffAt;

    /** 候选批次内排名。 */
    int baselineRank;

    /** 候选综合基线分数，范围 0 至 1。 */
    double baselineScore;

    /** LSTM 候选分数，范围 0 至 1。 */
    double lstmScore;

    /** 新闻情感影响分数，范围由候选生成服务定义。 */
    double newsImpactScore;

    /** 候选生成服务固化的入选理由。 */
    String candidateReason;

    /** 候选是否通过当前确定性入选条件。 */
    boolean baselineEligible;

    /** 截止信息时间可见的最近收盘价，单位为元，可为空。 */
    BigDecimal closePrice;

    /** 最近收盘价对应交易日，可为空。 */
    LocalDate priceTradeDate;

    /** 当前策略最低入选分数。 */
    double minimumScore;

    /** 券商会话是否已认证。 */
    boolean brokerAuthenticated;

    /** 真实写入总门禁是否开启。 */
    boolean realWriteEnabled;

    /** 当前是否允许真实写入。 */
    boolean liveWriteAllowed;

    /** 账户快照是否可用。 */
    boolean accountSnapshotAvailable;

    /** 账户总资产，单位为元，可为空。 */
    BigDecimal totalAssets;

    /** 账户可用资金，单位为元，可为空。 */
    BigDecimal availableCash;

    /** 账户快照时间，可为空。 */
    LocalDateTime accountCapturedAt;

    /** 数据源状态和降级原因。 */
    List<String> dataQualityFlags;

    /** 模型允许引用的上下文证据编号。 */
    List<String> evidenceRefs;

    /** 上下文固化时间。 */
    LocalDateTime asOf;

    /** 上下文失效时间。 */
    LocalDateTime expiresAt;
}
// AI_GENERATE_END --
