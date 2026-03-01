package com.stock.models.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 情感分析模型训练配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.sentiment")
public class SentimentTrainingConfig {

    /**
     * 模型保存路径
     */
    private String modelPath = "models/sentiment-analysis";

    /**
     * 预训练模型名称（HuggingFace）
     * 
     * 中文通用模型选项：
     * - hfl/chinese-bert-wwm-ext (哈工大 BERT 中文，推荐快速启动)
     * - hfl/chinese-roberta-wwm-ext (哈工大 RoBERTa 中文，更准确)
     * - hfl/chinese-macbert-base (哈工大 MacBERT 中文)
     * 
     * FinBERT 模型选项：
     * - valuesimplex-ai-lab/FinBERT2-base (推荐使用！320亿Token中文金融语料)
     * - valuesimplex-ai-lab/FinBERT2-large (大模型，更高精度)
     * - IDEA-CCNL/Erlangshen-Roberta-110M-Sentiment (IDEA研究院亿帆系列)
     * - yiyanghkust/finbert-tone-chinese (基于FinBERT，金融情感中文模型)
     * 
     * 推荐使用 FinBERT2 模型，在金融情感分析任务上 F1-score 达 0.895
     */
    private String pretrainedModel = "valuesimplex-ai-lab/FinBERT2-base";

    /**
     * 最大序列长度
     * FinBERT2 推荐使用 256 或更长
     */
    private int maxSequenceLength = 256;

    /**
     * 训练轮次
     */
    private int epochs = 3; // 减少训练轮次，以适应金融领域FinBERT2更好的初始化

    /**
     * 批次大小
     */
    private int batchSize = 16;

    /**
     * 学习率
     * FinBERT2 作为金融预训练模型，使用较小的学习率微调
     */
    private double learningRate = 5e-6;

    /**
     * 训练集比例
     */
    private double trainRatio = 0.8;
    
    /**
     * 验证集比例
     */
    private double valRatio = 0.1;
    
    /**
     * 测试集比例
     */
    private double testRatio = 0.1;

    /**
     * 标签数量
     */
    private int numLabels = 3;

    /**
     * 是否下载预训练模型
     */
    private boolean downloadPretrained = true;

    /**
     * 模型缓存目录
     */
    private String cacheDir = "models/cache";

    /**
     * 情感标签映射（中文）
     * 为了兼容 FinBERT2，使用标准标签
     */
    private String[] labels = {"负面", "中性", "正面"};

    /**
     * 模型精度评估阈值
     * 当使用FinBERT2时，期望更高准确率
     */
    private double accuracyThreshold = 0.85;

    /**
     * 模型微调时是否使用早停机制
     */
    private boolean useEarlyStopping = true;

    /**
     * 早停 patience 值
     */
    private int earlyStoppingPatience = 3;

    /**
     * warmup 步骤比例
     * 用于金融模型微调
     */
    private double warmupRatio = 0.1;

    /**
     * 微调后模型最大保存数量
     */
    private int maxCheckpoints = 5;
}