// AI_GENERATE_START-------------------
package com.stock.model.config;

import com.stock.model.algorithm.LstmArchitectureType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LSTM 模型结构和训练配置。
 * 配置用于创建新版本；发布时会将完整训练配置固化到 MongoDB 版本文档。
 *
 * @author mwangli
 * @since 2026-10-29
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.lstm")
public class LstmTrainingConfig {

    /** 模型逻辑名称。 */
    private String modelName = "global-shared-lstm";

    /**
     * 输入序列长度（时间步）
     */
    private int sequenceLength = 60;

    /**
     * 隐藏层大小
     */
    private int hiddenSize = 50;

    /**
     * LSTM 层数
     */
    private int numLayers = 2;

    /**
     * 训练轮次
     */
    private int epochs = 100;

    /**
     * 批次大小
     */
    private int batchSize = 32;

    /**
     * 学习率
     */
    private double learningRate = 0.001;

    /**
     * Dropout 率
     */
    private double dropout = 0.2;

    /**
     * 训练集比例
     */
    private double trainRatio = 0.8;

    /**
     * 训练集与验证集之间隔离的样本数，避免重叠窗口造成近邻泄漏。
     */
    private int validationGap = 2;

    /**
     * 下一交易日收益率标签缩放值，0.1 表示百分之十收益映射为 1。
     */
    private double targetReturnScale = 0.1D;

    /**
     * 单次共享训练允许保留的最大面板样本数量。
     */
    private int maxPanelSamples = 100000;

    /**
     * 单只股票允许保留的最大均匀时间窗口数量。
     */
    private int maxSamplesPerStock = 80;

    /** `V6` 候选模型架构。 */
    private LstmArchitectureType architectureType = LstmArchitectureType.RANKING_LSTM;

    /** 两个候选架构共用的 DJL 随机种子。 */
    private int randomSeed = 20261023;

    /** 统一行情和关联事实的数据来源。 */
    private String dataSource = "TUSHARE_DAILY";

    /** 统一行情和关联事实的数据契约版本。 */
    private String dataSourceVersion = "TUSHARE_DAILY_V1";

    /** 市场基准参考序列代码。 */
    private String benchmarkSeriesCode = "MARKET_EQUAL_WEIGHT";

    /** 市场类别 Embedding 最大字典容量，索引 0 保留给未知市场。 */
    private int marketEmbeddingBuckets = 16;

    /** 市场类别 Embedding 维度。 */
    private int marketEmbeddingSize = 4;

    /**
     * 股票 Embedding 哈希桶数量，索引 0 保留给未知股票。
     */
    private int stockEmbeddingBuckets = 8192;

    /**
     * 股票 Embedding 维度。
     */
    private int stockEmbeddingSize = 16;

    /**
     * 行业 Embedding 哈希桶数量，索引 0 保留给未知行业。
     */
    private int industryEmbeddingBuckets = 512;

    /**
     * 行业 Embedding 维度。
     */
    private int industryEmbeddingSize = 8;

    /**
     * 行业或风格分组 Head 数量。
     */
    private int groupHeadCount = 8;

    /**
     * 是否启用个股 Residual Head。
     */
    private boolean residualHeadEnabled = false;

    /**
     * 分组 Head 对共享输出的校正缩放系数。
     */
    private double groupHeadScale = 0.1D;

    /**
     * 个股 Residual Head 对共享输出的校正缩放系数。
     */
    private double residualHeadScale = 0.1D;

    /**
     * 收益率任务损失权重。
     */
    private double returnLossWeight = 0.5D;

    /**
     * 方向概率任务损失权重。
     */
    private double directionLossWeight = 0.3D;

    /**
     * 下行风险任务损失权重。
     */
    private double downsideLossWeight = 0.2D;

    /** 横截面排序损失权重。 */
    private double rankingLossWeight = 0.45D;

    /** 超额收益回归损失权重。 */
    private double excessReturnLossWeight = 0.25D;

    /** 成本感知方向损失权重。 */
    private double costAwareDirectionLossWeight = 0.15D;

    /** 下行分位损失权重。 */
    private double downsideQuantileLossWeight = 0.10D;

    /** 收益和方向输出一致性损失权重。 */
    private double consistencyLossWeight = 0.05D;

    /** 双边交易成本和滑点阈值，0.002 表示 0.2%。 */
    private double costThreshold = 0.002D;

    /** 中性方向样本参与方向损失的权重。 */
    private double neutralDirectionWeight = 0.25D;

    /** Walk-Forward 训练窗口年数。 */
    private int trainingWindowYears = 3;

    /** Walk-Forward 验证窗口月数。 */
    private int validationWindowMonths = 6;

    /** Walk-Forward 独立测试窗口月数。 */
    private int testWindowMonths = 3;

    /** Walk-Forward 滚动步长月数。 */
    private int walkForwardStepMonths = 3;

    /** 标签边界清除交易日数。 */
    private int purgeTradingDays = 2;

    /** 验证和测试边界隔离交易日数。 */
    private int embargoTradingDays = 20;

    /** 严格窗口重叠敏感性检查隔离交易日数。 */
    private int strictIsolationTradingDays = 60;

    /**
     * 是否启用早停
     */
    private boolean earlyStopping = true;

    /**
     * 早停耐心值
     */
    private int patience = 10;

    /**
     * 最小 delta（早停判断）
     */
    private double minDelta = 0.0001;

}
// AI_GENERATE_END-------------------
