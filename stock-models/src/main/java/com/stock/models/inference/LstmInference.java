package com.stock.models.inference;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.training.ParameterStore;
import ai.djl.translate.TranslateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class LstmInference {

    private Model model;
    private boolean isLoaded = false;

    @PostConstruct
    public void init() {
        loadModel();
    }

    public void loadModel() {
        try {
            Path modelPath = Paths.get("models/lstm-stock.pt");
            if (!modelPath.toFile().exists()) {
                log.warn("模型文件不存在: {}, 使用空模型", modelPath);
                isLoaded = false;
                return;
            }

            this.model = Model.newInstance("lstm-stock");
            this.model.load(modelPath, "lstm-stock");
            this.isLoaded = true;
            log.info("LSTM模型加载成功");
        } catch (Exception e) {
            log.error("加载LSTM模型失败", e);
            isLoaded = false;
        }
    }

    public float[] predict(float[][][] data) {
        if (!isLoaded || model == null) {
            log.warn("模型未加载, 返回默认预测");
            return new float[]{0f};
        }

        try (NDManager manager = NDManager.newBaseManager()) {
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

    public void unload() {
        if (model != null) {
            model.close();
            isLoaded = false;
            log.info("模型已卸载");
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}
