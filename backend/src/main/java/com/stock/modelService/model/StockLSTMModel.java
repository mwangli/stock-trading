// AI_GENERATE_START ----
package com.stock.modelService.model;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.ndarray.types.SparseFormat;
import ai.djl.nn.AbstractBlock;
import ai.djl.nn.Activation;
import ai.djl.nn.Parameter;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Embedding;
import ai.djl.nn.core.Linear;
import ai.djl.nn.norm.LayerNorm;
import ai.djl.nn.recurrent.LSTM;
import ai.djl.training.ParameterStore;
import ai.djl.training.initializer.XavierInitializer;
import ai.djl.util.PairList;
import lombok.extern.slf4j.Slf4j;

/**
 * 全市场共享多任务 LSTM 模型。
 * 使用连续量价特征提取时序表示，并融合股票、行业 Embedding、分组 Head 和个股 Residual Head，
 * 一次输出下一交易日收益率、上涨概率和下行风险。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Slf4j
public class StockLSTMModel extends AbstractBlock {

    /** 多任务输出数量：收益率、方向概率和下行风险。 */
    public static final int OUTPUT_SIZE = 3;

    private static final int STOCK_INDEX_COLUMN = 11;
    private static final int INDUSTRY_INDEX_COLUMN = 12;
    private static final int GROUP_INDEX_COLUMN = 13;
    private static final int CONTINUOUS_TAIL_START = 14;
    private static final int TEMPORAL_INPUT_SIZE = 13;

    private final SequentialBlock temporalBackbone;
    private final SequentialBlock sharedProjection;
    private final Linear sharedOutput;
    private final Parameter stockEmbedding;
    private final Parameter industryEmbedding;
    private final Parameter groupHeadWeights;
    private final Parameter groupHeadBias;
    private final Parameter stockResidual;
    private final int inputSize;
    private final int hiddenSize;
    private final int numLayers;
    private final float dropout;
    private final int sequenceLength;
    private final int stockEmbeddingBuckets;
    private final int stockEmbeddingSize;
    private final int industryEmbeddingBuckets;
    private final int industryEmbeddingSize;
    private final int groupHeadCount;
    private final boolean residualHeadEnabled;
    private final float groupHeadScale;
    private final float residualHeadScale;
    private final int representationSize;

    /**
     * 创建全市场共享多任务模型。
     *
     * @param inputSize 输入总特征维度
     * @param hiddenSize LSTM 隐藏层大小
     * @param numLayers LSTM 层数
     * @param dropout Dropout 比例
     * @param sequenceLength 输入序列长度
     * @param stockEmbeddingBuckets 股票 Embedding 哈希桶数量
     * @param stockEmbeddingSize 股票 Embedding 维度
     * @param industryEmbeddingBuckets 行业 Embedding 哈希桶数量
     * @param industryEmbeddingSize 行业 Embedding 维度
     * @param groupHeadCount 分组 Head 数量
     * @param residualHeadEnabled 是否启用个股 Residual Head
     * @param groupHeadScale 分组 Head 校正缩放系数
     * @param residualHeadScale 个股 Residual Head 校正缩放系数
     */
    public StockLSTMModel(int inputSize, int hiddenSize, int numLayers, float dropout,
                          int sequenceLength, int stockEmbeddingBuckets, int stockEmbeddingSize,
                          int industryEmbeddingBuckets, int industryEmbeddingSize, int groupHeadCount,
                          boolean residualHeadEnabled, float groupHeadScale, float residualHeadScale) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.numLayers = numLayers;
        this.dropout = dropout;
        this.sequenceLength = sequenceLength;
        this.stockEmbeddingBuckets = stockEmbeddingBuckets;
        this.stockEmbeddingSize = stockEmbeddingSize;
        this.industryEmbeddingBuckets = industryEmbeddingBuckets;
        this.industryEmbeddingSize = industryEmbeddingSize;
        this.groupHeadCount = groupHeadCount;
        this.residualHeadEnabled = residualHeadEnabled;
        this.groupHeadScale = groupHeadScale;
        this.residualHeadScale = residualHeadScale;
        this.representationSize = hiddenSize + stockEmbeddingSize + industryEmbeddingSize;

        temporalBackbone = new SequentialBlock();
        temporalBackbone.add(new LSTM.Builder()
                .setStateSize(hiddenSize)
                .setNumLayers(numLayers)
                .optDropRate(dropout)
                .optReturnState(false)
                .optBatchFirst(true)
                .optBidirectional(false)
                .build());
        temporalBackbone.add(LayerNorm.builder().build());
        temporalBackbone.addSingleton(input -> input.get(new NDIndex(":, -1, :")));

        sharedProjection = new SequentialBlock();
        sharedProjection.add(Linear.builder().setUnits(Math.max(8, hiddenSize / 2)).build());
        sharedProjection.add(Activation::relu);
        sharedOutput = Linear.builder().setUnits(OUTPUT_SIZE).build();

        stockEmbedding = createMatrixParameter(
                "stock_embedding", stockEmbeddingBuckets, stockEmbeddingSize);
        industryEmbedding = createMatrixParameter(
                "industry_embedding", industryEmbeddingBuckets, industryEmbeddingSize);
        groupHeadWeights = createMatrixParameter(
                "group_head_weights", groupHeadCount, representationSize * OUTPUT_SIZE);
        groupHeadBias = createMatrixParameter(
                "group_head_bias", groupHeadCount, OUTPUT_SIZE);
        stockResidual = createMatrixParameter(
                "stock_residual", stockEmbeddingBuckets, OUTPUT_SIZE);

        addChildBlock("temporal_backbone", temporalBackbone);
        addChildBlock("shared_projection", sharedProjection);
        addChildBlock("shared_output", sharedOutput);
        addParameter(stockEmbedding);
        addParameter(industryEmbedding);
        addParameter(groupHeadWeights);
        addParameter(groupHeadBias);
        addParameter(stockResidual);

        setInitializer(new XavierInitializer(), parameter -> parameter.getType() == Parameter.Type.WEIGHT);
        log.info("创建共享多任务 LSTM: inputSize={}, hiddenSize={}, stockBuckets={}, industryBuckets={}, groups={}",
                inputSize, hiddenSize, stockEmbeddingBuckets, industryEmbeddingBuckets, groupHeadCount);
    }

    private Parameter createMatrixParameter(String name, int rows, int columns) {
        return Parameter.builder()
                .setName(name)
                .setType(Parameter.Type.WEIGHT)
                .optShape(new Shape(rows, columns))
                .optInitializer(new XavierInitializer())
                .build();
    }

    /**
     * 执行多任务前向传播。
     *
     * @param parameterStore 参数存储
     * @param inputs 展平后的输入张量
     * @param training 是否处于训练阶段
     * @param params 扩展参数
     * @return 三任务输出张量
     */
    @Override
    protected NDList forwardInternal(ParameterStore parameterStore, NDList inputs, boolean training,
                                     PairList<String, Object> params) {
        NDArray flattened = inputs.singletonOrThrow();
        long batchSize = flattened.getShape().get(0);
        NDArray sequence = flattened.reshape(batchSize, sequenceLength, inputSize);

        // 离散索引不进入 LSTM，避免把类别编号误当作连续数值。
        NDArray temporalInput = sequence.get(new NDIndex(":, :, 0:" + STOCK_INDEX_COLUMN))
                .concat(sequence.get(new NDIndex(":, :, " + CONTINUOUS_TAIL_START + ":")), 2);
        NDArray temporalRepresentation = temporalBackbone.forward(
                parameterStore, new NDList(temporalInput), training, params).singletonOrThrow();

        NDArray stockIndices = categoryIndices(sequence, STOCK_INDEX_COLUMN, stockEmbeddingBuckets);
        NDArray industryIndices = categoryIndices(sequence, INDUSTRY_INDEX_COLUMN, industryEmbeddingBuckets);
        NDArray groupIndices = categoryIndices(sequence, GROUP_INDEX_COLUMN, groupHeadCount);

        NDArray stockVector = embeddingLookup(parameterStore, stockEmbedding, stockIndices, training);
        NDArray industryVector = embeddingLookup(parameterStore, industryEmbedding, industryIndices, training);
        NDArray representation = temporalRepresentation.concat(stockVector, 1).concat(industryVector, 1);

        NDArray sharedHidden = sharedProjection.forward(
                parameterStore, new NDList(representation), training, params).singletonOrThrow();
        NDArray sharedRaw = sharedOutput.forward(
                parameterStore, new NDList(sharedHidden), training, params).singletonOrThrow();

        // 每个分组拥有独立线性权重和偏置，并只做小幅输出校正。
        NDArray selectedGroupWeights = embeddingLookup(
                parameterStore, groupHeadWeights, groupIndices, training)
                .reshape(batchSize, representationSize, OUTPUT_SIZE);
        NDArray groupRaw = representation.expandDims(1)
                .batchMatMul(selectedGroupWeights)
                .squeeze(1)
                .add(embeddingLookup(parameterStore, groupHeadBias, groupIndices, training))
                .mul(groupHeadScale);

        NDArray combinedRaw = sharedRaw.add(groupRaw);
        if (residualHeadEnabled) {
            NDArray residualRaw = embeddingLookup(
                    parameterStore, stockResidual, stockIndices, training).mul(residualHeadScale);
            combinedRaw = combinedRaw.add(residualRaw);
        }

        NDArray predictedReturn = Activation.tanh(
                combinedRaw.get(new NDIndex(":, 0"))).expandDims(1);
        NDArray directionProbability = Activation.sigmoid(
                combinedRaw.get(new NDIndex(":, 1"))).expandDims(1);
        NDArray downsideRisk = Activation.sigmoid(
                combinedRaw.get(new NDIndex(":, 2"))).expandDims(1);
        return new NDList(predictedReturn.concat(directionProbability, 1).concat(downsideRisk, 1));
    }

    private NDArray categoryIndices(NDArray sequence, int column, int categoryCount) {
        return sequence.get(new NDIndex(":, -1, " + column))
                .clip(0, categoryCount - 1)
                .toType(DataType.INT64, false);
    }

    private NDArray embeddingLookup(ParameterStore parameterStore, Parameter parameter,
                                    NDArray indices, boolean training) {
        NDArray weight = parameterStore.getValue(parameter, indices.getDevice(), training);
        return Embedding.embedding(indices, weight, SparseFormat.DENSE).singletonOrThrow();
    }

    /**
     * 初始化子块和直接参数。
     *
     * @param manager NDArray 管理器
     * @param dataType 数据类型
     * @param inputShapes 输入形状
     */
    @Override
    protected void initializeChildBlocks(ai.djl.ndarray.NDManager manager, DataType dataType,
                                         Shape... inputShapes) {
        long batchSize = inputShapes[0].get(0);
        temporalBackbone.initialize(manager, dataType,
                new Shape(batchSize, sequenceLength, TEMPORAL_INPUT_SIZE));
        sharedProjection.initialize(manager, dataType, new Shape(batchSize, representationSize));
        sharedOutput.initialize(manager, dataType,
                new Shape(batchSize, Math.max(8, hiddenSize / 2)));
        initializeParameter(manager, dataType, stockEmbedding);
        initializeParameter(manager, dataType, industryEmbedding);
        initializeParameter(manager, dataType, groupHeadWeights);
        initializeParameter(manager, dataType, groupHeadBias);
        initializeParameter(manager, dataType, stockResidual);
    }

    private void initializeParameter(ai.djl.ndarray.NDManager manager, DataType dataType,
                                     Parameter parameter) {
        if (!parameter.isInitialized()) {
            parameter.initialize(manager, dataType);
        }
    }

    /**
     * 计算模型输出形状。
     *
     * @param inputShapes 输入形状
     * @return 输出形状，固定为 batch x 3
     */
    @Override
    public Shape[] getOutputShapes(Shape[] inputShapes) {
        return new Shape[]{new Shape(inputShapes[0].get(0), OUTPUT_SIZE)};
    }

    /**
     * 获取模型结构摘要。
     *
     * @return 可读模型结构摘要
     */
    public String getModelInfo() {
        return String.format(
                "Stock Multi-task LSTM [input=%d, hidden=%d, layers=%d, dropout=%.2f, seqLen=%d, stockEmbedding=%dx%d, industryEmbedding=%dx%d, groups=%d, residual=%s]",
                inputSize, hiddenSize, numLayers, dropout, sequenceLength,
                stockEmbeddingBuckets, stockEmbeddingSize,
                industryEmbeddingBuckets, industryEmbeddingSize,
                groupHeadCount, residualHeadEnabled);
    }
}
// AI_GENERATE_END ----