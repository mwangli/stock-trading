// AI_GENERATE_START --
package com.stock.model.model;

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

/**
 * 横截面排序 LSTM 模型。
 * 支持基础 Ranking LSTM 和特征、时间双注意力结构，并融合稳定的股票、行业和市场类别字典。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public class RankingLSTMModel extends AbstractBlock {

    /** `v5` 固定输出数量。 */
    public static final int OUTPUT_SIZE = 4;

    private static final int CATEGORY_COLUMN_COUNT = 3;

    private final LstmArchitectureType architectureType;
    private final int inputSize;
    private final int continuousFeatureCount;
    private final int hiddenSize;
    private final int sequenceLength;
    private final int representationSize;
    private final SequentialBlock featureAttentionProjection;
    private final SequentialBlock encoder;
    private final SequentialBlock timeAttentionProjection;
    private final SequentialBlock sharedProjection;
    private final Linear outputProjection;
    private final Parameter stockEmbedding;
    private final Parameter industryEmbedding;
    private final Parameter marketEmbedding;
    private final int stockDictionarySize;
    private final int industryDictionarySize;
    private final int marketDictionarySize;
    private final int stockEmbeddingSize;
    private final int industryEmbeddingSize;
    private final int marketEmbeddingSize;

    /**
     * 创建横截面排序 LSTM。
     *
     * @param architectureType 模型架构类型
     * @param inputSize 每个时间步的总输入维度，末尾三列固定为类别索引
     * @param hiddenSize LSTM 隐藏维度
     * @param numLayers LSTM 层数
     * @param dropout Dropout 比例
     * @param sequenceLength 序列长度
     * @param stockDictionarySize 股票字典容量
     * @param stockEmbeddingSize 股票 Embedding 维度
     * @param industryDictionarySize 行业字典容量
     * @param industryEmbeddingSize 行业 Embedding 维度
     * @param marketDictionarySize 市场字典容量
     * @param marketEmbeddingSize 市场 Embedding 维度
     */
    public RankingLSTMModel(
            LstmArchitectureType architectureType,
            int inputSize,
            int hiddenSize,
            int numLayers,
            float dropout,
            int sequenceLength,
            int stockDictionarySize,
            int stockEmbeddingSize,
            int industryDictionarySize,
            int industryEmbeddingSize,
            int marketDictionarySize,
            int marketEmbeddingSize) {
        if (architectureType == null || architectureType == LstmArchitectureType.LEGACY_MULTITASK_LSTM) {
            throw new IllegalArgumentException("v5 模型必须使用 Ranking LSTM 架构");
        }
        if (inputSize <= CATEGORY_COLUMN_COUNT) {
            throw new IllegalArgumentException("v5 LSTM 输入必须包含连续特征和三列类别索引");
        }
        this.architectureType = architectureType;
        this.inputSize = inputSize;
        this.continuousFeatureCount = inputSize - CATEGORY_COLUMN_COUNT;
        this.hiddenSize = hiddenSize;
        this.sequenceLength = sequenceLength;
        this.stockDictionarySize = stockDictionarySize;
        this.industryDictionarySize = industryDictionarySize;
        this.marketDictionarySize = marketDictionarySize;
        this.stockEmbeddingSize = stockEmbeddingSize;
        this.industryEmbeddingSize = industryEmbeddingSize;
        this.marketEmbeddingSize = marketEmbeddingSize;
        this.representationSize = hiddenSize + stockEmbeddingSize + industryEmbeddingSize + marketEmbeddingSize;

        featureAttentionProjection = new SequentialBlock();
        featureAttentionProjection.add(Linear.builder().setUnits(continuousFeatureCount).build());

        encoder = new SequentialBlock();
        encoder.add(new LSTM.Builder()
                .setStateSize(hiddenSize)
                .setNumLayers(numLayers)
                .optDropRate(dropout)
                .optReturnState(false)
                .optBatchFirst(true)
                .optBidirectional(false)
                .build());
        encoder.add(LayerNorm.builder().build());

        timeAttentionProjection = new SequentialBlock();
        timeAttentionProjection.add(Linear.builder().setUnits(1).build());

        sharedProjection = new SequentialBlock();
        sharedProjection.add(Linear.builder().setUnits(Math.max(16, hiddenSize / 2)).build());
        sharedProjection.add(Activation::relu);
        outputProjection = Linear.builder().setUnits(OUTPUT_SIZE).build();

        stockEmbedding = createEmbeddingParameter("v5_stock_embedding", stockDictionarySize, stockEmbeddingSize);
        industryEmbedding = createEmbeddingParameter(
                "v5_industry_embedding", industryDictionarySize, industryEmbeddingSize);
        marketEmbedding = createEmbeddingParameter("v5_market_embedding", marketDictionarySize, marketEmbeddingSize);

        addChildBlock("feature_attention_projection", featureAttentionProjection);
        addChildBlock("encoder", encoder);
        addChildBlock("time_attention_projection", timeAttentionProjection);
        addChildBlock("shared_projection", sharedProjection);
        addChildBlock("output_projection", outputProjection);
        addParameter(stockEmbedding);
        addParameter(industryEmbedding);
        addParameter(marketEmbedding);
        setInitializer(new XavierInitializer(), parameter -> parameter.getType() == Parameter.Type.WEIGHT);
    }

    private Parameter createEmbeddingParameter(String name, int rows, int columns) {
        return Parameter.builder()
                .setName(name)
                .setType(Parameter.Type.WEIGHT)
                .optShape(new Shape(rows, columns))
                .optInitializer(new XavierInitializer())
                .build();
    }

    /**
     * 执行横截面排序模型前向传播。
     *
     * @param parameterStore 参数存储
     * @param inputs 展平后的时序输入
     * @param training 是否处于训练阶段
     * @param params 扩展参数
     * @return 四任务输出张量
     */
    @Override
    protected NDList forwardInternal(
            ParameterStore parameterStore, NDList inputs, boolean training, PairList<String, Object> params) {
        NDArray flattened = inputs.singletonOrThrow();
        long batchSize = flattened.getShape().get(0);
        NDArray sequence = flattened.reshape(batchSize, sequenceLength, inputSize);
        NDArray continuous = sequence.get(new NDIndex(":, :, 0:" + continuousFeatureCount));

        NDArray encoderInput = continuous;
        if (architectureType == LstmArchitectureType.DUAL_ATTENTION_RANKING_LSTM) {
            NDArray featureScores = featureAttentionProjection.forward(
                    parameterStore, new NDList(continuous), training, params).singletonOrThrow();
            encoderInput = continuous.mul(featureScores.softmax(2));
        }

        NDArray encodedSequence = encoder.forward(
                parameterStore, new NDList(encoderInput), training, params).singletonOrThrow();
        NDArray temporalRepresentation;
        if (architectureType == LstmArchitectureType.DUAL_ATTENTION_RANKING_LSTM) {
            NDArray timeScores = timeAttentionProjection.forward(
                    parameterStore, new NDList(encodedSequence), training, params).singletonOrThrow();
            NDArray timeWeights = timeScores.softmax(1);
            temporalRepresentation = encodedSequence.mul(timeWeights).sum(new int[]{1});
        } else {
            temporalRepresentation = encodedSequence.get(new NDIndex(":, -1, :"));
        }

        int stockColumn = continuousFeatureCount;
        int industryColumn = continuousFeatureCount + 1;
        int marketColumn = continuousFeatureCount + 2;
        NDArray stockVector = embeddingLookup(parameterStore, stockEmbedding,
                categoryIndices(sequence, stockColumn, stockDictionarySize), training);
        NDArray industryVector = embeddingLookup(parameterStore, industryEmbedding,
                categoryIndices(sequence, industryColumn, industryDictionarySize), training);
        NDArray marketVector = embeddingLookup(parameterStore, marketEmbedding,
                categoryIndices(sequence, marketColumn, marketDictionarySize), training);

        NDArray representation = temporalRepresentation.concat(stockVector, 1)
                .concat(industryVector, 1)
                .concat(marketVector, 1);
        NDArray hidden = sharedProjection.forward(
                parameterStore, new NDList(representation), training, params).singletonOrThrow();
        NDArray raw = outputProjection.forward(
                parameterStore, new NDList(hidden), training, params).singletonOrThrow();

        NDArray rankScore = raw.get(new NDIndex(":, 0")).expandDims(1);
        NDArray expectedExcessReturn = Activation.tanh(raw.get(new NDIndex(":, 1))).expandDims(1);
        NDArray upProbability = Activation.sigmoid(raw.get(new NDIndex(":, 2))).expandDims(1);
        NDArray downsideQuantile = Activation.sigmoid(raw.get(new NDIndex(":, 3))).expandDims(1);
        return new NDList(rankScore.concat(expectedExcessReturn, 1)
                .concat(upProbability, 1)
                .concat(downsideQuantile, 1));
    }

    private NDArray categoryIndices(NDArray sequence, int column, int categoryCount) {
        return sequence.get(new NDIndex(":, -1, " + column))
                .clip(0, categoryCount - 1)
                .toType(DataType.INT64, false);
    }

    private NDArray embeddingLookup(
            ParameterStore parameterStore, Parameter parameter, NDArray indices, boolean training) {
        NDArray weight = parameterStore.getValue(parameter, indices.getDevice(), training);
        return Embedding.embedding(indices, weight, SparseFormat.DENSE).singletonOrThrow();
    }

    /**
     * 初始化模型子块和直接参数。
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
        featureAttentionProjection.initialize(manager, dataType, sequenceShape);
        encoder.initialize(manager, dataType, sequenceShape);
        timeAttentionProjection.initialize(manager, dataType, new Shape(batchSize, sequenceLength, hiddenSize));
        sharedProjection.initialize(manager, dataType, new Shape(batchSize, representationSize));
        outputProjection.initialize(manager, dataType, new Shape(batchSize, Math.max(16, hiddenSize / 2)));
        initializeParameter(manager, dataType, stockEmbedding);
        initializeParameter(manager, dataType, industryEmbedding);
        initializeParameter(manager, dataType, marketEmbedding);
    }

    private void initializeParameter(
            ai.djl.ndarray.NDManager manager, DataType dataType, Parameter parameter) {
        if (!parameter.isInitialized()) {
            parameter.initialize(manager, dataType);
        }
    }

    /**
     * 计算模型输出形状。
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
