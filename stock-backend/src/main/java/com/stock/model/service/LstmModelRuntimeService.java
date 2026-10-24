// AI_GENERATE_START-------
package com.stock.model.service;

import ai.djl.Model;
import ai.djl.MalformedModelException;
import ai.djl.nn.Block;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.training.ParameterStore;
import com.stock.market.dto.StockPriceItemDto;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.LstmPredictionResultDto;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.algorithm.StockLSTMModel;
import com.stock.model.algorithm.MasterTransformerModel;
import com.stock.model.algorithm.RankingLSTMModel;
import com.stock.model.algorithm.LstmArchitectureType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
    private final RankingLstmTrainingDataService rankingTrainingDataService;
    private final RankingLstmDataPreprocessor rankingDataPreprocessor;
    private final ObjectMapper objectMapper;
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
                Block block = createModelBlock(artifact.metadata());
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
        Properties metadata = loadMetadata(artifact.metadata());
        boolean rankingModel = LstmModelArtifactService.MODEL_VERSION.equals(
                metadata.getProperty("modelVersion"));
        int sequenceLength = intMetadata(metadata, "sequenceLength", config.getSequenceLength());
        int fetchDays = Math.max(sequenceLength + 21, sequenceLength * 2);
        PredictionBatchInput preparedInput = rankingModel
                ? buildRankingBatchInput(codes, fetchDays, baseTradeDate, metadata)
                : buildLegacyBatchInput(codes, fetchDays, baseTradeDate);
        if (preparedInput.validCodes().isEmpty()) {
            return;
        }
        int effectiveBatchSize = resolveForwardBatchSize(metadata, batchSize);

        try (NDManager manager = NDManager.newBaseManager("PyTorch");
             Model model = Model.newInstance("lstm-global-predict", "PyTorch")) {
            Block block = createModelBlock(artifact.metadata());
            model.setBlock(block);
            try (ByteArrayInputStream input = new ByteArrayInputStream(artifact.params());
                 DataInputStream dataInput = new DataInputStream(input)) {
                block.loadParameters(manager, dataInput);
            }

            int succeeded = 0;
            for (int start = 0; start < preparedInput.validCodes().size(); start += effectiveBatchSize) {
                int end = Math.min(preparedInput.validCodes().size(), start + effectiveBatchSize);
                PredictionBatchInput currentInput = slice(preparedInput, start, end);
                Map<String, LstmPredictionResultDto> chunkResults = predictLoadedModelBatch(
                        currentInput, artifact, metadata, manager, model);
                succeeded += chunkResults.size();
                batchConsumer.accept(currentInput.validCodes(), chunkResults);
            }
            log.info("共享 LSTM 分块预测完成: requested={}, succeeded={}, batchSize={}, modelVersion={}",
                    codes.size(), succeeded, effectiveBatchSize, MODEL_VERSION);
        } catch (CancellationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("共享 LSTM 批量预测失败", exception);
            throw new IllegalStateException("共享 LSTM 批量预测失败：" + exception.getMessage(), exception);
        }
    }

    private Map<String, LstmPredictionResultDto> predictLoadedModelBatch(
            PredictionBatchInput batchInput,
            LstmModelArtifactService.ModelArtifact artifact,
            Properties metadata,
            NDManager manager,
            Model model) {
        boolean rankingModel = LstmModelArtifactService.MODEL_VERSION.equals(
                metadata.getProperty("modelVersion"));
        List<String> validCodes = batchInput.validCodes();
        List<float[][]> inputs = batchInput.inputs();
        List<Double> lastClosePrices = batchInput.lastClosePrices();
        List<LocalDate> baseTradeDates = batchInput.baseTradeDates();
        if (validCodes.isEmpty()) {
            return Map.of();
        }

        float[][] flattened = flattenInputs(inputs.toArray(float[][][]::new));
        try (NDManager batchManager = manager.newSubManager()) {
            NDArray inputArray = batchManager.create(flattened);
            ParameterStore parameterStore = new ParameterStore(manager, false);
            try (NDList output = model.getBlock().forward(parameterStore, new NDList(inputArray), false)) {
                float[] values = output.singletonOrThrow().toFloatArray();
                int outputSize = rankingModel ? RankingLSTMModel.OUTPUT_SIZE : StockLSTMModel.OUTPUT_SIZE;
                int expectedOutputCount = validCodes.size() * outputSize;
                if (values.length != expectedOutputCount) {
                    throw new IllegalStateException("共享 LSTM 多任务批量输出数量不匹配");
                }
                return rankingModel
                        ? mapRankingResults(validCodes, lastClosePrices, baseTradeDates,
                                artifact.modelId(), metadata, values)
                        : mapLegacyResults(validCodes, lastClosePrices, baseTradeDates,
                                artifact.modelId(), values);
            }
        }
    }

    private int resolveForwardBatchSize(Properties metadata, int requestedBatchSize) {
        if (LstmArchitectureType.MASTER_TRANSFORMER.name()
                .equals(metadata.getProperty("architectureType"))) {
            return intMetadata(metadata, "crossSectionBatchSize", config.getBatchSize());
        }
        return requestedBatchSize;
    }

    private PredictionBatchInput slice(PredictionBatchInput source, int start, int end) {
        return new PredictionBatchInput(
                List.copyOf(source.validCodes().subList(start, end)),
                List.copyOf(source.inputs().subList(start, end)),
                List.copyOf(source.lastClosePrices().subList(start, end)),
                List.copyOf(source.baseTradeDates().subList(start, end)));
    }

    private PredictionBatchInput buildLegacyBatchInput(
            List<String> codes, int fetchDays, LocalDate baseTradeDate) {
        Map<String, List<StockPriceItemDto>> priceSeries = trainingDataService.loadPriceSeries(
                codes, fetchDays, baseTradeDate);
        Map<String, LstmDataPreprocessor.StockContext> contexts = trainingDataService.loadStockContexts(codes);
        List<String> validCodes = new ArrayList<>();
        List<float[][]> inputs = new ArrayList<>();
        List<Double> lastClosePrices = new ArrayList<>();
        List<LocalDate> baseTradeDates = new ArrayList<>();
        for (String code : codes) {
            List<StockPriceItemDto> prices = priceSeries.getOrDefault(code, List.of());
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
        return new PredictionBatchInput(validCodes, inputs, lastClosePrices, baseTradeDates);
    }

    private PredictionBatchInput buildRankingBatchInput(
            List<String> codes, int fetchDays, LocalDate baseTradeDate, Properties metadata) {
        RankingLstmTrainingDataService.RankingTrainingDataBundle bundle =
                rankingTrainingDataService.loadPredictionData(codes, fetchDays, baseTradeDate);
        Map<String, Integer> stockDictionary = jsonMapMetadata(metadata, "stockDictionaryJson");
        Map<String, Integer> industryDictionary = jsonMapMetadata(metadata, "industryDictionaryJson");
        Map<String, Integer> marketDictionary = jsonMapMetadata(metadata, "marketDictionaryJson");
        double[] means = jsonArrayMetadata(metadata, "featureMeansJson");
        double[] deviations = jsonArrayMetadata(metadata, "featureStdDeviationsJson");
        Map<String, LstmDataPreprocessor.PredictionInput> predictionInputs =
                rankingDataPreprocessor.buildPredictionInputs(bundle, stockDictionary,
                        industryDictionary, marketDictionary, means, deviations);
        List<String> validCodes = new ArrayList<>();
        List<float[][]> inputs = new ArrayList<>();
        List<Double> lastClosePrices = new ArrayList<>();
        List<LocalDate> baseTradeDates = new ArrayList<>();
        for (String code : codes) {
            LstmDataPreprocessor.PredictionInput input = predictionInputs.get(code);
            if (input == null) {
                log.warn("跳过 v5 LSTM 输入不足股票: stockCode={}", code);
                continue;
            }
            if (baseTradeDate != null && !baseTradeDate.equals(input.getBaseTradeDate())) {
                log.warn("跳过 v5 历史基准日不一致股票: stockCode={}, expectedBaseDate={}, actualBaseDate={}",
                        code, baseTradeDate, input.getBaseTradeDate());
                continue;
            }
            validCodes.add(code);
            inputs.add(input.getInput());
            lastClosePrices.add(input.getLastClosePrice());
            baseTradeDates.add(input.getBaseTradeDate());
        }
        return new PredictionBatchInput(validCodes, inputs, lastClosePrices, baseTradeDates);
    }

    private Map<String, LstmPredictionResultDto> mapRankingResults(
            List<String> codes,
            List<Double> lastClosePrices,
            List<LocalDate> baseTradeDates,
            String modelId,
            Properties metadata,
            float[] values) {
        Map<String, LstmPredictionResultDto> results = new LinkedHashMap<>();
        double returnScale = doubleMetadata(metadata, "targetReturnScale", config.getTargetReturnScale());
        double temperature = doubleMetadata(metadata, "probabilityTemperature", 1D);
        for (int index = 0; index < codes.size(); index++) {
            int offset = index * RankingLSTMModel.OUTPUT_SIZE;
            float rankScore = values[offset];
            float scaledReturn = values[offset + 1];
            float rawProbability = values[offset + 2];
            float downsideQuantile = values[offset + 3];
            if (!Float.isFinite(rankScore) || !Float.isFinite(scaledReturn)
                    || !Float.isFinite(rawProbability) || !Float.isFinite(downsideQuantile)) {
                log.warn("跳过 v5 LSTM 输出无效股票: stockCode={}", codes.get(index));
                continue;
            }
            double expectedExcessReturn = scaledReturn * returnScale;
            double calibratedProbability = calibrateProbability(rawProbability, temperature);
            double lastClose = lastClosePrices.get(index);
            results.put(codes.get(index), LstmPredictionResultDto.builder()
                    .stockCode(codes.get(index))
                    .baseTradeDate(baseTradeDates.get(index))
                    .predictedClosePrice(lastClose > 0D ? lastClose * (1D + expectedExcessReturn) : 0D)
                    .lastClosePrice(lastClose > 0D ? lastClose : null)
                    .predictedChangeRatio(expectedExcessReturn)
                    .directionProbability(calibratedProbability)
                    .downsideRisk((double) downsideQuantile)
                    .rankScore((double) rankScore)
                    .expectedExcessReturn(expectedExcessReturn)
                    .calibratedUpProbability(calibratedProbability)
                    .downsideQuantile((double) downsideQuantile)
                    .architectureType(metadata.getProperty("architectureType"))
                    .featureSchemaVersion(metadata.getProperty("featureVersion"))
                    .modelId(modelId)
                    .build());
        }
        return results;
    }

    private Map<String, LstmPredictionResultDto> mapLegacyResults(
            List<String> codes,
            List<Double> lastClosePrices,
            List<LocalDate> baseTradeDates,
            String modelId,
            float[] values) {
        Map<String, LstmPredictionResultDto> results = new LinkedHashMap<>();
        for (int index = 0; index < codes.size(); index++) {
            int offset = index * StockLSTMModel.OUTPUT_SIZE;
            float scaledReturn = values[offset];
            float probability = values[offset + 1];
            float downsideRisk = values[offset + 2];
            if (!Float.isFinite(scaledReturn) || !Float.isFinite(probability) || !Float.isFinite(downsideRisk)) {
                log.warn("跳过历史 LSTM 输出无效股票: stockCode={}", codes.get(index));
                continue;
            }
            double predictedReturn = scaledReturn * config.getTargetReturnScale();
            double lastClose = lastClosePrices.get(index);
            results.put(codes.get(index), LstmPredictionResultDto.builder()
                    .stockCode(codes.get(index))
                    .baseTradeDate(baseTradeDates.get(index))
                    .predictedClosePrice(lastClose > 0D ? lastClose * (1D + predictedReturn) : 0D)
                    .lastClosePrice(lastClose > 0D ? lastClose : null)
                    .predictedChangeRatio(predictedReturn)
                    .directionProbability((double) probability)
                    .downsideRisk((double) downsideRisk)
                    .architectureType(LstmArchitectureType.LEGACY_MULTITASK_LSTM.name())
                    .featureSchemaVersion(LstmDataPreprocessor.FEATURE_VERSION)
                    .modelId(modelId)
                    .build());
        }
        return results;
    }

    private double calibrateProbability(double probability, double temperature) {
        double safeProbability = Math.max(1.0E-7D, Math.min(1D - 1.0E-7D, probability));
        double safeTemperature = !Double.isFinite(temperature) || temperature <= 0D ? 1D : temperature;
        double logit = Math.log(safeProbability / (1D - safeProbability));
        return 1D / (1D + Math.exp(-logit / safeTemperature));
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

    private Block createModelBlock(String metadataText) {
        Properties metadata = loadMetadata(metadataText);
        if (LstmModelArtifactService.MODEL_VERSION.equals(metadata.getProperty("modelVersion"))) {
            LstmArchitectureType architectureType = LstmArchitectureType.valueOf(
                    metadata.getProperty("architectureType"));
            if (architectureType == LstmArchitectureType.MASTER_TRANSFORMER) {
                return new MasterTransformerModel(
                        intMetadata(metadata, "inputSize", RankingLstmDataPreprocessor.FEATURE_COUNT),
                        intMetadata(metadata, "hiddenSize", config.getHiddenSize()),
                        intMetadata(metadata, "sequenceLength", config.getSequenceLength()));
            }
            return new RankingLSTMModel(
                    architectureType,
                    intMetadata(metadata, "inputSize", RankingLstmDataPreprocessor.FEATURE_COUNT),
                    intMetadata(metadata, "hiddenSize", config.getHiddenSize()),
                    intMetadata(metadata, "numLayers", config.getNumLayers()),
                    (float) config.getDropout(),
                    intMetadata(metadata, "sequenceLength", config.getSequenceLength()),
                    intMetadata(metadata, "stockDictionarySize", 2),
                    intMetadata(metadata, "stockEmbeddingSize", config.getStockEmbeddingSize()),
                    intMetadata(metadata, "industryDictionarySize", 2),
                    intMetadata(metadata, "industryEmbeddingSize", config.getIndustryEmbeddingSize()),
                    intMetadata(metadata, "marketDictionarySize", 2),
                    intMetadata(metadata, "marketEmbeddingSize", config.getMarketEmbeddingSize()));
        }
        return new StockLSTMModel(
                intMetadata(metadata, "inputSize", LstmDataPreprocessor.FEATURE_COUNT),
                intMetadata(metadata, "hiddenSize", 50),
                intMetadata(metadata, "numLayers", 2),
                (float) config.getDropout(),
                intMetadata(metadata, "sequenceLength", 60),
                intMetadata(metadata, "stockEmbeddingBuckets", 8192),
                intMetadata(metadata, "stockEmbeddingSize", 16),
                intMetadata(metadata, "industryEmbeddingBuckets", 512),
                intMetadata(metadata, "industryEmbeddingSize", 8),
                intMetadata(metadata, "groupHeadCount", 8),
                Boolean.parseBoolean(metadata.getProperty("residualHeadEnabled", "true")),
                floatMetadata(metadata, "groupHeadScale", 0.1F),
                floatMetadata(metadata, "residualHeadScale", 0.1F));
    }

    private Properties loadMetadata(String metadataText) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(metadataText));
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("共享 LSTM 元数据无法解析", exception);
        }
    }

    private Map<String, Integer> jsonMapMetadata(Properties metadata, String key) {
        try {
            return objectMapper.readValue(metadata.getProperty(key), new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("共享 LSTM 字典元数据无法解析: " + key, exception);
        }
    }

    private double[] jsonArrayMetadata(Properties metadata, String key) {
        try {
            return objectMapper.readValue(metadata.getProperty(key), double[].class);
        } catch (Exception exception) {
            throw new IllegalStateException("共享 LSTM 标准化元数据无法解析: " + key, exception);
        }
    }

    private int intMetadata(Properties metadata, String key, int fallback) {
        try {
            return Integer.parseInt(metadata.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private float floatMetadata(Properties metadata, String key, float fallback) {
        try {
            return Float.parseFloat(metadata.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private double doubleMetadata(Properties metadata, String key, double fallback) {
        try {
            return Double.parseDouble(metadata.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
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

    private record PredictionBatchInput(
            List<String> validCodes,
            List<float[][]> inputs,
            List<Double> lastClosePrices,
            List<LocalDate> baseTradeDates) {
    }
}
// AI_GENERATE_END-------
