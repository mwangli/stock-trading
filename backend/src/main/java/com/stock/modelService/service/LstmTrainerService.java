package com.stock.modelService.service;

import ai.djl.Model;
import ai.djl.engine.Engine;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.GradientCollector;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.dataset.Batch;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;
import ai.djl.ndarray.NDList;
import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.repository.PriceRepository;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.entity.LstmModelDocument;
import com.stock.modelService.model.StockLSTMModel;
import com.stock.modelService.repository.LstmModelRepository;
import com.stock.modelService.model.io.ModelBinaryCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LstmTrainerService {

    private final LstmTrainingConfig config;
    private final PriceRepository priceRepository;
    private final LstmDataPreprocessor dataPreprocessor;
    private final LstmModelRepository lstmModelRepository;
    private final ModelBinaryCodec modelBinaryCodec;
    private final Map<String, TrainingStatus> trainingStatusMap = new ConcurrentHashMap<>();
    private String currentModelPath;

    public TrainingResult trainModel(String stockCodes, int days, Integer epochs,
                                     Integer batchSize, Double learningRate) {
        String trainingId = "training_" + System.currentTimeMillis();
        TrainingStatus status = new TrainingStatus();
        trainingStatusMap.put(trainingId, status);

        try {
            int trainEpochs = epochs != null ? epochs : config.getEpochs();
            int trainBatchSize = batchSize != null ? batchSize : config.getBatchSize();
            float trainLearningRate = learningRate != null ? learningRate.floatValue() : (float) config.getLearningRate();

            log.info("========== 开始 LSTM 模型训练 ==========");
            log.info("股票: {}, 天数: {}, 轮次: {}, 批次: {}, 学习率: {}", 
                    stockCodes, days, trainEpochs, trainBatchSize, trainLearningRate);

            status.setStatus("准备数据");
            status.setProgress(5);

            // 预处理需要 sequenceLength + 1 条数据（包含目标值），如果用户传入 days==sequenceLength
            // 则额外获取一条历史记录以保证有目标值
            int fetchDays = days;
            if (days <= config.getSequenceLength()) {
                fetchDays = config.getSequenceLength() + 1;
            }
            List<StockPrice> prices = getStockPrices(stockCodes, fetchDays);
            if (prices.isEmpty()) {
                return TrainingResult.builder().success(false).message("没有找到股票数据").build();
            }
            log.info("获取到 {} 条价格数据", prices.size());

            status.setProgress(15);
            status.setStatus("数据预处理");

            LstmDataPreprocessor.ProcessedData processedData = dataPreprocessor.processData(prices);
            if (processedData == null || processedData.getTrainSamples().isEmpty()) {
                return TrainingResult.builder().success(false)
                        .message("数据预处理失败，需要至少 " + (config.getSequenceLength() + 1) + " 条数据").build();
            }

            int trainSize = processedData.getTrainSamples().size();
            int valSize = processedData.getValSamples().size();
            log.info("训练样本: {}, 验证样本: {}", trainSize, valSize);

            status.setStatus("初始化模型");
            status.setProgress(20);

            Model model = Model.newInstance("lstm-stock", "PyTorch");
            StockLSTMModel lstmModel = new StockLSTMModel(
                    config.getInputSize(),
                    config.getHiddenSize(),
                    config.getNumLayers(),
                    (float) config.getDropout(),
                    config.getSequenceLength()
            );
            model.setBlock(lstmModel);
            log.info("模型架构: {}", lstmModel.getModelInfo());

            status.setProgress(25);

            Optimizer optimizer = Optimizer.adam()
                    .optLearningRateTracker(Tracker.fixed(trainLearningRate))
                    .optWeightDecays(0.001f)
                    .build();

            DefaultTrainingConfig trainingConfig = new DefaultTrainingConfig(Loss.l2Loss())
                    .optOptimizer(optimizer)
                    .optDevices(Engine.getInstance().getDevices(1))
                    .addTrainingListeners(TrainingListener.Defaults.logging());

            status.setStatus("训练中");
            status.setProgress(30);

            List<Map<String, Object>> trainingLog = new ArrayList<>();
            double bestValLoss = Double.MAX_VALUE;
            int patienceCounter = 0;
            String bestModelPath = null;

            try (Trainer trainer = model.newTrainer(trainingConfig)) {
                int flattenedSize = config.getSequenceLength() * config.getInputSize();
                // 初始化时使用可用样本大小（避免 batchSize 大于样本数导致初始化不匹配）
                int initBatchSize = Math.min(trainBatchSize, Math.max(1, trainSize));
                Shape inputShape = new Shape(initBatchSize, flattenedSize);
                trainer.initialize(inputShape);

                log.info("训练器初始化完成，输入形状: {}", inputShape);

                float[][] trainInputs = flattenInputs(processedData.getTrainInputs());
                float[] trainTargets = processedData.getTrainTargets();
                float[][] valInputs = flattenInputs(processedData.getValInputs());
                float[] valTargets = processedData.getValTargets();

                // 使用 Trainer 的 NDManager 来创建 NDArrays，保证生命周期由 Trainer 管理
                ArrayDataset trainDataset = createDataset(trainer.getManager(), trainInputs, trainTargets, trainBatchSize);
                int valBatchSize = Math.min(trainBatchSize, Math.max(1, valSize));
                ArrayDataset valDataset = createDataset(trainer.getManager(), valInputs, valTargets, valBatchSize);

                for (int epoch = 0; epoch < trainEpochs; epoch++) {
                    status.setCurrentEpoch(epoch + 1);
                    status.setTotalEpochs(trainEpochs);

                    float epochLoss = 0;
                    int batchCount = 0;

                    for (Batch batch : trainer.iterateDataset(trainDataset)) {
                        try {
                            // 使用显式的 forward/loss/backward 计算训练损失。
                            // 说明：依赖 trainer.getTrainingResult().getTrainLoss() 在某些情况下可能为 null/0，
                            // 导致无法得到可用的 loss 数值，进而影响训练结果验证。
                            Loss loss = trainer.getLoss();
                            try (GradientCollector collector = trainer.newGradientCollector();
                                 NDList predictions = trainer.forward(batch.getData());
                                 NDArray lossValue = loss.evaluate(batch.getLabels(), predictions)) {
                                collector.backward(lossValue);
                                epochLoss += lossValue.toFloatArray()[0];
                            }
                            trainer.step();
                            batchCount++;
                        } finally {
                            batch.close();
                        }
                    }

                    float avgTrainLoss = batchCount > 0 ? epochLoss / batchCount : epochLoss;
                    float valLoss;
                    if (valDataset == null) {
                        // 没有验证集时，跳过评估，使用极大值占位（以避免误判为最佳模型）
                        valLoss = Float.MAX_VALUE;
                    } else {
                        valLoss = evaluateModel(trainer, valDataset);
                    }

                    Map<String, Object> logEntry = new HashMap<>();
                    logEntry.put("epoch", epoch + 1);
                    logEntry.put("trainLoss", (double) avgTrainLoss);
                    logEntry.put("valLoss", (double) valLoss);
                    trainingLog.add(logEntry);

                    double progress = 30 + (epoch + 1) * (55.0 / trainEpochs);
                    status.setProgress(progress);
                    status.setStatus(String.format("Epoch %d/%d - TrainLoss: %.6f, ValLoss: %.6f", 
                            epoch + 1, trainEpochs, avgTrainLoss, valLoss));

                    log.info(
                            "Epoch {}/{} - TrainLoss: {}, ValLoss: {}",
                            epoch + 1,
                            trainEpochs,
                            avgTrainLoss,
                            valLoss
                    );

                    if (config.isEarlyStopping()) {
                        if (valLoss < bestValLoss - config.getMinDelta()) {
                            bestValLoss = valLoss;
                            patienceCounter = 0;
                            bestModelPath = saveModel(model, processedData, epoch + 1, stockCodes);
                        } else {
                            patienceCounter++;
                            if (patienceCounter >= config.getPatience()) {
                                log.info("早停触发 - 连续 {} 轮验证损失未改善", patienceCounter);
                                break;
                            }
                        }
                    }
                }
            }

            status.setStatus("保存模型");
            status.setProgress(90);

            if (bestModelPath == null) {
                bestModelPath = saveModel(model, processedData, trainEpochs, stockCodes);
            }
            currentModelPath = bestModelPath;

            status.setStatus("训练完成");
            status.setProgress(100);

            double finalTrainLoss = trainingLog.isEmpty() ? 0 : 
                    (double) trainingLog.get(trainingLog.size() - 1).get("trainLoss");
            double finalValLoss = trainingLog.isEmpty() ? 0 : 
                    (double) trainingLog.get(trainingLog.size() - 1).get("valLoss");

            log.info("========== 训练完成 ==========");
            log.info("最终训练损失: {}, 验证损失: {}", finalTrainLoss, finalValLoss);
            log.info("模型保存路径: {}", currentModelPath);

            return TrainingResult.builder()
                    .success(true).message("训练完成").epochs(trainEpochs)
                    .trainLoss(finalTrainLoss).valLoss(finalValLoss)
                    .modelPath(currentModelPath).trainSamples(trainSize)
                    .valSamples(valSize).details(trainingLog)
                    .trainingId(trainingId)
                    .build();

        } catch (Exception e) {
            log.error("训练失败", e);
            status.setStatus("训练失败：" + e.getMessage());
            status.setProgress(-1);
            return TrainingResult.builder().success(false).message("训练失败：" + e.getMessage()).build();
        }
    }

    private float[][] flattenInputs(float[][][] inputs) {
        float[][] result = new float[inputs.length][];
        for (int i = 0; i < inputs.length; i++) {
            int seqLen = inputs[i].length;
            int features = inputs[i][0].length;
            result[i] = new float[seqLen * features];
            int idx = 0;
            for (int s = 0; s < seqLen; s++) {
                for (int f = 0; f < features; f++) {
                    result[i][idx++] = inputs[i][s][f];
                }
            }
        }
        return result;
    }

    private ArrayDataset createDataset(float[][] inputs, float[] targets, int batchSize) throws IOException {
        // 注意：不要在此方法中关闭 NDManager，因为 Trainer/ArrayDataset
        // 可能会在训练过程中管理 NDArray 的生命周期。
        NDManager manager = NDManager.newBaseManager("PyTorch");
        return createDataset(manager, inputs, targets, batchSize);
    }

    /**
     * 使用指定的 NDManager 创建 ArrayDataset，通常应传入 Trainer.getManager()
     * 以确保 NDArrays 的生命周期由 Trainer 管理。
     */
    private ArrayDataset createDataset(NDManager manager, float[][] inputs, float[] targets, int batchSize) {
        if (inputs == null || inputs.length == 0 || targets == null || targets.length == 0) {
            return null;
        }
        NDArray inputArray = manager.create(inputs);
        NDArray targetArray = manager.create(targets).reshape(targets.length, 1);
        return new ArrayDataset.Builder()
                .setData(inputArray).optLabels(targetArray)
                .setSampling(batchSize, true).build();
    }

    /**
     * 在验证集上评估模型：只做前向传播 + 计算损失，不更新梯度
     */
    private float evaluateModel(Trainer trainer, ArrayDataset valDataset) throws IOException, ai.djl.translate.TranslateException {
        if (valDataset == null) {
            return Float.MAX_VALUE;
        }
        float totalLoss = 0f;
        int batchCount = 0;
        Loss loss = trainer.getLoss();

        for (Batch batch : trainer.iterateDataset(valDataset)) {
            try {
                try (NDList predictions = trainer.forward(batch.getData());
                     NDArray batchLoss = loss.evaluate(batch.getLabels(), predictions)) {
                    totalLoss += batchLoss.toFloatArray()[0];
                    batchCount++;
                }
            } finally {
                batch.close();
            }
        }
        return batchCount > 0 ? totalLoss / batchCount : totalLoss;
    }

    private String saveModel(Model model, LstmDataPreprocessor.ProcessedData processedData, int epoch, String modelIdentifier) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String modelName = "lstm-stock-" + timestamp + "-epoch" + epoch;

        // 1. Serialize model parameters to memory (no file I/O)
        byte[] paramsBytes = modelBinaryCodec.serialize(model);

        // 2. Prepare normalization params string
        String params = String.format(
                "maxPrice=%.6f\nmaxVolume=%.6f\nsequenceLength=%d\ninputSize=%d\nhiddenSize=%d\nnumLayers=%d\ncreatedAt=%s\nmodelName=%s",
                processedData.getMaxPrice(), processedData.getMaxVolume(),
                processedData.getSequenceLength(), processedData.getFeatureCount(),
                config.getHiddenSize(), config.getNumLayers(),
                LocalDateTime.now().toString(), modelName
        );

        // 3. Save to MongoDB
        try {
            // Delete old model by identifier first to keep only one active model per identifier
            lstmModelRepository.deleteByModelName(modelIdentifier);
            log.info("Deleted old LSTM model: {}", modelIdentifier);

            // Save new model
            LstmModelDocument doc = new LstmModelDocument();
            doc.setModelName(modelIdentifier);
            doc.setEpoch(epoch);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setParams(paramsBytes);
            doc.setNormalizationParams(params);
            doc.setModelVersion("v1");

            LstmModelDocument saved = lstmModelRepository.save(doc);
            log.info("New model saved to MongoDB, ID: {}, ModelName: {}", saved.getId(), saved.getModelName());

            String identifier = "mongo:" + saved.getId();
            log.info("Model saved successfully: {}", identifier);
            return identifier;

        } catch (Exception e) {
            log.error("Failed to save model to MongoDB", e);
            throw new IOException("Failed to save model to MongoDB", e);
        }
    }

    private List<StockPrice> getStockPrices(String stockCodes, int days) {
        List<StockPrice> allPrices = new ArrayList<>();
        String[] codes = stockCodes.split(",");
        for (String code : codes) {
            code = code.trim();
            List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(code);
            if (!prices.isEmpty()) {
                int startIdx = Math.max(0, prices.size() - days);
                allPrices.addAll(prices.subList(startIdx, prices.size()));
            }
        }
        return allPrices.stream()
                .sorted(Comparator.comparing(StockPrice::getDate))
                .collect(Collectors.toList());
    }

    public TrainingStatus getTrainingStatus(String trainingId) {
        return trainingStatusMap.get(trainingId);
    }

    public String getCurrentModelPath() {
        return currentModelPath;
    }

    @lombok.Data
    public static class TrainingStatus {
        private String status = "等待中";
        private double progress = 0;
        private int currentEpoch = 0;
        private int totalEpochs = 0;
    }

    @lombok.Data
    @lombok.Builder
    public static class TrainingResult {
        private boolean success;
        private String message;
        private String trainingId;
        private int epochs;
        private double trainLoss;
        private double valLoss;
        private String modelPath;
        private int trainSamples;
        private int valSamples;
        private List<Map<String, Object>> details;
    }
}
