package com.stock.modelService.config;

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
     *
     * 支持绝对路径或相对路径：
     * - 绝对路径示例: D:/ai-stock-trading/backend/models/sentiment
     * - 相对路径示例: backend/models/sentiment 或 models/sentiment
     */
    private String modelPath = "models/sentiment";

    /**
     * 模型加载来源
     *
     * 可选值:
     * - local: 使用本地模型文件
     * - huggingface: 从 HuggingFace 下载并缓存模型
     */
    private String modelSource = "local";

    /**
     * 预训练模型名称（HuggingFace）
     * 
     * 中文通用模型选项：
     * - hfl/chinese-bert-wwm-ext (哈工大 BERT 中文，推荐快速启动)
     * - hfl/chinese-roberta-wwm-ext (哈工大 RoBERTa 中文，更准确)
     * - hfl/chinese-macbert-base (哈工大 MacBERT 中文)
     * 
     * 中文金融专用模型选项（推荐用于生产）：
     * - local/finbert-entropy (熵简科技 FinBERT，金融领域专用，需手动下载)
     * - local/finbert-tushare (基于 Tushare 财经新闻微调，需手动下载)
     * 
     * 推荐：
     * - 快速启动：hfl/chinese-bert-wwm-ext
     * - 生产环境：熵简 FinBERT（金融领域准确率更高）
     */
    private String pretrainedModel = "yiyanghkust/finbert-tone-chinese";

    /**
     * 最大序列长度
     */
    private int maxSequenceLength = 128;

    /**
     * 训练轮次
     */
    private int epochs = 5;

    /**
     * 批次大小
     */
    private int batchSize = 16;

    /**
     * 学习率
     */
    private double learningRate = 2e-5;

    /**
     * 训练集比例
     */
    private double trainRatio = 0.8;

    /**
     * 标签数量（正面、负面、中性）
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
     */
    /**
     * 情感标签映射（注意：需与模型输出对齐）
     * yiyanghkust/finbert-tone-chinese 标签定义: 0=Neutral, 1=Positive, 2=Negative
     */
    private String[] labels = {"neutral", "positive", "negative"};
}
