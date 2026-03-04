package com.stock.modelService.inference;

import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.ndarray.types.DataType;
import ai.djl.training.ParameterStore;
import ai.djl.nn.Block;
import ai.djl.nn.Blocks;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.norm.LayerNorm;
import ai.djl.nn.recurrent.LSTM;
import com.stock.modelService.entity.LstmModelDocument;
import com.stock.modelService.model.StockLSTMModel;
import com.stock.modelService.repository.LstmModelRepository;
import com.stock.modelService.model.io.ModelBinaryCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LSTM 模型推理组件
 * 支持加载DJL训练的LSTM模型并进行预测
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class LstmInference {

    private final LstmModelRepository lstmModelRepository;
    private final ModelBinaryCodec modelBinaryCodec;

    private Model model;
    private boolean isLoaded = false;
    private String modelPath = "models/lstm-stock";
    private LocalDateTime lastLoadedTime;

    // 归一化参数
    private double maxPrice = 1.0;
    private double maxVolume = 1.0;
    private int sequenceLength = 60;
    private int inputSize = 5;
    private int hiddenSize = 50;
    private int numLayers = 2;

    @PostConstruct
    public void init() {
        // Lazy load
    }

    /**
     * 加载模型（使用默认路径）
     */
    public void loadModel() {
        loadModel(modelPath);
    }

    /**
     * 加载模型（指定路径）
     */
    /**
     * 加载模型（指定路径）
     * 注意：path 参数仅为了兼容接口保留，实际总是优先从 MongoDB 加载
     */
    public void loadModel(String path) {
        try {
            // 1. Load latest model from MongoDB
            LstmModelDocument latest = lstmModelRepository.findTopByOrderByCreatedAtDesc();
            
            if (latest != null && latest.getParams() != null) {
                log.info("Loading latest LSTM model from MongoDB: {}, version: {}", latest.getModelName(), latest.getModelVersion());

                if (this.model != null) {
                    this.model.close();
                }

                // 2. Parse normalization params to configure model structure
                parseNormalizationParams(latest.getNormalizationParams());

                // 3. Create Model instance
                this.model = Model.newInstance("lstm-stock", "PyTorch");

                // 4. Reconstruct Block structure
                StockLSTMModel block = new StockLSTMModel(
                        inputSize, hiddenSize, numLayers, 0.2f, sequenceLength
                );
                this.model.setBlock(block);

                // 5. Deserialize parameters directly from memory
                modelBinaryCodec.deserialize(latest.getParams(), this.model);

                this.isLoaded = true;
                this.lastLoadedTime = LocalDateTime.now();
                this.modelPath = "mongo:" + latest.getId();
                log.info("LSTM model loaded successfully from memory.");
                return;
            }
            
            log.warn("No trained model found in MongoDB. Using default initialized model.");
            createDefaultModel();

        } catch (Exception e) {
            log.error("Failed to load LSTM model from memory", e);
            createDefaultModel();
        }
    }

    private void parseNormalizationParams(String content) {
        if (content == null || content.isEmpty()) return;

        String[] lines = content.split("\n");
        for (String line : lines) {
            String[] parts = line.split("=");
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                try {
                    switch (key) {
                        case "maxPrice": maxPrice = Double.parseDouble(value); break;
                        case "maxVolume": maxVolume = Double.parseDouble(value); break;
                        case "sequenceLength": sequenceLength = Integer.parseInt(value); break;
                        case "inputSize": inputSize = Integer.parseInt(value); break;
                        case "hiddenSize": hiddenSize = Integer.parseInt(value); break;
                        case "numLayers": numLayers = Integer.parseInt(value); break;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid format for param: {}={}", key, value);
                }
            }
        }
        log.info("Normalization params parsed: maxPrice={}, seqLen={}, inputSize={}, hiddenSize={}", 
                maxPrice, sequenceLength, inputSize, hiddenSize);
    }
    /**
     * 创建默认LSTM模型（当没有训练好的模型时）
     */
    private void createDefaultModel() {
        try {
            if (this.model != null) {
                this.model.close();
            }

            this.model = Model.newInstance("lstm-stock-default", "PyTorch");
            Block block = createDefaultLSTMBlock();
            this.model.setBlock(block);

            this.isLoaded = false;
            log.info("使用默认LSTM模型结构");

        } catch (Exception e) {
            log.error("创建默认模型失败", e);
        }
    }

    /**
     * 创建默认LSTM模型块
     */
    private Block createDefaultLSTMBlock() {
        SequentialBlock block = new SequentialBlock();
        
        // 输入重塑
        block.addSingleton(input -> {
            Shape shape = input.getShape();
            long batchSize = shape.get(0);
            return input.reshape(new Shape(batchSize, sequenceLength, inputSize));
        });
        
        // LSTM层
        LSTM lstm = new LSTM.Builder()
                .setStateSize(hiddenSize)
                .setNumLayers(numLayers)
                .optDropRate(0.2f)
                .optReturnState(false)
                .optBatchFirst(true)
                .build();
        block.add(lstm);
        
        // LayerNorm
        block.add(LayerNorm.builder().build());
        
        // 展平
        block.add(Blocks.batchFlattenBlock());
        
        // 全连接层
        block.add(Linear.builder().setUnits(hiddenSize).build());
        block.add(ai.djl.nn.Activation::relu);
        block.add(Linear.builder().setUnits(hiddenSize / 2).build());
        block.add(ai.djl.nn.Activation::relu);
        block.add(Linear.builder().setUnits(1).build());
        
        return block;
    }

    /**
     * 使用 LSTM 模型进行预测
     *
     * @param data 输入数据 [batch, sequenceLength, inputSize]
     * @return 预测结果
     */
    public float[] predict(float[][][] data) {
        if (!isLoaded) {
            loadModel();
        }

        if (data == null || data.length == 0) {
            log.warn("输入数据为空，返回默认预测");
            return new float[]{0f};
        }

        if (model == null || model.getBlock() == null) {
            log.warn("模型未加载，返回默认预测");
            return new float[]{0f};
        }

        // 使用 model 的 NDManager（如果可用）来创建输入，保证参数和 NDArray 在同一 manager 下
        NDManager manager = model != null && model.getNDManager() != null
                ? model.getNDManager().newSubManager()
                : NDManager.newBaseManager("PyTorch");
        try (NDManager autoClose = manager) {
            // 展平输入数据: [batch, seq, features] -> [batch, seq*features]
            int batchSize = data.length;
            int seqLen = data[0].length;
            int features = data[0][0].length;

            float[] flatData = new float[batchSize * seqLen * features];
            int idx = 0;
            for (float[][] batch : data) {
                for (float[] seq : batch) {
                    for (float val : seq) {
                        flatData[idx++] = val;
                    }
                }
            }

            // 创建输入张量
            NDArray input = manager.create(flatData, new Shape(batchSize, seqLen * features));

            // 确保模型参数已初始化：如果模型是未训练的默认结构，初始化子块
            if (model.getBlock() instanceof com.stock.modelService.model.StockLSTMModel) {
                // 对于自定义 StockLSTMModel，block.initialize 在创建模型时可能尚未被调用
                try {
                    model.getBlock().initialize(manager, DataType.FLOAT32, new Shape(batchSize, seqLen * features));
                } catch (Exception ignore) {
                    // 忽略初始化已完成或不必要的错误
                }
            }

            // 执行推理
            ParameterStore parameterStore = new ParameterStore(manager, false);
            NDList output = model.getBlock().forward(parameterStore, new NDList(input), false);

            // 获取预测结果
            float[] predictions = output.singletonOrThrow().toFloatArray();

            // 反归一化
            for (int i = 0; i < predictions.length; i++) {
                predictions[i] *= (float) maxPrice;
            }

            return predictions;

        } catch (Exception e) {
            log.error("推理失败", e);
            return new float[]{0f};
        }
    }

    /**
     * 预测并返回详细信息
     */
    public Map<String, Object> predictWithDetails(String stockCode, float[][][] data) {
        float[] result = predict(data);
        float predictedPrice = result.length > 0 ? result[0] : 0f;

        Map<String, Object> response = new HashMap<>();
        response.put("stockCode", stockCode);
        response.put("predictedPrice", predictedPrice);
        response.put("isTrained", isLoaded);
        response.put("confidence", isLoaded ? 0.75f : 0.5f);
        response.put("modelPath", modelPath);
        response.put("lastLoadedTime", lastLoadedTime != null ? lastLoadedTime.toString() : null);
        response.put("modelInfo", String.format("LSTM[input=%d, hidden=%d, layers=%d, seqLen=%d]",
                inputSize, hiddenSize, numLayers, sequenceLength));

        return response;
    }

    /**
     * 使用归一化参数预测（输入原始价格数据）
     */
    public float[] predictWithNormalization(float[][][] rawData, double maxPriceParam, double maxVolumeParam) {
        // 归一化输入
        float[][][] normalizedData = new float[rawData.length][][];
        for (int b = 0; b < rawData.length; b++) {
            normalizedData[b] = new float[rawData[b].length][];
            for (int s = 0; s < rawData[b].length; s++) {
                normalizedData[b][s] = new float[rawData[b][s].length];
                for (int f = 0; f < rawData[b][s].length; f++) {
                    if (f < 4) {
                        normalizedData[b][s][f] = (float) (rawData[b][s][f] / maxPriceParam);
                    } else {
                        normalizedData[b][s][f] = (float) (rawData[b][s][f] / maxVolumeParam);
                    }
                }
            }
        }

        return predict(normalizedData);
    }

    /**
     * 卸载模型
     */
    public void unload() {
        if (model != null) {
            model.close();
            isLoaded = false;
            log.info("模型已卸载");
        }
    }

    /**
     * 重新加载最新训练的模型
     */
    public void reloadLatestModel() {
        log.info("重新加载最新模型...");
        loadModel(modelPath);
    }

    /**
     * 设置模型路径
     */
    public void setModelPath(String path) {
        this.modelPath = path;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public String getModelPath() {
        return modelPath;
    }

    public LocalDateTime getLastLoadedTime() {
        return lastLoadedTime;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public double getMaxVolume() {
        return maxVolume;
    }

    public int getSequenceLength() {
        return sequenceLength;
    }

    public int getInputSize() {
        return inputSize;
    }
}
