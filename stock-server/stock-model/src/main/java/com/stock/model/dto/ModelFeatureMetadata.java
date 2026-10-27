// AI_GENERATE_START --
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模型不可变特征元数据。
 * 记录模型训练和推理实际使用的特征名称、顺序、维度及关联数据契约。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelFeatureMetadata {

    /** 模型代际，如 V6.1 或 V6.2。 */
    private String modelGeneration;

    /** 模型业务版本，如 v6.1-price-ranking-18d。 */
    private String modelVersion;

    /** 完整特征契约版本。 */
    private String featureVersion;

    /** 新闻特征版本；不使用新闻时为空。 */
    private String newsFeatureVersion;

    /** 按输入顺序排列的行情连续特征名称。 */
    private List<String> marketFeatures;

    /** 按输入顺序排列的新闻连续特征名称。 */
    private List<String> newsFeatures;

    /** 按输入顺序排列的类别索引特征名称。 */
    private List<String> categoryFeatures;

    /** 标签契约版本。 */
    private String labelVersion;

    /** 输出结构版本。 */
    private String outputSchemaVersion;

    /** 时间切分策略版本。 */
    private String splitPolicyVersion;

    /** 交易成本模型版本。 */
    private String costModelVersion;

    /** 价格连续化口径。 */
    private String priceBasis;

    /** 市场基准构造口径。 */
    private String benchmarkType;

    /** 是否启用新闻特征。 */
    private boolean newsFeaturesEnabled;

    /**
     * 返回行情连续特征数量。
     *
     * @return 行情连续特征数量
     */
    public int marketFeatureCount() {
        return marketFeatures == null ? 0 : marketFeatures.size();
    }

    /**
     * 返回新闻连续特征数量。
     *
     * @return 新闻连续特征数量
     */
    public int newsFeatureCount() {
        return newsFeatures == null ? 0 : newsFeatures.size();
    }

    /**
     * 返回类别索引特征数量。
     *
     * @return 类别索引特征数量
     */
    public int categoryFeatureCount() {
        return categoryFeatures == null ? 0 : categoryFeatures.size();
    }

    /**
     * 返回连续特征总数。
     *
     * @return 连续特征总数
     */
    public int continuousFeatureCount() {
        return marketFeatureCount() + newsFeatureCount();
    }

    /**
     * 返回模型输入总维度。
     *
     * @return 模型输入总维度
     */
    public int inputSize() {
        return continuousFeatureCount() + categoryFeatureCount();
    }

    /**
     * 校验特征元数据内部一致性。
     *
     * @throws IllegalStateException 必填版本、特征顺序或新闻开关不一致时抛出
     */
    public void validate() {
        requireText(modelGeneration, "模型代际不能为空");
        requireText(modelVersion, "模型版本不能为空");
        requireText(featureVersion, "特征版本不能为空");
        requireText(labelVersion, "标签版本不能为空");
        requireText(outputSchemaVersion, "输出结构版本不能为空");
        requireText(splitPolicyVersion, "切分策略版本不能为空");
        requireText(costModelVersion, "成本模型版本不能为空");
        requireNames(marketFeatures, "行情特征不能为空");
        requireNames(categoryFeatures, "类别特征不能为空");
        List<String> safeNewsFeatures = newsFeatures == null ? List.of() : newsFeatures;
        if (newsFeaturesEnabled != !safeNewsFeatures.isEmpty()) {
            throw new IllegalStateException("新闻特征开关与新闻特征列表不一致");
        }
        if (newsFeaturesEnabled) {
            requireText(newsFeatureVersion, "启用新闻特征时新闻版本不能为空");
        }
        long distinctCount = java.util.stream.Stream.of(
                        marketFeatures, safeNewsFeatures, categoryFeatures)
                .flatMap(List::stream)
                .distinct()
                .count();
        if (distinctCount != inputSize()) {
            throw new IllegalStateException("模型特征名称存在重复");
        }
    }

    private void requireNames(List<String> values, String message) {
        if (values == null || values.isEmpty()
                || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException(message);
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
// AI_GENERATE_END --
