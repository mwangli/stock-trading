// AI_GENERATE_START -
package com.stock.modelService.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.config.OnnxInferenceConfig;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.domain.dto.OnnxModelMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 DJL 主结果之后执行 LSTM ONNX 影子推理并记录输出差异。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LstmOnnxShadowService {

    private final OnnxInferenceConfig onnxConfig;
    private final LstmTrainingConfig lstmConfig;
    private final OnnxSessionRegistry sessionRegistry;

    /**
     * 比较同一批输入的 DJL 与 ONNX 三任务输出，失败不影响 DJL 主路径。
     *
     * @param stockCodes 有效股票代码
     * @param inputs Java 特征输入
     * @param djlResults DJL 主推理结果
     */
    public void compare(List<String> stockCodes, List<float[][]> inputs,
                        Map<String, LstmPredictionResultDto> djlResults) {
        if (!onnxConfig.isEnabled() || !onnxConfig.isShadowEnabled() || inputs.isEmpty()) {
            return;
        }
        try {
            Path artifactDir = Path.of(onnxConfig.getLstmArtifactDir());
            OnnxSessionRegistry.SessionHandle handle = sessionRegistry.get(artifactDir);
            validateCompatibility(handle.artifact().metadata());
            int sequenceLength = inputs.get(0).length;
            int featureCount = inputs.get(0)[0].length;
            float[] flattened = flatten(inputs, sequenceLength, featureCount);
            String inputName = handle.artifact().metadata().getInputNames().get(0);
            try (OnnxTensor tensor = OnnxTensor.createTensor(
                    handle.session().getEnvironment(),
                    FloatBuffer.wrap(flattened),
                    new long[]{inputs.size(), sequenceLength, featureCount});
                 var result = handle.session().run(Map.of(inputName, tensor))) {
                float[][] outputs = (float[][]) result.get(0).getValue();
                reportDifferences(stockCodes, outputs, djlResults);
            }
        } catch (Exception exception) {
            log.error("LSTM ONNX 影子推理失败，继续使用 DJL 主结果", exception);
        }
    }

    private void validateCompatibility(OnnxModelMetadata metadata) {
        if (!LstmDataPreprocessor.FEATURE_VERSION.equals(metadata.getFeatureVersion())) {
            throw new IllegalStateException("LSTM ONNX 特征版本不兼容");
        }
        requireNumber(metadata, "sequenceLength", lstmConfig.getSequenceLength());
        requireNumber(metadata, "inputSize", lstmConfig.getInputSize());
        requireNumber(metadata, "outputSize", 3);
        requireNumber(metadata, "stockEmbeddingBuckets", lstmConfig.getStockEmbeddingBuckets());
        requireNumber(metadata, "industryEmbeddingBuckets", lstmConfig.getIndustryEmbeddingBuckets());
        requireNumber(metadata, "groupHeadCount", lstmConfig.getGroupHeadCount());
    }

    private void requireNumber(OnnxModelMetadata metadata, String key, int expected) {
        Object raw = metadata.getCompatibility().get(key);
        if (!(raw instanceof Number number) || number.intValue() != expected) {
            throw new IllegalStateException("LSTM ONNX 元数据不兼容: " + key);
        }
    }

    private float[] flatten(List<float[][]> inputs, int sequenceLength, int featureCount) {
        float[] flattened = new float[inputs.size() * sequenceLength * featureCount];
        int offset = 0;
        for (float[][] sequence : inputs) {
            for (float[] row : sequence) {
                System.arraycopy(row, 0, flattened, offset, featureCount);
                offset += featureCount;
            }
        }
        return flattened;
    }

    private void reportDifferences(List<String> stockCodes, float[][] outputs,
                                   Map<String, LstmPredictionResultDto> djlResults) {
        Map<String, Double> maxDifferenceByStock = new LinkedHashMap<>();
        double batchMaxDifference = 0D;
        for (int index = 0; index < stockCodes.size(); index++) {
            LstmPredictionResultDto djl = djlResults.get(stockCodes.get(index));
            if (djl == null) {
                continue;
            }
            double djlScaledReturn = djl.getPredictedChangeRatio() / lstmConfig.getTargetReturnScale();
            double maxDifference = Math.max(
                    Math.abs(outputs[index][0] - djlScaledReturn),
                    Math.max(
                            Math.abs(outputs[index][1] - djl.getDirectionProbability()),
                            Math.abs(outputs[index][2] - djl.getDownsideRisk())));
            maxDifferenceByStock.put(stockCodes.get(index), maxDifference);
            batchMaxDifference = Math.max(batchMaxDifference, maxDifference);
        }
        if (batchMaxDifference > onnxConfig.getOutputTolerance()) {
            log.warn("LSTM ONNX 影子输出超过容差: maxDifference={}, tolerance={}, details={}",
                    batchMaxDifference, onnxConfig.getOutputTolerance(), maxDifferenceByStock);
        } else {
            log.info("LSTM ONNX 影子输出通过: stocks={}, maxDifference={}",
                    maxDifferenceByStock.size(), batchMaxDifference);
        }
    }
}
// AI_GENERATE_END -
