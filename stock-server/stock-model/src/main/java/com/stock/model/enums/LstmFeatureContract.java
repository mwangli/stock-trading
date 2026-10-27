// AI_GENERATE_START --
package com.stock.model.enums;

import com.stock.model.dto.ModelFeatureMetadata;

import java.util.List;
import java.util.Locale;

/**
 * 可用于新训练任务的 Ranking LSTM 特征契约模板。
 * 模型生成后以持久化的 {@link ModelFeatureMetadata} 为运行时权威信息。
 *
 * @author mwangli
 * @since 2026-10-27
 */
public enum LstmFeatureContract {

    /** 不使用新闻的 18 维价格排序模型。 */
    V6_1_PRICE_18D(
            "V6.1", "v6.1-price-ranking-18d", "tushare-free-price-v6.1-18d", null, List.of()),

    /** 使用 8 维紧凑新闻特征的 26 维排序模型。 */
    V6_2_COMPACT_NEWS_26D(
            "V6.2", "v6.2-compact-news-ranking-26d", "tushare-free-compact-news-v6.2-26d",
            "temporal-news-v2-8d",
            List.of("signedImpact", "effectiveConfidence", "materiality", "novelty",
                    "stockSpecificity", "recency", "sourceQuality", "evidenceCoverage"));

    private static final List<String> MARKET_FEATURES = List.of(
            "overnightGapReturn", "intradayReturn", "intradayAmplitude", "closePosition",
            "relativeAmount20", "rsi14", "return5", "return20", "return60",
            "volatility5", "volatility20", "volatility60", "marketExcessReturn",
            "equalWeightMarketReturn20", "marketBreadth");
    private static final List<String> CATEGORY_FEATURES = List.of(
            "stockIndex", "industryIndex", "marketIndex");
    private static final String LABEL_VERSION = "open-open-market-neutral-cost-v1";
    private static final String OUTPUT_SCHEMA_VERSION = "ranking-return-probability-downside-v1";
    private static final String SPLIT_POLICY_VERSION = "global-walk-forward-purge-embargo-v1";
    private static final String COST_MODEL_VERSION = "round-trip-threshold-v1";
    private static final String PRICE_BASIS = "NORMALIZED_PRE_CLOSE_V1";
    private static final String BENCHMARK_TYPE = "MARKET_EQUAL_WEIGHT";

    private final String modelGeneration;
    private final String modelVersion;
    private final String featureVersion;
    private final String newsFeatureVersion;
    private final List<String> newsFeatures;

    LstmFeatureContract(String modelGeneration, String modelVersion, String featureVersion,
                        String newsFeatureVersion, List<String> newsFeatures) {
        this.modelGeneration = modelGeneration;
        this.modelVersion = modelVersion;
        this.featureVersion = featureVersion;
        this.newsFeatureVersion = newsFeatureVersion;
        this.newsFeatures = newsFeatures;
    }

    /**
     * 将训练模板转换为可持久化模型特征元数据。
     *
     * @return 模型特征元数据
     */
    public ModelFeatureMetadata metadata() {
        ModelFeatureMetadata metadata = ModelFeatureMetadata.builder()
                .modelGeneration(modelGeneration)
                .modelVersion(modelVersion)
                .featureVersion(featureVersion)
                .newsFeatureVersion(newsFeatureVersion)
                .marketFeatures(List.copyOf(MARKET_FEATURES))
                .newsFeatures(List.copyOf(newsFeatures))
                .categoryFeatures(List.copyOf(CATEGORY_FEATURES))
                .labelVersion(LABEL_VERSION)
                .outputSchemaVersion(OUTPUT_SCHEMA_VERSION)
                .splitPolicyVersion(SPLIT_POLICY_VERSION)
                .costModelVersion(COST_MODEL_VERSION)
                .priceBasis(PRICE_BASIS)
                .benchmarkType(BENCHMARK_TYPE)
                .newsFeaturesEnabled(!newsFeatures.isEmpty())
                .build();
        metadata.validate();
        return metadata;
    }

    /**
     * 解析外部训练请求中的特征契约名称。
     *
     * @param value 契约枚举名、模型代际或特征版本；为空时默认 V6.1
     * @return 匹配的特征契约
     */
    public static LstmFeatureContract resolve(String value) {
        if (value == null || value.isBlank()) {
            return V6_1_PRICE_18D;
        }
        String normalized = value.trim();
        for (LstmFeatureContract contract : values()) {
            if (contract.name().equalsIgnoreCase(normalized)
                    || contract.modelGeneration.equalsIgnoreCase(normalized)
                    || contract.featureVersion.equalsIgnoreCase(normalized)) {
                return contract;
            }
        }
        throw new IllegalArgumentException("featureContract 仅支持 "
                + V6_1_PRICE_18D.name().toLowerCase(Locale.ROOT) + " 或 "
                + V6_2_COMPACT_NEWS_26D.name().toLowerCase(Locale.ROOT));
    }
}
// AI_GENERATE_END --
