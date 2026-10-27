// AI_GENERATE_START --
package com.stock.model.algorithm;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.AbstractBlock;
import ai.djl.nn.Activation;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.norm.LayerNorm;
import ai.djl.training.ParameterStore;
import ai.djl.training.initializer.XavierInitializer;
import ai.djl.util.PairList;

/**
 * 市场状态引导的横截面股票 Transformer。
 *
 * 先对单股时序特征进行投影和时间注意力，再在同一目标交易日 Batch 内计算股票间关系，
 * 最后由市场状态门控关系表示并输出与 Ranking LSTM 一致的四任务结果。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public class MasterTransformerModel extends AbstractBlock {

    /** 固定输出数量，与 Ranking LSTM 保持一致。 */
    public static final int OUTPUT_SIZE = 4;

    private static final int CATEGORY_COLUMN_COUNT = 3;

    private final int inputSize;
    private final int continuousFeatureCount;
    private final int hiddenSize;
    private final int sequenceLength;
    private final SequentialBlock featureProjection;
    private final SequentialBlock temporalAttention;
    private final Linear queryProjection;
    private final Linear keyProjection;
    private final Linear valueProjection;
    private final Linear marketGateProjection;
    private final SequentialBlock outputHead;

    /**
     * 创建 MASTER 候选模型。
     *
     * @param inputSize 每个时间步的总输入维度，末尾三列为类别索引
     * @param hiddenSize 隐藏表示维度
     * @param sequenceLength 输入交易日序列长度
     */
    public MasterTransformerModel(int inputSize, int hiddenSize, int sequenceLength) {
        if (inputSize <= CATEGORY_COLUMN_COUNT || hiddenSize <= 0 || sequenceLength <= 0) {
            throw new IllegalArgumentException("MASTER 模型输入维度、隐藏维度和序列长度必须有效");
        }
        this.inputSize = inputSize;
        this.continuousFeatureCount = inputSize - CATEGORY_COLUMN_COUNT;
        this.hiddenSize = hiddenSize;
        this.sequenceLength = sequenceLength;

        featureProjection = new SequentialBlock()
                .add(Linear.builder().setUnits(hiddenSize).build())
                .add(Activation::relu)
                .add(LayerNorm.builder().build());
        temporalAttention = new SequentialBlock()
                .add(Linear.builder().setUnits(Math.max(8, hiddenSize / 2)).build())
                .add(Activation::tanh)
                .add(Linear.builder().setUnits(1).build());
        queryProjection = Linear.builder().setUnits(hiddenSize).build();
        keyProjection = Linear.builder().setUnits(hiddenSize).build();
        valueProjection = Linear.builder().setUnits(hiddenSize).build();
        marketGateProjection = Linear.builder().setUnits(hiddenSize).build();
        outputHead = new SequentialBlock()
                .add(Linear.builder().setUnits(Math.max(16, hiddenSize)).build())
                .add(Activation::relu)
                .add(Linear.builder().setUnits(OUTPUT_SIZE).build());

        addChildBlock("feature_projection", featureProjection);
        addChildBlock("temporal_attention", temporalAttention);
        addChildBlock("query_projection", queryProjection);
        addChildBlock("key_projection", keyProjection);
        addChildBlock("value_projection", valueProjection);
        addChildBlock("market_gate_projection", marketGateProjection);
        addChildBlock("output_head", outputHead);
        setInitializer(new XavierInitializer(), parameter ->
                parameter.getType() == ai.djl.nn.Parameter.Type.WEIGHT);
    }

    /**
     * 执行 MASTER 前向传播。
     *
     * @param parameterStore 参数存储
     * @param inputs 展平后的同日横截面时序输入
     * @param training 是否处于训练阶段
     * @param params 扩展参数
     * @return 四任务输出
     */
    @Override
    protected NDList forwardInternal(
            ParameterStore parameterStore, NDList inputs, boolean training,
            PairList<String, Object> params) {
        NDArray flattened = inputs.singletonOrThrow();
        long batchSize = flattened.getShape().get(0);
        NDArray sequence = flattened.reshape(batchSize, sequenceLength, inputSize);
        NDArray continuous = sequence.get(new NDIndex(":, :, 0:" + continuousFeatureCount));
        NDArray encoded = featureProjection.forward(
                parameterStore, new NDList(continuous), training, params).singletonOrThrow();
        NDArray timeScores = temporalAttention.forward(
                parameterStore, new NDList(encoded), training, params).singletonOrThrow();
        NDArray temporalRepresentation = encoded.mul(timeScores.softmax(1)).sum(new int[]{1});

        NDArray query = queryProjection.forward(
                parameterStore, new NDList(temporalRepresentation), training, params).singletonOrThrow();
        NDArray key = keyProjection.forward(
                parameterStore, new NDList(temporalRepresentation), training, params).singletonOrThrow();
        NDArray value = valueProjection.forward(
                parameterStore, new NDList(temporalRepresentation), training, params).singletonOrThrow();
        NDArray relationScores = query.dot(key.transpose()).div(Math.sqrt(hiddenSize)).softmax(1);
        NDArray relationRepresentation = relationScores.dot(value);

        NDArray marketState = temporalRepresentation.mean(new int[]{0}).expandDims(0);
        NDArray marketGate = Activation.sigmoid(marketGateProjection.forward(
                parameterStore, new NDList(marketState), training, params).singletonOrThrow());
        NDArray fused = temporalRepresentation.add(relationRepresentation.mul(marketGate));
        NDArray raw = outputHead.forward(
                parameterStore, new NDList(fused), training, params).singletonOrThrow();

        NDArray rankScore = raw.get(new NDIndex(":, 0")).expandDims(1);
        NDArray expectedExcessReturn = Activation.tanh(raw.get(new NDIndex(":, 1"))).expandDims(1);
        NDArray upProbability = Activation.sigmoid(raw.get(new NDIndex(":, 2"))).expandDims(1);
        NDArray downsideQuantile = Activation.sigmoid(raw.get(new NDIndex(":, 3"))).expandDims(1);
        return new NDList(rankScore.concat(expectedExcessReturn, 1)
                .concat(upProbability, 1).concat(downsideQuantile, 1));
    }

    /**
     * 初始化 MASTER 子块。
     *
     * @param manager NDArray 管理器
     * @param dataType 数据类型
     * @param inputShapes 输入形状
     */
    @Override
    protected void initializeChildBlocks(
            ai.djl.ndarray.NDManager manager, DataType dataType, Shape... inputShapes) {
        long batchSize = inputShapes[0].get(0);
        Shape sequenceShape = new Shape(batchSize, sequenceLength, continuousFeatureCount);
        Shape encodedShape = new Shape(batchSize, sequenceLength, hiddenSize);
        Shape representationShape = new Shape(batchSize, hiddenSize);
        featureProjection.initialize(manager, dataType, sequenceShape);
        temporalAttention.initialize(manager, dataType, encodedShape);
        queryProjection.initialize(manager, dataType, representationShape);
        keyProjection.initialize(manager, dataType, representationShape);
        valueProjection.initialize(manager, dataType, representationShape);
        marketGateProjection.initialize(manager, dataType, new Shape(1, hiddenSize));
        outputHead.initialize(manager, dataType, representationShape);
    }

    /**
     * 返回 MASTER 输出形状。
     *
     * @param inputShapes 输入形状
     * @return batch x 4 输出形状
     */
    @Override
    public Shape[] getOutputShapes(Shape[] inputShapes) {
        return new Shape[]{new Shape(inputShapes[0].get(0), OUTPUT_SIZE)};
    }
}
// AI_GENERATE_END --
