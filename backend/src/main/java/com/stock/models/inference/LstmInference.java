package com.stock.models.inference;

import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.training.ParameterStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * LSTM 模型推理组件
 */
@Slf4j
@Component
public class LstmInference {

    private Model model;
    private boolean isLoaded = false;
    private String modelPath = "models/lstm-stock";
    private LocalDateTime lastLoadedTime;

    @PostConstruct
    public void init() {
        loadModel();
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
    public void loadModel(String path) {
        try {
            Path modelPath = Paths.get(path);
            Path paramFile = modelPath.resolve("lstm-stock-0000.params");
            
            if (!paramFile.toFile().exists()) {
                log.warn("模型文件不存在：{}, 使用空模型", paramFile);
                isLoaded = false;
                return;
            }

            if (this.model != null) {
                this.model.close();
            }

            this.model = Model.newInstance("lstm-stock");
            this.model.load(modelPath, "lstm-stock");
            this.isLoaded = true;
            this.lastLoadedTime = LocalDateTime.now();
            log.info("LSTM 模型加载成功，路径：{}", modelPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("加载 LSTM 模型失败", e);
            isLoaded = false;
        }
    }

    /**
     * 使用 LSTM 模型进行预测
     *
     * @param data 输入数据 [batch, sequenceLength, inputSize]
     * @return 预测结果
     */
    public float[] predict(float[][][] data) {
        if (!isLoaded || model == null) {
            log.warn("模型未加载，返回默认预测");
            return new float[]{0f};
        }

        try (NDManager manager = NDManager.newBaseManager()) {
            // 展平输入数据
            float[] flatData = new float[data.length * data[0].length * data[0][0].length];
            int idx = 0;
            for (float[][] batch : data) {
                for (float[] row : batch) {
                    for (float val : row) {
                        flatData[idx++] = val;
                    }
                }
            }
            NDArray input = manager.create(flatData, new Shape(data.length, data[0].length, data[0][0].length));
            
            ParameterStore parameterStore = new ParameterStore(manager, false);
            NDList output = model.getBlock().forward(parameterStore, new NDList(input), false);
            return output.singletonOrThrow().toFloatArray();
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

        return response;
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

    public boolean isLoaded() {
        return isLoaded;
    }

    public String getModelPath() {
        return modelPath;
    }

    public LocalDateTime getLastLoadedTime() {
        return lastLoadedTime;
    }
}
