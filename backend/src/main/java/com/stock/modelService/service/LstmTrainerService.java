// AI_GENERATE_START -----------
package com.stock.modelService.service;

import ai.djl.Device;
import ai.djl.Model;
import ai.djl.engine.Engine;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.GradientCollector;
import ai.djl.training.ParameterStore;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.dataset.Batch;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.domain.entity.ModelActivationDocument;
import com.stock.modelService.model.ModelBinaryCodec;
import com.stock.modelService.model.MultiTaskStockLoss;
import com.stock.modelService.model.StockLSTMModel;
import com.stock.modelService.persistence.LstmModelRepository;
import com.stock.modelService.persistence.ModelActivationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 全市场共享 LSTM 模型训练与推理服务。
 * 收盘后将多只股票分别构造成时序窗口后合并训练一个共享模型，
 * 交易期间只加载当前生效版本并输出下一交易日收益率。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LstmTrainerService {

    /** 当前共享模型固定名称。 */
    public static final String DEFAULT_SHARED_MODEL_NAME = "global-shared-lstm";

    /** 当前网络结构与标签口径版本。 */
    public static final String MODEL_VERSION = "v3-embedding-multitask";

    private final LstmTrainingConfig config;
    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final LstmDataPreprocessor dataPreprocessor;
    private final LstmModelRepository lstmModelRepository;
    private final ModelActivationRepository modelActivationRepository;
    private final ModelBinaryCodec modelBinaryCodec;
    private final ObjectMapper objectMapper;
    private String currentModelPath;

    /**
     * 兼容原有训练调用，将传入股票统一训练为共享基础模型。
     *
     * @param stockCodes 逗号分隔的股票代码
     * @param days 每只股票读取的交易日数量
     * @param epochs 训练轮次，可为空
     * @param batchSize 批次大小，可为空
     * @param learningRate 学习率，可为空
     * @return 训练结果
     */
    public TrainingResult trainModel(String stockCodes, int days, Integer epochs,
                                     Integer batchSize, Double learningRate) {
        if (stockCodes == null || stockCodes.isBlank()) {
            return TrainingResult.builder().success(false).message("股票代码不能为空").build();
        }
        List<String> codes = Stream.of(stockCodes.split(","))
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
        return trainSharedModel(codes, days, epochs, batchSize, learningRate);
    }

    /**
     * 训练全市场共享基础模型。
     *
     * @param stockCodes 参与训练的股票代码
     * @param days 每只股票读取的交易日数量
     * @return 训练结果
     */
    public TrainingResult trainSharedModel(List<String> stockCodes, int days) {
        return trainSharedModel(stockCodes, days, null, null, null);
    }

    /**
     * 训练全市场共享基础模型，并允许离线任务覆盖固定训练参数。
     *
     * @param stockCodes 参与训练的股票代码
     * @param days 每只股票读取的交易日数量
     * @param epochs 训练轮次，可为空
     * @param batchSize 批次大小，可为空
     * @param learningRate 学习率，可为空
     * @return 训练结果
     */
    public TrainingResult trainSharedModel(List<String> stockCodes, int days, Integer epochs,
                                           Integer batchSize, Double learningRate) {
        String trainingId = "shared_training_" + System.currentTimeMillis();
        try {
            List<String> codes = stockCodes == null ? List.of() : stockCodes.stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (codes.isEmpty()) {
                return TrainingResult.builder().success(false).message("没有可训练股票").build();
            }

            int trainEpochs = epochs != null ? epochs : config.getEpochs();
            int trainBatchSize = batchSize != null ? batchSize : config.getBatchSize();
            float trainLearningRate = learningRate != null
                    ? learningRate.floatValue()
                    : (float) config.getLearningRate();
            int fetchDays = Math.max(days, config.getSequenceLength() + config.getValidationGap() + 2);

            log.info("开始共享 LSTM 离线训练: stocks={}, days={}, epochs={}, batchSize={}, learningRate={}",
                    codes.size(), fetchDays, trainEpochs, trainBatchSize, trainLearningRate);

            Map<String, List<StockPrice>> priceSeries = loadPriceSeries(codes, fetchDays);
            Map<String, LstmDataPreprocessor.StockContext> contexts = loadStockContexts(codes);
            LstmDataPreprocessor.ProcessedData processedData =
                    dataPreprocessor.processPanelData(priceSeries, contexts);
            if (processedData == null || processedData.getTrainSamples().isEmpty()) {
                return TrainingResult.builder().success(false).message("共享训练数据不足").build();
            }

            int trainSize = processedData.getTrainSamples().size();
            int valSize = processedData.getValSamples().size();
            List<Map<String, Object>> trainingLog = new ArrayList<>();
            byte[] bestParams = null;
            double bestValLoss = Double.MAX_VALUE;
            double bestTrainLoss = Double.MAX_VALUE;
            int bestEpoch = 0;
            int patienceCounter = 0;

            try (Model model = Model.newInstance("lstm-global-shared", "PyTorch")) {
                StockLSTMModel block = createModelBlock();
                model.setBlock(block);
                Optimizer optimizer = Optimizer.adam()
                        .optLearningRateTracker(Tracker.fixed(trainLearningRate))
                        .optWeightDecays(0.001f)
                        .build();
                Device[] availableDevices = Engine.getInstance().getDevices(1);
                Device device = availableDevices.length == 0 ? Device.cpu() : availableDevices[0];
                DefaultTrainingConfig trainingConfig = new DefaultTrainingConfig(new MultiTaskStockLoss(
                        (float) config.getReturnLossWeight(),
                        (float) config.getDirectionLossWeight(),
                        (float) config.getDownsideLossWeight()))
                        .optOptimizer(optimizer)
                        .optDevices(new Device[]{device})
                        .addTrainingListeners(TrainingListener.Defaults.logging());

                try (Trainer trainer = model.newTrainer(trainingConfig)) {
                    int initBatchSize = Math.min(trainBatchSize, Math.max(1, trainSize));
                    trainer.initialize(new Shape(initBatchSize,
                            config.getSequenceLength() * config.getInputSize()));
                    ArrayDataset trainDataset = createDataset(
                            trainer.getManager(),
                            flattenInputs(processedData.getTrainInputs()),
                            processedData.getTrainTargets(),
                            trainBatchSize);
                    ArrayDataset valDataset = createDataset(
                            trainer.getManager(),
                            flattenInputs(processedData.getValInputs()),
                            processedData.getValTargets(),
                            Math.min(trainBatchSize, Math.max(1, valSize)));

                    for (int epoch = 0; epoch < trainEpochs; epoch++) {
                        float trainLoss = trainEpoch(trainer, trainDataset);
                        float valLoss = valDataset == null ? trainLoss : evaluateModel(trainer, valDataset);
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("epoch", epoch + 1);
                        entry.put("trainLoss", (double) trainLoss);
                        entry.put("valLoss", (double) valLoss);
                        trainingLog.add(entry);
                        log.info("共享 LSTM Epoch {}/{}: trainLoss={}, valLoss={}",
                                epoch + 1, trainEpochs, trainLoss, valLoss);

                        if (valLoss < bestValLoss - config.getMinDelta()) {
                            bestValLoss = valLoss;
                            bestTrainLoss = trainLoss;
                            bestEpoch = epoch + 1;
                            bestParams = modelBinaryCodec.serialize(model);
                            patienceCounter = 0;
                        } else if (config.isEarlyStopping()) {
                            patienceCounter++;
                            if (patienceCounter >= config.getPatience()) {
                                log.info("共享 LSTM 早停: bestEpoch={}, patience={}", bestEpoch, patienceCounter);
                                break;
                            }
                        }
                    }

                    if (bestParams == null) {
                        bestParams = modelBinaryCodec.serialize(model);
                        bestEpoch = trainingLog.size();
                        Map<String, Object> last = trainingLog.get(trainingLog.size() - 1);
                        bestTrainLoss = (double) last.get("trainLoss");
                        bestValLoss = (double) last.get("valLoss");
                    }
                }
            }

            currentModelPath = saveModel(bestParams, processedData, bestEpoch,
                    bestTrainLoss, bestValLoss);
            return TrainingResult.builder()
                    .success(true)
                    .message("共享基础模型训练完成")
                    .trainingId(trainingId)
                    .epochs(bestEpoch)
                    .trainLoss(bestTrainLoss)
                    .valLoss(bestValLoss)
                    .modelPath(currentModelPath)
                    .trainSamples(trainSize)
                    .valSamples(valSize)
                    .details(trainingLog)
                    .build();
        } catch (Exception exception) {
            log.error("共享 LSTM 训练失败", exception);
            return TrainingResult.builder()
                    .success(false)
                    .trainingId(trainingId)
                    .message("共享 LSTM 训练失败：" + exception.getMessage())
                    .build();
        }
    }

    /**
     * 使用当前共享基础模型预测单只股票下一交易日收益率和收盘价。
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
     * 批量预测股票下一交易日收益率，共享模型只加载和前向传播一次。
     * 单只股票数据不足时跳过该股票，模型制品异常时终止整批预测。
     *
     * @param stockCodes 股票代码集合
     * @return 股票代码到预测结果的映射
     */
    public Map<String, LstmPredictionResultDto> predictNextBatch(List<String> stockCodes) {
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        ModelArtifact artifact = loadCurrentArtifact();
        validateMetadata(artifact.metadata());

        int fetchDays = Math.max(config.getSequenceLength() + 21, config.getSequenceLength() * 2);
        Map<String, List<StockPrice>> priceSeries = loadPriceSeries(codes, fetchDays);
        Map<String, LstmDataPreprocessor.StockContext> contexts = loadStockContexts(codes);
        List<String> validCodes = new ArrayList<>();
        List<float[][]> inputs = new ArrayList<>();
        List<Double> lastClosePrices = new ArrayList<>();
        for (String code : codes) {
            List<StockPrice> prices = priceSeries.getOrDefault(code, List.of());
            LstmDataPreprocessor.PredictionInput input = dataPreprocessor.buildPredictionInput(
                    prices,
                    contexts.getOrDefault(code, LstmDataPreprocessor.StockContext.basic(code)));
            if (input == null) {
                log.warn("跳过共享 LSTM 输入不足股票: stockCode={}", code);
                continue;
            }
            validCodes.add(code);
            inputs.add(input.getInput());
            lastClosePrices.add(input.getLastClosePrice());
        }
        if (validCodes.isEmpty()) {
            return Map.of();
        }

        try (NDManager manager = NDManager.newBaseManager("PyTorch");
             Model model = Model.newInstance("lstm-global-predict", "PyTorch")) {
            Block block = createModelBlock();
            model.setBlock(block);
            try (ByteArrayInputStream input = new ByteArrayInputStream(artifact.params());
                 DataInputStream dataInput = new DataInputStream(input)) {
                block.loadParameters(manager, dataInput);
            }

            float[][][] batchInputs = inputs.toArray(float[][][]::new);
            float[][] flattened = flattenInputs(batchInputs);
            NDArray inputArray = manager.create(flattened);
            ParameterStore parameterStore = new ParameterStore(manager, false);
            try (NDList output = model.getBlock().forward(
                    parameterStore, new NDList(inputArray), false)) {
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
                    double predictedPrice = lastClose > 0D
                            ? lastClose * (1D + predictedReturn)
                            : 0D;
                    results.put(validCodes.get(index), LstmPredictionResultDto.builder()
                            .stockCode(validCodes.get(index))
                            .predictedClosePrice(predictedPrice)
                            .lastClosePrice(lastClose > 0D ? lastClose : null)
                            .predictedChangeRatio(predictedReturn)
                            .directionProbability((double) directionProbability)
                            .downsideRisk((double) downsideRisk)
                            .modelId(artifact.modelId())
                            .build());
                }
                log.info("共享 LSTM 批量预测完成: requested={}, succeeded={}, modelVersion={}",
                        codes.size(), results.size(), MODEL_VERSION);
                return results;
            }
        } catch (Exception exception) {
            log.error("共享 LSTM 批量预测失败", exception);
            throw new IllegalStateException("共享 LSTM 批量预测失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 判断当前存储介质是否已有可用共享模型。
     *
     * @return true 表示共享模型制品存在
     */
    public boolean hasSharedModel() {
        try {
            ModelArtifact artifact = loadCurrentArtifact();
            validateMetadata(artifact.metadata());
            return true;
        } catch (RuntimeException exception) {
            log.warn("共享 LSTM 模型存在但版本契约无效: {}", exception.getMessage());
            return false;
        }
    }

    /**
     * 判断当前共享模型是否版本兼容且仍在有效期内。
     *
     * @param maxAgeDays 最大允许模型天数
     * @return true 表示当前模型可继续使用
     */
    public boolean isSharedModelFresh(int maxAgeDays) {
        if (maxAgeDays < 0 || !hasSharedModel()) {
            return false;
        }
        LocalDateTime staleBefore = LocalDateTime.now().minusDays(maxAgeDays);
        try {
            LstmModelDocument document = loadActiveDocument();
            return document != null
                    && document.getCreatedAt() != null
                    && document.getCreatedAt().isAfter(staleBefore);
        } catch (RuntimeException exception) {
            log.warn("检查共享 LSTM 模型有效期失败: {}", exception.getMessage());
            return false;
        }
    }

    /**
     * 获取最近一次训练保存路径。
     *
     * @return 模型保存标识
     */
    public String getCurrentModelPath() {
        return currentModelPath;
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

    private float trainEpoch(Trainer trainer, ArrayDataset trainDataset)
            throws IOException, ai.djl.translate.TranslateException {
        float totalLoss = 0F;
        int batchCount = 0;
        Loss loss = trainer.getLoss();
        for (Batch batch : trainer.iterateDataset(trainDataset)) {
            try {
                try (GradientCollector collector = trainer.newGradientCollector();
                     NDList predictions = trainer.forward(batch.getData());
                     NDArray lossValue = loss.evaluate(batch.getLabels(), predictions)) {
                    collector.backward(lossValue);
                    totalLoss += lossValue.toFloatArray()[0];
                }
                trainer.step();
                batchCount++;
            } finally {
                batch.close();
            }
        }
        return batchCount == 0 ? totalLoss : totalLoss / batchCount;
    }

    private float evaluateModel(Trainer trainer, ArrayDataset dataset)
            throws IOException, ai.djl.translate.TranslateException {
        float totalLoss = 0F;
        int batchCount = 0;
        Loss loss = trainer.getLoss();
        for (Batch batch : trainer.iterateDataset(dataset)) {
            try {
                try (NDList predictions = trainer.forward(batch.getData());
                     NDArray lossValue = loss.evaluate(batch.getLabels(), predictions)) {
                    totalLoss += lossValue.toFloatArray()[0];
                    batchCount++;
                }
            } finally {
                batch.close();
            }
        }
        return batchCount == 0 ? totalLoss : totalLoss / batchCount;
    }

    private ArrayDataset createDataset(NDManager manager, float[][] inputs,
                                       float[][] targets, int batchSize) {
        if (inputs == null || inputs.length == 0 || targets == null || targets.length == 0) {
            return null;
        }
        NDArray inputArray = manager.create(inputs);
        NDArray targetArray = manager.create(targets);
        return new ArrayDataset.Builder()
                .setData(inputArray)
                .optLabels(targetArray)
                .setSampling(Math.max(1, batchSize), true)
                .build();
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

    private Map<String, List<StockPrice>> loadPriceSeries(List<String> stockCodes, int days) {
        Map<String, List<StockPrice>> result = new LinkedHashMap<>();
        int queryLimit = Math.max(1, days);
        PageRequest pageRequest = PageRequest.of(0, queryLimit);
        for (String stockCode : stockCodes) {
            List<StockPrice> prices = new ArrayList<>(
                    priceRepository.findByCodeOrderByDateDesc(stockCode, pageRequest));
            if (prices.isEmpty()) {
                continue;
            }
            Collections.reverse(prices);
            result.put(stockCode, prices);
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

    private String saveModel(byte[] paramsBytes,
                             LstmDataPreprocessor.ProcessedData processedData,
                             int epoch,
                             double trainLoss,
                             double valLoss) throws IOException {
        String metadata = buildMetadata(processedData, epoch);
        return saveModelToMongo(paramsBytes, metadata, processedData, epoch, trainLoss, valLoss);
    }

    private String buildMetadata(LstmDataPreprocessor.ProcessedData processedData, int epoch) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("modelVersion", MODEL_VERSION);
        properties.setProperty("featureVersion", processedData.getFeatureVersion());
        properties.setProperty("labelVersion", "return-direction-downside-v3");
        properties.setProperty("sequenceLength", String.valueOf(processedData.getSequenceLength()));
        properties.setProperty("inputSize", String.valueOf(processedData.getFeatureCount()));
        properties.setProperty("outputSize", String.valueOf(StockLSTMModel.OUTPUT_SIZE));
        properties.setProperty("stockEmbeddingBuckets", String.valueOf(config.getStockEmbeddingBuckets()));
        properties.setProperty("stockEmbeddingSize", String.valueOf(config.getStockEmbeddingSize()));
        properties.setProperty("industryEmbeddingBuckets", String.valueOf(config.getIndustryEmbeddingBuckets()));
        properties.setProperty("industryEmbeddingSize", String.valueOf(config.getIndustryEmbeddingSize()));
        properties.setProperty("groupHeadCount", String.valueOf(config.getGroupHeadCount()));
        properties.setProperty("residualHeadEnabled", String.valueOf(config.isResidualHeadEnabled()));
        properties.setProperty("groupHeadScale", String.valueOf(config.getGroupHeadScale()));
        properties.setProperty("residualHeadScale", String.valueOf(config.getResidualHeadScale()));
        properties.setProperty("hashAlgorithm", "java-string-hashcode-floor-mod-v1");
        properties.setProperty("lossVersion", "huber-bce-huber-v1");
        properties.setProperty("hiddenSize", String.valueOf(config.getHiddenSize()));
        properties.setProperty("numLayers", String.valueOf(config.getNumLayers()));
        properties.setProperty("targetReturnScale", String.valueOf(processedData.getTargetReturnScale()));
        properties.setProperty("epoch", String.valueOf(epoch));
        properties.setProperty("createdAt", LocalDateTime.now().toString());
        properties.setProperty("modelName", modelName());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            properties.store(output, "stock-trading shared LSTM metadata");
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private String saveModelToMongo(byte[] paramsBytes, String metadata,
                                    LstmDataPreprocessor.ProcessedData processedData,
                                    int epoch, double trainLoss, double valLoss) throws IOException {
        try {
            ModelActivationDocument currentActivation = modelActivationRepository
                    .findByModelName(modelName()).orElse(null);
            LstmModelDocument document = new LstmModelDocument();
            document.setModelName(modelName());
            document.setModelVersion(MODEL_VERSION + "-" + System.currentTimeMillis());
            document.setParentModelVersionId(currentActivation == null
                    ? null : currentActivation.getActiveModelVersionId());
            document.setEpoch(epoch);
            document.setCreatedAt(LocalDateTime.now());
            document.setParams(paramsBytes);
            document.setNormalizationParams(metadata);
            document.setTrainingConfigJson(buildTrainingConfigJson());
            document.setInputContractJson(objectMapper.writeValueAsString(Map.of(
                    "sequenceLength", processedData.getSequenceLength(),
                    "inputSize", processedData.getFeatureCount(),
                    "outputSize", StockLSTMModel.OUTPUT_SIZE,
                    "featureVersion", processedData.getFeatureVersion(),
                    "labelVersion", "return-direction-downside-v3")));
            document.setMetricsJson(objectMapper.writeValueAsString(Map.of(
                    "trainLoss", trainLoss,
                    "valLoss", valLoss,
                    "bestEpoch", epoch,
                    "trainSamples", processedData.getTrainSamples().size(),
                    "valSamples", processedData.getValSamples().size())));
            document.setFeatureVersion(processedData.getFeatureVersion());
            document.setLabelVersion("return-direction-downside-v3");
            document.setEngineName("PyTorch");
            document.setEngineVersion(Engine.getInstance().getVersion());
            document.setDjlVersion(Model.class.getPackage().getImplementationVersion());
            document.setParameterSha256(sha256(paramsBytes));
            document.setParameterSize(paramsBytes.length);
            document.setStatus("READY");
            document.setTrainLoss(trainLoss);
            document.setValLoss(valLoss);
            LstmModelDocument saved = lstmModelRepository.save(document);

            ModelActivationDocument activation = currentActivation == null
                    ? new ModelActivationDocument() : currentActivation;
            activation.setModelName(modelName());
            activation.setPreviousModelVersionId(activation.getActiveModelVersionId());
            activation.setActiveModelVersionId(saved.getId());
            activation.setActivatedAt(LocalDateTime.now());
            modelActivationRepository.save(activation);

            log.info("共享 LSTM 模型保存并激活: id={}, version={}, previous={}",
                    saved.getId(), saved.getModelVersion(), activation.getPreviousModelVersionId());
            return "mongo:" + saved.getId();
        } catch (Exception exception) {
            throw new IOException("保存共享 LSTM 模型失败", exception);
        }
    }

    private ModelArtifact loadCurrentArtifact() {
        LstmModelDocument document = loadActiveDocument();
        if (document.getParams() == null || document.getParams().length == 0) {
            throw new IllegalStateException("共享 LSTM 模型参数为空");
        }
        String actualSha256 = sha256(document.getParams());
        if (!actualSha256.equalsIgnoreCase(document.getParameterSha256())) {
            throw new IllegalStateException("共享 LSTM 模型参数摘要不匹配");
        }
        return new ModelArtifact(document.getParams(), document.getNormalizationParams(), document.getId());
    }

    private LstmModelDocument loadActiveDocument() {
        ModelActivationDocument activation = modelActivationRepository.findByModelName(modelName())
                .orElseThrow(() -> new IllegalStateException("共享 LSTM 尚未激活模型版本"));
        return lstmModelRepository.findById(activation.getActiveModelVersionId())
                .orElseThrow(() -> new IllegalStateException("共享 LSTM 激活版本不存在"));
    }

    private String buildTrainingConfigJson() throws IOException {
        return objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("inputSize", config.getInputSize()),
                Map.entry("hiddenSize", config.getHiddenSize()),
                Map.entry("numLayers", config.getNumLayers()),
                Map.entry("dropout", config.getDropout()),
                Map.entry("sequenceLength", config.getSequenceLength()),
                Map.entry("stockEmbeddingBuckets", config.getStockEmbeddingBuckets()),
                Map.entry("stockEmbeddingSize", config.getStockEmbeddingSize()),
                Map.entry("industryEmbeddingBuckets", config.getIndustryEmbeddingBuckets()),
                Map.entry("industryEmbeddingSize", config.getIndustryEmbeddingSize()),
                Map.entry("groupHeadCount", config.getGroupHeadCount()),
                Map.entry("residualHeadEnabled", config.isResidualHeadEnabled()),
                Map.entry("groupHeadScale", config.getGroupHeadScale()),
                Map.entry("residualHeadScale", config.getResidualHeadScale()),
                Map.entry("learningRate", config.getLearningRate()),
                Map.entry("batchSize", config.getBatchSize()),
                Map.entry("epochs", config.getEpochs())));
    }

    private String modelName() {
        return config.getModelName() == null || config.getModelName().isBlank()
                ? DEFAULT_SHARED_MODEL_NAME : config.getModelName().trim();
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private void validateMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            throw new IllegalStateException("共享 LSTM 模型缺少元数据");
        }
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(metadata));
        } catch (IOException exception) {
            throw new IllegalStateException("共享 LSTM 元数据无法解析", exception);
        }
        requireMetadata(properties, "modelVersion", MODEL_VERSION);
        requireMetadata(properties, "featureVersion", LstmDataPreprocessor.FEATURE_VERSION);
        requireMetadata(properties, "sequenceLength", String.valueOf(config.getSequenceLength()));
        requireMetadata(properties, "inputSize", String.valueOf(config.getInputSize()));
        requireMetadata(properties, "outputSize", String.valueOf(StockLSTMModel.OUTPUT_SIZE));
        requireMetadata(properties, "stockEmbeddingBuckets", String.valueOf(config.getStockEmbeddingBuckets()));
        requireMetadata(properties, "stockEmbeddingSize", String.valueOf(config.getStockEmbeddingSize()));
        requireMetadata(properties, "industryEmbeddingBuckets", String.valueOf(config.getIndustryEmbeddingBuckets()));
        requireMetadata(properties, "industryEmbeddingSize", String.valueOf(config.getIndustryEmbeddingSize()));
        requireMetadata(properties, "groupHeadCount", String.valueOf(config.getGroupHeadCount()));
        requireMetadata(properties, "residualHeadEnabled", String.valueOf(config.isResidualHeadEnabled()));
        requireMetadata(properties, "groupHeadScale", String.valueOf(config.getGroupHeadScale()));
        requireMetadata(properties, "residualHeadScale", String.valueOf(config.getResidualHeadScale()));
        requireMetadata(properties, "hashAlgorithm", "java-string-hashcode-floor-mod-v1");
        requireMetadata(properties, "lossVersion", "huber-bce-huber-v1");
        requireMetadata(properties, "targetReturnScale", String.valueOf(config.getTargetReturnScale()));
    }

    private void requireMetadata(Properties properties, String key, String expected) {
        String actual = properties.getProperty(key);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("共享 LSTM 元数据不兼容: " + key
                    + ", expected=" + expected + ", actual=" + actual);
        }
    }

    private record ModelArtifact(byte[] params, String metadata, String modelId) {
    }

    /**
     * LSTM 离线训练结果。
     *
     * @author mwangli
     * @since 2026-10-08
     */
    @Data
    @Builder
    public static class TrainingResult {
        /** 是否训练成功。 */
        private boolean success;

        /** 训练结果说明。 */
        private String message;

        /** 训练任务标识。 */
        private String trainingId;

        /** 最佳训练轮次。 */
        private int epochs;

        /** 最佳训练损失。 */
        private double trainLoss;

        /** 最佳验证损失。 */
        private double valLoss;

        /** 模型保存路径或文档标识。 */
        private String modelPath;

        /** 训练样本数量。 */
        private int trainSamples;

        /** 验证样本数量。 */
        private int valSamples;

        /** 每轮训练摘要。 */
        private List<Map<String, Object>> details;
    }
}
// AI_GENERATE_END -----------
