package com.stock.modelService.model;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.AbstractBlock;
import ai.djl.nn.Blocks;
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
 * 真正的 LSTM 神经网络，支持时序数据训练和预测
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
     * @param inputSize      输入特征维度 (如开盘价、收盘价等，通常为5)
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
        
        // 4. 展平层: [batch, seq, hidden] -> [batch, seq * hidden]
        model.add(Blocks.batchFlattenBlock());
        
        // 5. 全连接层1
        model.add(Linear.builder().setUnits(hiddenSize).build());
        model.add(ai.djl.nn.Activation::relu);
        
        // 6. Dropout已在LSTM层内部处理，这里不需要额外处理
        
        // 7. 全连接层2
        model.add(Linear.builder().setUnits(hiddenSize / 2).build());
        model.add(ai.djl.nn.Activation::relu);
        
        // 8. 输出层 (预测下一个价格)
        model.add(Linear.builder().setUnits(1).build());
        
        // 添加为子块
        addChildBlock("lstm_model", model);
        
        // 设置初始化器：仅对权重参数使用 Xavier，避免作用于一维 bias
        setInitializer(new XavierInitializer(), param -> {
            String name = param.getName() == null ? "" : param.getName().toLowerCase();
            return name.contains("weight") || name.contains("kernel");
        });
    }

    /**
     * 简化构造函数 (使用默认sequenceLength)
     */
    public StockLSTMModel(int inputSize, int hiddenSize, int numLayers, float dropout) {
        this(inputSize, hiddenSize, numLayers, dropout, 60);  // 默认60天序列
    }

    @Override
    protected NDList forwardInternal(ParameterStore parameterStore, NDList inputs, boolean training, PairList<String, Object> params) {
        return model.forward(parameterStore, inputs, training, params);
    }

    @Override
    protected void initializeChildBlocks(ai.djl.ndarray.NDManager manager, DataType dataType, Shape... inputShapes) {
        // 将子块交给 SequentialBlock 进行初始化
        model.initialize(manager, dataType, inputShapes);
    }

    @Override
    public Shape[] getOutputShapes(Shape[] inputShapes) {
        // 输出形状: [batch, 1]
        return new Shape[]{new Shape(inputShapes[0].get(0), 1)};
    }

    /**
     * 获取模型配置信息
     */
    public String getModelInfo() {
        return String.format(
                "Stock LSTM Model [input=%d, hidden=%d, layers=%d, dropout=%.2f, seqLen=%d]", 
                inputSize, hiddenSize, numLayers, dropout, sequenceLength);
    }
    
    public int getInputSize() {
        return inputSize;
    }
    
    public int getHiddenSize() {
        return hiddenSize;
    }
    
    public int getNumLayers() {
        return numLayers;
    }
    
    public int getSequenceLength() {
        return sequenceLength;
    }
}
