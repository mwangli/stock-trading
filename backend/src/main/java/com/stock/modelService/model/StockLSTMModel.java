// AI_GENERATE_START --
package com.stock.modelService.model;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.AbstractBlock;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.norm.LayerNorm;
import ai.djl.nn.recurrent.LSTM;
import ai.djl.training.ParameterStore;
import ai.djl.ndarray.types.DataType;
import ai.djl.training.initializer.XavierInitializer;
import ai.djl.util.PairList;
import lombok.extern.slf4j.Slf4j;
/**
 * 股票价格预测 LSTM 模型
 * 
 * 使用 DJL 原生的 ai.djl.nn.recurrent.LSTM 实现
 * 使用共享时序主干提取全市场量价特征，并输出缩放后的下一交易日收益率。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Slf4j
public class StockLSTMModel extends AbstractBlock {

    private final SequentialBlock model;
    private final int inputSize;
    private final int hiddenSize;
    private final int numLayers;
    private final float dropout;
    private final int sequenceLength;

    /**
     * 创建股票预测LSTM模型
     *
     * @param inputSize      输入特征维度，当前共享模型为 15
     * @param hiddenSize     LSTM隐藏层大小
     * @param numLayers      LSTM层数
     * @param dropout        Dropout率
     * @param sequenceLength 输入序列长度 (如60天历史数据)
     */
    public StockLSTMModel(int inputSize, int hiddenSize, int numLayers, float dropout, int sequenceLength) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.numLayers = numLayers;
        this.dropout = dropout;
        this.sequenceLength = sequenceLength;

        log.info("创建LSTM模型 - inputSize: {}, hiddenSize: {}, numLayers: {}, dropout: {}, sequenceLength: {}", 
                inputSize, hiddenSize, numLayers, dropout, sequenceLength);

        // 使用SequentialBlock构建模型
        model = new SequentialBlock();
        
        // 1. 输入重塑层: [batch, seq*features] -> [batch, seq, features]
        model.addSingleton(input -> {
            Shape shape = input.getShape();
            long batchSize = shape.get(0);
            return input.reshape(new Shape(batchSize, sequenceLength, inputSize));
        });
        
        // 2. LSTM层 (核心)
        LSTM lstm = new LSTM.Builder()
                .setStateSize(hiddenSize)
                .setNumLayers(numLayers)
                .optDropRate(dropout)
                .optReturnState(false)    // 只返回输出，不返回隐藏状态
                .optBatchFirst(true)      // batch在第一维
                .optBidirectional(false)  // 单向LSTM
                .build();
        model.add(lstm);
        
        // 3. Layer Normalization (帮助训练稳定性)
        model.add(LayerNorm.builder().build());
        
        // 4. 只保留最后一个时间步，避免窗口展平导致参数量随序列长度膨胀
        model.addSingleton(input -> input.get(new NDIndex(":, -1, :")));
        
        // 5. 全连接层1
        model.add(Linear.builder().setUnits(hiddenSize).build());
        model.add(ai.djl.nn.Activation::relu);
        
        // 6. Dropout已在LSTM层内部处理，这里不需要额外处理
        
        // 7. 全连接层2
        model.add(Linear.builder().setUnits(hiddenSize / 2).build());
        model.add(ai.djl.nn.Activation::relu);
        
        // 8. 输出缩放后的下一交易日收益率
        model.add(Linear.builder().setUnits(1).build());
        model.add(ai.djl.nn.Activation::tanh);
        
        // 添加为子块
        addChildBlock("lstm_model", model);
        
        // 设置初始化器：仅对权重参数使用 Xavier，避免作用于一维 bias
        setInitializer(new XavierInitializer(), param -> {
            String name = param.getName() == null ? "" : param.getName().toLowerCase();
            return name.contains("weight") || name.contains("kernel");
        });
    }

    /**
     * 使用默认 60 日序列创建共享 LSTM。
     *
     * @param inputSize 输入特征维度
     * @param hiddenSize 隐藏层大小
     * @param numLayers LSTM 层数
     * @param dropout Dropout 比例
     */
    public StockLSTMModel(int inputSize, int hiddenSize, int numLayers, float dropout) {
        this(inputSize, hiddenSize, numLayers, dropout, 60);  // 默认60天序列
    }

    /**
     * 执行共享 LSTM 前向传播。
     *
     * @param parameterStore 参数存储
     * @param inputs 输入张量
     * @param training 是否处于训练阶段
     * @param params 扩展参数
     * @return 模型输出
     */
    @Override
    protected NDList forwardInternal(ParameterStore parameterStore, NDList inputs, boolean training, PairList<String, Object> params) {
        return model.forward(parameterStore, inputs, training, params);
    }

    /**
     * 初始化内部顺序网络。
     *
     * @param manager NDArray 管理器
     * @param dataType 数据类型
     * @param inputShapes 输入形状
     */
    @Override
    protected void initializeChildBlocks(ai.djl.ndarray.NDManager manager, DataType dataType, Shape... inputShapes) {
        // 将子块交给 SequentialBlock 进行初始化
        model.initialize(manager, dataType, inputShapes);
    }

    /**
     * 计算模型输出形状。
     *
     * @param inputShapes 输入形状
     * @return 输出形状
     */
    @Override
    public Shape[] getOutputShapes(Shape[] inputShapes) {
        // 输出形状: [batch, 1]
        return new Shape[]{new Shape(inputShapes[0].get(0), 1)};
    }

    /**
     * 获取模型配置信息。
     *
     * @return 可读模型结构摘要
     */
    public String getModelInfo() {
        return String.format(
                "Stock LSTM Model [input=%d, hidden=%d, layers=%d, dropout=%.2f, seqLen=%d]", 
                inputSize, hiddenSize, numLayers, dropout, sequenceLength);
    }
    
    /**
     * 获取输入特征数量。
     *
     * @return 输入特征数量
     */
    public int getInputSize() {
        return inputSize;
    }
    
    /**
     * 获取隐藏层大小。
     *
     * @return 隐藏层大小
     */
    public int getHiddenSize() {
        return hiddenSize;
    }
    
    /**
     * 获取 LSTM 层数。
     *
     * @return LSTM 层数
     */
    public int getNumLayers() {
        return numLayers;
    }
    
    /**
     * 获取输入序列长度。
     *
     * @return 输入序列长度
     */
    public int getSequenceLength() {
        return sequenceLength;
    }
}
// AI_GENERATE_END --
