// AI_GENERATE_START --
package com.stock.modelService.service;

import ai.onnxruntime.OnnxTensor;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.config.LstmModelConfig;
import com.stock.modelService.config.OnnxInferenceConfig;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.domain.dto.OnnxModelMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 使用 Python 产出的 ONNX 制品执行 LSTM 在线推理。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LstmInferenceService {

    private static final int OUTPUT_SIZE = 3;

    private final OnnxInferenceConfig onnxConfig;
    private final LstmModelConfig modelConfig;
    private final OnnxSessionRegistry sessionRegistry;
    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final LstmDataPreprocessor dataPreprocessor;

    public LstmPredictionResultDto predictNext(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        String code = stockCode.trim();
        LstmPredictionResultDto result = predictNextBatch(List.of(code)).get(code);
        if (result == null) {
            throw new IllegalStateException("LSTM 未能生成股票预测: " + code);
        }
        return result;
    }

    public Map<String, LstmPredictionResultDto> predictNextBatch(List<String> stockCodes) {
        List<String> codes = normalizeCodes(stockCodes);
        if (codes.isEmpty()) {
            return Map.of();
        }

        int fetchDays = Math.max(
                modelConfig.getSequenceLength() + 21,
                modelConfig.getSequenceLength() * 2);
        Map<String, List<StockPrice>> priceSeries = loadPriceSeries(codes, fetchDays);
        Map<String, LstmDataPreprocessor.StockContext> contexts = loadStockContexts(codes);
        List<String> validCodes = new ArrayList<>();
        List<float[][]> inputs = new ArrayList<>();
        List<Double> lastClosePrices = new ArrayList<>();
        for (String code : codes) {
            LstmDataPreprocessor.PredictionInput input = dataPreprocessor.buildPredictionInput(
                    priceSeries.getOrDefault(code, List.of()),
                    contexts.getOrDefault(code, LstmDataPreprocessor.StockContext.basic(code)));
            if (input == null) {
                log.warn("跳过 LSTM 输入不足股票: stockCode={}", code);
                continue;
            }
            validCodes.add(code);
            inputs.add(input.getInput());
            lastClosePrices.add(input.getLastClosePrice());
        }
        if (validCodes.isEmpty()) {
            return Map.of();
        }

        try {
            OnnxSessionRegistry.SessionHandle handle = sessionRegistry.get(
                    Path.of(onnxConfig.getLstmArtifactDir()));
            validateCompatibility(handle.artifact().metadata());
            int sequenceLength = inputs.get(0).length;
            int featureCount = inputs.get(0)[0].length;
            float[] flattened = flatten(inputs, sequenceLength, featureCount);
            String inputName = handle.artifact().metadata().getInputNames().get(0);
            try (OnnxTensor tensor = OnnxTensor.createTensor(
                    handle.environment(),
                    FloatBuffer.wrap(flattened),
                    new long[]{inputs.size(), sequenceLength, featureCount});
                 var output = handle.session().run(Map.of(inputName, tensor))) {
                float[][] values = (float[][]) output.get(0).getValue();
                if (values.length != validCodes.size()) {
                    throw new IllegalStateException("LSTM ONNX 批量输出数量不匹配");
                }
                return buildResults(validCodes, lastClosePrices, values,
                        handle.artifact().metadata().getModelVersion());
            }
        } catch (Exception exception) {
            log.error("LSTM ONNX 批量预测失败", exception);
            throw new IllegalStateException("LSTM ONNX 批量预测失败：" + exception.getMessage(), exception);
        }
    }

    public boolean hasModel() {
        try {
            OnnxSessionRegistry.SessionHandle handle = sessionRegistry.get(
                    Path.of(onnxConfig.getLstmArtifactDir()));
            validateCompatibility(handle.artifact().metadata());
            return true;
        } catch (RuntimeException exception) {
            log.warn("LSTM ONNX 制品不可用: {}", exception.getMessage());
            return false;
        }
    }

    private Map<String, LstmPredictionResultDto> buildResults(
            List<String> codes, List<Double> lastClosePrices, float[][] outputs, String modelVersion) {
        Map<String, LstmPredictionResultDto> results = new LinkedHashMap<>();
        for (int index = 0; index < codes.size(); index++) {
            if (outputs[index].length != OUTPUT_SIZE) {
                throw new IllegalStateException("LSTM ONNX 输出维度不匹配");
            }
            float scaledReturn = outputs[index][0];
            float directionProbability = outputs[index][1];
            float downsideRisk = outputs[index][2];
            if (!Float.isFinite(scaledReturn) || !Float.isFinite(directionProbability)
                    || !Float.isFinite(downsideRisk)) {
                log.warn("跳过 LSTM ONNX 输出无效股票: stockCode={}", codes.get(index));
                continue;
            }
            double predictedReturn = scaledReturn * modelConfig.getTargetReturnScale();
            double lastClose = lastClosePrices.get(index);
            results.put(codes.get(index), LstmPredictionResultDto.builder()
                    .stockCode(codes.get(index))
                    .predictedClosePrice(lastClose > 0D ? lastClose * (1D + predictedReturn) : 0D)
                    .lastClosePrice(lastClose > 0D ? lastClose : null)
                    .predictedChangeRatio(predictedReturn)
                    .directionProbability((double) directionProbability)
                    .downsideRisk((double) downsideRisk)
                    .modelId(modelVersion)
                    .build());
        }
        log.info("LSTM ONNX 批量预测完成: requested={}, succeeded={}, modelVersion={}",
                codes.size(), results.size(), modelVersion);
        return results;
    }

    private void validateCompatibility(OnnxModelMetadata metadata) {
        if (!LstmDataPreprocessor.FEATURE_VERSION.equals(metadata.getFeatureVersion())) {
            throw new IllegalStateException("LSTM ONNX 特征版本不兼容");
        }
        requireNumber(metadata, "sequenceLength", modelConfig.getSequenceLength());
        requireNumber(metadata, "inputSize", modelConfig.getInputSize());
        requireNumber(metadata, "outputSize", OUTPUT_SIZE);
        requireNumber(metadata, "stockEmbeddingBuckets", modelConfig.getStockEmbeddingBuckets());
        requireNumber(metadata, "industryEmbeddingBuckets", modelConfig.getIndustryEmbeddingBuckets());
        requireNumber(metadata, "groupHeadCount", modelConfig.getGroupHeadCount());
    }

    private void requireNumber(OnnxModelMetadata metadata, String key, int expected) {
        Object raw = metadata.getCompatibility().get(key);
        if (!(raw instanceof Number number) || number.intValue() != expected) {
            throw new IllegalStateException("LSTM ONNX 元数据不兼容: " + key);
        }
    }

    private List<String> normalizeCodes(List<String> stockCodes) {
        return stockCodes == null ? List.of() : stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
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

    private Map<String, List<StockPrice>> loadPriceSeries(List<String> stockCodes, int days) {
        Map<String, List<StockPrice>> result = new LinkedHashMap<>();
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, days));
        for (String stockCode : stockCodes) {
            List<StockPrice> prices = new ArrayList<>(
                    priceRepository.findByCodeOrderByDateDesc(stockCode, pageRequest));
            if (!prices.isEmpty()) {
                Collections.reverse(prices);
                result.put(stockCode, prices);
            }
        }
        return result;
    }

    private Map<String, LstmDataPreprocessor.StockContext> loadStockContexts(List<String> stockCodes) {
        return stockInfoRepository.findByCodeIn(stockCodes).stream()
                .collect(Collectors.toMap(
                        StockInfo::getCode,
                        stock -> LstmDataPreprocessor.StockContext.builder()
                                .stockCode(stock.getCode())
                                .industryCode(stock.getIndustryCode())
                                .market(stock.getMarket())
                                .build(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }
}
// AI_GENERATE_END --
