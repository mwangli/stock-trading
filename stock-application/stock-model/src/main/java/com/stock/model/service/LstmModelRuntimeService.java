// AI_GENERATE_START---
package com.stock.model.service;

import ai.djl.Model;
import ai.djl.MalformedModelException;
import ai.djl.nn.Block;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.training.ParameterStore;
import com.stock.data.entity.StockPrice;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.LstmPredictionResultDto;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.model.StockLSTMModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;

/**
 * LSTM 激活模型运行时服务。
 *
 * 负责激活制品预加载、在线推理和版本有效性检查，不承担模型训练。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LstmModelRuntimeService {

    private static final String MODEL_VERSION = LstmModelArtifactService.MODEL_VERSION;

    private final LstmTrainingConfig config;
    private final LstmDataPreprocessor dataPreprocessor;
    private final LstmTrainingDataService trainingDataService;
    private final LstmModelArtifactService artifactService;
    private volatile LstmModelArtifactService.ModelArtifact activeArtifact;

    /**
     * 预加载并校验当前激活模型。
     */
    public void preloadActiveModel() {
        activeArtifact = null;
        try {
            LstmModelArtifactService.ModelArtifact artifact = artifactService.loadArtifact(null);
            artifactService.validateMetadata(artifact.metadata());
            try (ai.djl.ndarray.NDManager manager = ai.djl.ndarray.NDManager.newBaseManager("PyTorch");
                 Model model = Model.newInstance("lstm-global-preload", "PyTorch")) {
                StockLSTMModel block = createModelBlock();
                model.setBlock(block);
                try (ByteArrayInputStream input = new ByteArrayInputStream(artifact.params());
                     DataInputStream dataInput = new DataInputStream(input)) {
                    block.loadParameters(manager, dataInput);
                }
            }
            log.info("当前激活共享 LSTM 模型预加载校验成功: modelId={}", artifact.modelId());
        } catch (RuntimeException | IOException | MalformedModelException exception) {
            log.warn("当前激活共享 LSTM 模型预加载失败，后续状态检查将显示不可用: {}", exception.getMessage());
        }
    }

    /**
     * 预测单只股票下一交易日结果。
     *
     * @param stockCode 股票代码
     * @return 预测结果
     */
    public LstmPredictionResultDto predictNext(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        String code = stockCode.trim();
        LstmPredictionResultDto result = predictNextBatch(List.of(code)).get(code);
        if (result == null) {
            throw new IllegalStateException("共享 LSTM 未能生成股票预测: " + code);
        }
        return result;
    }

    /**
     * 使用激活模型批量预测。
     *
     * @param stockCodes 股票代码
     * @return 股票代码到预测结果的映射
     */
    public Map<String, LstmPredictionResultDto> predictNextBatch(List<String> stockCodes) {
        return predictNextBatch(stockCodes, null);
    }

    /**
     * 使用指定模型版本批量预测。
     *
     * @param stockCodes 股票代码
     * @param modelVersionId 模型版本 ID
     * @return 股票代码到预测结果的映射
     */
    public Map<String, LstmPredictionResultDto> predictNextBatch(
            List<String> stockCodes, String modelVersionId) {
        return predictNextBatch(stockCodes, modelVersionId, null);
    }

    /**
     * 使用指定模型版本和历史基准日批量预测。
     *
     * @param stockCodes 股票代码
     * @param modelVersionId 模型版本 ID
     * @param baseTradeDate 历史基准交易日
     * @return 股票代码到预测结果的映射
     */
    public Map<String, LstmPredictionResultDto> predictNextBatch(
            List<String> stockCodes, String modelVersionId, LocalDate baseTradeDate) {
        Map<String, LstmPredictionResultDto> results = new LinkedHashMap<>();
        int requestedSize = stockCodes == null ? 0 : stockCodes.size();
        predictNextBatchChunks(stockCodes, modelVersionId, baseTradeDate,
                Math.max(1, requestedSize), (ignoredCodes, chunkResults) -> results.putAll(chunkResults));
        return results;
    }

    /**
     * 使用单次模型加载分块执行预测。
     *
     * @param stockCodes 股票代码
     * @param modelVersionId 模型版本 ID
     * @param baseTradeDate 历史基准交易日
     * @param batchSize 分块大小
     * @param batchConsumer 分块结果消费者
     */
    public void predictNextBatchChunks(
            List<String> stockCodes, String modelVersionId, LocalDate baseTradeDate, int batchSize,
            BiConsumer<List<String>, Map<String, LstmPredictionResultDto>> batchConsumer) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("预测分块大小必须大于 0");
        }
        if (batchConsumer == null) {
            throw new IllegalArgumentException("预测分块结果消费者不能为空");
        }
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return;
        }
        LstmModelArtifactService.ModelArtifact artifact = modelVersionId == null
                ? activeArtifact : null;
        if (artifact == null) {
            artifact = artifactService.loadArtifact(modelVersionId);
        }
        artifactService.validateMetadata(artifact.metadata());

        try (NDManager manager = NDManager.newBaseManager("PyTorch");
             Model model = Model.newInstance("lstm-global-predict", "PyTorch")) {
            Block block = createModelBlock();
            model.setBlock(block);
            try (ByteArrayInputStream input = new ByteArrayInputStream(artifact.params());
                 DataInputStream dataInput = new DataInputStream(input)) {
                block.loadParameters(manager, dataInput);
            }

            int succeeded = 0;
            for (int start = 0; start < codes.size(); start += batchSize) {
                int end = Math.min(codes.size(), start + batchSize);
                List<String> currentCodes = List.copyOf(codes.subList(start, end));
                Map<String, LstmPredictionResultDto> chunkResults = predictLoadedModelBatch(
                        currentCodes, baseTradeDate, artifact.modelId(), manager, model);
                succeeded += chunkResults.size();
                batchConsumer.accept(currentCodes, chunkResults);
            }
            log.info("共享 LSTM 分块预测完成: requested={}, succeeded={}, batchSize={}, modelVersion={}",
                    codes.size(), succeeded, batchSize, MODEL_VERSION);
        } catch (CancellationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("共享 LSTM 批量预测失败", exception);
            throw new IllegalStateException("共享 LSTM 批量预测失败：" + exception.getMessage(), exception);
        }
    }

    private Map<String, LstmPredictionResultDto> predictLoadedModelBatch(
            List<String> codes, LocalDate baseTradeDate, String modelId, NDManager manager, Model model) {
        int fetchDays = Math.max(config.getSequenceLength() + 21, config.getSequenceLength() * 2);
        Map<String, List<StockPrice>> priceSeries = trainingDataService.loadPriceSeries(
                codes, fetchDays, baseTradeDate);
        Map<String, LstmDataPreprocessor.StockContext> contexts = trainingDataService.loadStockContexts(codes);
        List<String> validCodes = new ArrayList<>();
        List<float[][]> inputs = new ArrayList<>();
        List<Double> lastClosePrices = new ArrayList<>();
        List<LocalDate> baseTradeDates = new ArrayList<>();
        for (String code : codes) {
            List<StockPrice> prices = priceSeries.getOrDefault(code, List.of());
            LstmDataPreprocessor.PredictionInput input = dataPreprocessor.buildPredictionInput(
                    prices, contexts.getOrDefault(code, LstmDataPreprocessor.StockContext.basic(code)));
            if (input == null) {
                log.warn("跳过共享 LSTM 输入不足股票: stockCode={}", code);
                continue;
            }
            if (baseTradeDate != null && !baseTradeDate.equals(input.getBaseTradeDate())) {
                log.warn("跳过历史基准日不一致股票: stockCode={}, expectedBaseDate={}, actualBaseDate={}",
                        code, baseTradeDate, input.getBaseTradeDate());
                continue;
            }
            validCodes.add(code);
            inputs.add(input.getInput());
            lastClosePrices.add(input.getLastClosePrice());
            baseTradeDates.add(input.getBaseTradeDate());
        }
        if (validCodes.isEmpty()) {
            return Map.of();
        }

        float[][] flattened = flattenInputs(inputs.toArray(float[][][]::new));
        try (NDManager batchManager = manager.newSubManager()) {
            NDArray inputArray = batchManager.create(flattened);
            ParameterStore parameterStore = new ParameterStore(manager, false);
            try (NDList output = model.getBlock().forward(parameterStore, new NDList(inputArray), false)) {
                float[] values = output.singletonOrThrow().toFloatArray();
                int expectedOutputCount = validCodes.size() * StockLSTMModel.OUTPUT_SIZE;
                if (values.length != expectedOutputCount) {
                    throw new IllegalStateException("共享 LSTM 多任务批量输出数量不匹配");
                }
                Map<String, LstmPredictionResultDto> results = new LinkedHashMap<>();
                for (int index = 0; index < validCodes.size(); index++) {
                    int outputOffset = index * StockLSTMModel.OUTPUT_SIZE;
                    float scaledReturn = values[outputOffset];
                    float directionProbability = values[outputOffset + 1];
                    float downsideRisk = values[outputOffset + 2];
                    if (!Float.isFinite(scaledReturn) || !Float.isFinite(directionProbability)
                            || !Float.isFinite(downsideRisk)) {
                        log.warn("跳过共享 LSTM 多任务输出无效股票: stockCode={}", validCodes.get(index));
                        continue;
                    }
                    double predictedReturn = scaledReturn * config.getTargetReturnScale();
                    double lastClose = lastClosePrices.get(index);
                    results.put(validCodes.get(index), LstmPredictionResultDto.builder()
                            .stockCode(validCodes.get(index))
                            .baseTradeDate(baseTradeDates.get(index))
                            .predictedClosePrice(lastClose > 0D ? lastClose * (1D + predictedReturn) : 0D)
                            .lastClosePrice(lastClose > 0D ? lastClose : null)
                            .predictedChangeRatio(predictedReturn)
                            .directionProbability((double) directionProbability)
                            .downsideRisk((double) downsideRisk)
                            .modelId(modelId)
                            .build());
                }
                return results;
            }
        }
    }

    /**
     * 判断是否存在可加载的激活模型。
     *
     * @return 是否存在可用模型
     */
    public boolean hasSharedModel() {
        try {
            LstmModelArtifactService.ModelArtifact artifact = activeArtifact;
            if (artifact == null) {
                artifact = artifactService.loadArtifact(null);
                artifactService.validateMetadata(artifact.metadata());
                activeArtifact = artifact;
            }
            return true;
        } catch (RuntimeException exception) {
            log.warn("共享 LSTM 模型存在但版本契约无效: {}", exception.getMessage());
            return false;
        }
    }

    /**
     * 判断激活模型是否仍在有效期内。
     *
     * @param maxAgeDays 最大模型年龄
     * @return 是否仍可使用
     */
    public boolean isSharedModelFresh(int maxAgeDays) {
        if (maxAgeDays < 0 || !hasSharedModel()) {
            return false;
        }
        LocalDateTime staleBefore = LocalDateTime.now().minusDays(maxAgeDays);
        try {
            LstmModelDocument document = artifactService.loadActiveDocument();
            return document != null
                    && document.getCreatedAt() != null
                    && document.getCreatedAt().isAfter(staleBefore);
        } catch (RuntimeException exception) {
            log.warn("检查共享 LSTM 模型有效期失败: {}", exception.getMessage());
            return false;
        }
    }

    private StockLSTMModel createModelBlock() {
        return new StockLSTMModel(
                config.getInputSize(),
                config.getHiddenSize(),
                config.getNumLayers(),
                (float) config.getDropout(),
                config.getSequenceLength(),
                config.getStockEmbeddingBuckets(),
                config.getStockEmbeddingSize(),
                config.getIndustryEmbeddingBuckets(),
                config.getIndustryEmbeddingSize(),
                config.getGroupHeadCount(),
                config.isResidualHeadEnabled(),
                (float) config.getGroupHeadScale(),
                (float) config.getResidualHeadScale());
    }

    private float[][] flattenInputs(float[][][] inputs) {
        float[][] flattened = new float[inputs.length][];
        for (int sample = 0; sample < inputs.length; sample++) {
            int sequenceLength = inputs[sample].length;
            int featureCount = inputs[sample][0].length;
            flattened[sample] = new float[sequenceLength * featureCount];
            int targetIndex = 0;
            for (int timeStep = 0; timeStep < sequenceLength; timeStep++) {
                for (int featureIndex = 0; featureIndex < featureCount; featureIndex++) {
                    flattened[sample][targetIndex++] = inputs[sample][timeStep][featureIndex];
                }
            }
        }
        return flattened;
    }
}
// AI_GENERATE_END---
