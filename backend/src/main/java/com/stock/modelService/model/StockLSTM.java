package com.stock.modelService.model;


import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.nn.Block;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.training.ParameterStore;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;

@Slf4j
public class StockLSTM {

    public static Block createLstmBlock(int inputSize, int hiddenSize, int numLayers) {
        try {
            SequentialBlock block = new SequentialBlock();
            
            block.add(Linear.builder().setUnits(hiddenSize).build());
            
            block.add(Linear.builder().setUnits(50).build());
            block.add(ai.djl.nn.Activation::relu);
            block.add(Linear.builder().setUnits(25).build());
            block.add(ai.djl.nn.Activation::relu);
            block.add(Linear.builder().setUnits(1).build());
            
            return block;
        } catch (Exception e) {
            log.error("创建LSTM网络失败", e);
            throw new RuntimeException("创建LSTM网络失败", e);
        }
    }

    public static Block createSimpleLstm(int inputSize, int hiddenSize) {
        SequentialBlock block = new SequentialBlock();
        
        block.add(Linear.builder().setUnits(hiddenSize).build());
        block.add(Linear.builder().setUnits(1).build());
        
        return block;
    }
}
