// AI_GENERATE_START -
package com.stock.model.algorithm;

import ai.djl.ndarray.NDArray;

import java.util.EnumMap;
import java.util.Map;

/**
 * LSTM 模型批量输出的通用封装。
 *
 * 根据模型元数据选择输出结构，将不同代际、不同输出维度的扁平张量统一为按语义字段访问的批量结果，
 * 避免推理服务直接依赖固定列偏移。新增模型输出结构时只需扩展本类中的结构声明。
 *
 * @author mwangli
 * @since 2026-10-28
 */
public final class LstmModelOutputBatch {

    private static final String LEGACY_V3_MODEL_VERSION = "v3-embedding-multitask";
    private static final String LEGACY_V4_MODEL_VERSION = "v4-balanced-direction";
    private static final String RANKING_MODEL_VERSION = "ranking-feature-metadata-v1";

    private final OutputSchema schema;
    private final int sampleCount;
    private final float[] values;

    private LstmModelOutputBatch(OutputSchema schema, int sampleCount, float[] values) {
        this.schema = schema;
        this.sampleCount = sampleCount;
        this.values = values;
    }

    /**
     * 根据模型元数据和实际张量创建批量输出封装。
     *
     * @param output 模型前向传播输出
     * @param modelVersion 模型结构版本
     * @param outputSchemaVersion 输出结构版本；历史模型可为空
     * @param declaredOutputSize 元数据声明的单样本输出维度
     * @param expectedSampleCount 预期样本数量
     * @return 已完成结构和维度校验的批量输出
     * @throws IllegalStateException 输出结构未知、声明维度不一致或实际输出数量不匹配时抛出
     */
    public static LstmModelOutputBatch from(
            NDArray output,
            String modelVersion,
            String outputSchemaVersion,
            int declaredOutputSize,
            int expectedSampleCount) {
        if (output == null) {
            throw new IllegalStateException("模型前向输出不能为空");
        }
        if (expectedSampleCount <= 0) {
            throw new IllegalStateException("模型前向输出的预期样本数必须大于 0");
        }
        OutputSchema schema = OutputSchema.resolve(modelVersion, outputSchemaVersion);
        if (declaredOutputSize != schema.outputSize()) {
            throw new IllegalStateException("模型输出维度声明与输出结构不匹配: schema="
                    + schema.schemaVersion() + ", declared=" + declaredOutputSize
                    + ", expected=" + schema.outputSize());
        }
        float[] values = output.toFloatArray();
        int expectedValueCount = expectedSampleCount * schema.outputSize();
        if (values.length != expectedValueCount) {
            throw new IllegalStateException("模型批量输出数量不匹配: schema=" + schema.schemaVersion()
                    + ", expected=" + expectedValueCount + ", actual=" + values.length);
        }
        return new LstmModelOutputBatch(schema, expectedSampleCount, values);
    }

    /**
     * 返回批量输出包含的样本数量。
     *
     * @return 样本数量
     */
    public int sampleCount() {
        return sampleCount;
    }

    /**
     * 判断当前输出结构是否包含指定语义字段。
     *
     * @param field 输出语义字段
     * @return 包含该字段时返回 true
     */
    public boolean hasField(OutputField field) {
        return schema.column(field) != null;
    }

    /**
     * 读取指定样本的必填输出字段。
     *
     * @param sampleIndex 样本下标
     * @param field 输出语义字段
     * @return 模型原始输出值
     * @throws IllegalStateException 当前结构不包含指定字段时抛出
     * @throws IndexOutOfBoundsException 样本下标越界时抛出
     */
    public double requiredValue(int sampleIndex, OutputField field) {
        Integer column = schema.column(field);
        if (column == null) {
            throw new IllegalStateException("模型输出结构缺少字段: schema="
                    + schema.schemaVersion() + ", field=" + field);
        }
        return valueAt(sampleIndex, column);
    }

    /**
     * 读取指定样本的可选输出字段。
     *
     * @param sampleIndex 样本下标
     * @param field 输出语义字段
     * @return 模型原始输出值；当前结构不包含该字段时返回 null
     * @throws IndexOutOfBoundsException 样本下标越界时抛出
     */
    public Double optionalValue(int sampleIndex, OutputField field) {
        Integer column = schema.column(field);
        return column == null ? null : valueAt(sampleIndex, column);
    }

    /**
     * 校验指定样本的全部输出均为有限数值，且概率字段位于 0 到 1 区间。
     *
     * @param sampleIndex 样本下标
     * @return 输出满足数值约束时返回 true
     * @throws IndexOutOfBoundsException 样本下标越界时抛出
     */
    public boolean isValidSample(int sampleIndex) {
        checkSampleIndex(sampleIndex);
        int offset = sampleIndex * schema.outputSize();
        for (int column = 0; column < schema.outputSize(); column++) {
            float value = values[offset + column];
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        for (OutputField field : OutputField.values()) {
            Integer column = schema.column(field);
            if (column != null && field.probability()) {
                float value = values[offset + column];
                if (value < 0F || value > 1F) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 校验批量输出中的全部样本。
     *
     * @throws IllegalStateException 任一样本包含非有限值或概率越界时抛出
     */
    public void validateAll() {
        for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
            if (!isValidSample(sampleIndex)) {
                throw new IllegalStateException("模型输出数值不满足结构约束: schema="
                        + schema.schemaVersion() + ", sampleIndex=" + sampleIndex);
            }
        }
    }

    private double valueAt(int sampleIndex, int column) {
        checkSampleIndex(sampleIndex);
        return values[sampleIndex * schema.outputSize() + column];
    }

    private void checkSampleIndex(int sampleIndex) {
        if (sampleIndex < 0 || sampleIndex >= sampleCount) {
            throw new IndexOutOfBoundsException("模型输出样本下标越界: " + sampleIndex);
        }
    }

    /**
     * 统一模型输出语义字段。
     */
    public enum OutputField {
        /** 排序分数。 */
        RANK_SCORE(false),
        /** 缩放后的预测收益或预期超额收益。 */
        SCALED_RETURN(false),
        /** 上涨概率。 */
        UP_PROBABILITY(true),
        /** 下行风险或下行分位数。 */
        DOWNSIDE_RISK(true);

        private final boolean probability;

        OutputField(boolean probability) {
            this.probability = probability;
        }

        private boolean probability() {
            return probability;
        }
    }

    private enum OutputSchema {
        LEGACY_MULTITASK(
                "return-probability-downside-v1",
                3,
                Map.of(
                        OutputField.SCALED_RETURN, 0,
                        OutputField.UP_PROBABILITY, 1,
                        OutputField.DOWNSIDE_RISK, 2)),
        RANKING_MULTITASK(
                "ranking-return-probability-downside-v1",
                4,
                Map.of(
                        OutputField.RANK_SCORE, 0,
                        OutputField.SCALED_RETURN, 1,
                        OutputField.UP_PROBABILITY, 2,
                        OutputField.DOWNSIDE_RISK, 3));

        private final String schemaVersion;
        private final int outputSize;
        private final Map<OutputField, Integer> columns;

        OutputSchema(String schemaVersion, int outputSize, Map<OutputField, Integer> columns) {
            this.schemaVersion = schemaVersion;
            this.outputSize = outputSize;
            this.columns = new EnumMap<>(columns);
        }

        private static OutputSchema resolve(String modelVersion, String outputSchemaVersion) {
            if (outputSchemaVersion != null && !outputSchemaVersion.isBlank()) {
                for (OutputSchema schema : values()) {
                    if (schema.schemaVersion.equals(outputSchemaVersion.trim())) {
                        return schema;
                    }
                }
                throw new IllegalStateException("不支持的模型输出结构版本: " + outputSchemaVersion);
            }
            if (LEGACY_V3_MODEL_VERSION.equals(modelVersion) || LEGACY_V4_MODEL_VERSION.equals(modelVersion)) {
                return LEGACY_MULTITASK;
            }
            if (RANKING_MODEL_VERSION.equals(modelVersion)) {
                return RANKING_MULTITASK;
            }
            throw new IllegalStateException("模型缺少可识别的输出结构: modelVersion=" + modelVersion);
        }

        private String schemaVersion() {
            return schemaVersion;
        }

        private int outputSize() {
            return outputSize;
        }

        private Integer column(OutputField field) {
            return columns.get(field);
        }
    }
}
// AI_GENERATE_END -