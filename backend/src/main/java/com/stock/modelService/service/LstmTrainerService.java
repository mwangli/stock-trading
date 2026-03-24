package com.stock.modelService.service;

import ai.djl.Model;
import ai.djl.Device;
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
import ai.djl.training.ParameterStore;
import ai.djl.nn.Block;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.model.ModelBinaryCodec;
import com.stock.modelService.model.StockLSTMModel;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.persistence.LstmModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LstmTrainerService {

    private final LstmTrainingConfig config;
    private final PriceRepository priceRepository;
    private final LstmDataPreprocessor dataPreprocessor;
    private final LstmModelRepository lstmModelRepository;
    private final ModelBinaryCodec modelBinaryCodec;
    private final com.stock.modelService.service.ModelTrainingRecordService modelTrainingRecordService;
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

            long trainingStartNs = System.nanoTime();

            // 标记对应股票进入“训练中”状态，便于前端实时展示
            try {
                modelTrainingRecordService.markTraining(stockCodes, true);
            } catch (Exception e) {
                log.warn("标记模型训练中状态失败，不影响训练主流程: {}", e.getMessage());
            }

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

            // 优先使用 GPU（需 pytorch-native-cu121 + NVIDIA 驱动 + CUDA 12.1）
            Device[] devices = Engine.getInstance().getDevices(1);
            Device device = devices.length > 0 ? devices[0] : Device.cpu();
            log.info("LSTM 训练设备: {} (deviceType={})", device, device.getDeviceType());

            DefaultTrainingConfig trainingConfig = new DefaultTrainingConfig(Loss.l2Loss())
                    .optOptimizer(optimizer)
                    .optDevices(devices)
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
                    long epochStartNs = System.nanoTime();
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
                            "Epoch {}/{} ({}) - TrainLoss: {}, ValLoss: {}, 耗时: {} 秒",
                            epoch + 1,
                            trainEpochs,
                            String.format("%.2f%%", (epoch + 1) * 100.0 / trainEpochs),
                            avgTrainLoss,
                            valLoss,
                            String.format("%.2f", (System.nanoTime() - epochStartNs) / 1_000_000_000.0)
                    );

                    if (config.isEarlyStopping()) {
                        if (valLoss < bestValLoss - config.getMinDelta()) {
                            bestValLoss = valLoss;
                            patienceCounter = 0;
                            bestModelPath = saveModel(model, processedData, epoch + 1, stockCodes, (double) avgTrainLoss, (double) valLoss);
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

            double finalTrainLoss = trainingLog.isEmpty() ? 0 : 
                    (double) trainingLog.get(trainingLog.size() - 1).get("trainLoss");
            double finalValLoss = trainingLog.isEmpty() ? 0 : 
                    (double) trainingLog.get(trainingLog.size() - 1).get("valLoss");

            if (bestModelPath == null) {
                bestModelPath = saveModel(model, processedData, trainEpochs, stockCodes, finalTrainLoss, finalValLoss);
            }
            currentModelPath = bestModelPath;

            status.setStatus("训练完成");
            status.setProgress(100);

            log.info("========== 训练完成 ==========");
            log.info("最终训练损失: {}, 验证损失: {}", finalTrainLoss, finalValLoss);
            log.info("模型保存结果: {}", currentModelPath);
            log.info("训练总耗时: {} 秒", String.format("%.2f", (System.nanoTime() - trainingStartNs) / 1_000_000_000.0));

            TrainingResult result = TrainingResult.builder()
                    .success(true).message("训练完成").epochs(trainEpochs)
                    .trainLoss(finalTrainLoss).valLoss(finalValLoss)
                    .modelPath(currentModelPath).trainSamples(trainSize)
                    .valSamples(valSize).details(trainingLog)
                    .trainingId(trainingId)
                    .build();

            // 更新 MySQL 中的模型训练记录（可能是一只或多只股票）
            try {
                long durationSeconds = (System.nanoTime() - trainingStartNs) / 1_000_000_000;
                // modelPath 形如 mongo:<id>，这里仅提取 MongoDB 文档 ID
                String mongoId = null;
                if (currentModelPath != null && currentModelPath.startsWith("mongo:")) {
                    mongoId = currentModelPath.substring("mongo:".length());
                }
                modelTrainingRecordService.updateAfterTraining(
                        stockCodes,
                        trainEpochs,
                        finalTrainLoss,
                        finalValLoss,
                        durationSeconds,
                        mongoId
                );
            } catch (Exception e) {
                log.warn("更新模型训练记录失败，不影响训练主流程: {}", e.getMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("训练失败", e);
            status.setStatus("训练失败：" + e.getMessage());
            status.setProgress(-1);
            // 训练失败时清除“训练中”标记
            try {
                modelTrainingRecordService.markTraining(stockCodes, false);
            } catch (Exception ex) {
                log.warn("清除模型训练中状态失败: {}", ex.getMessage());
            }
            return TrainingResult.builder().success(false).message("训练失败：" + e.getMessage()).build();
        }
    }

    /**
     * 使用最新的 LSTM 模型对单只股票进行下一交易日价格预测
     *
     * 该方法会：
     * 1. 根据股票代码从 MongoDB 中加载最近一次训练保存的模型参数；
     * 2. 从价格仓库中读取该股票的历史价格数据，并构建与训练阶段一致的特征序列；
     * 3. 使用 DJL 执行前向推理，得到归一化预测结果，并反归一化为实际价格；
     * 4. 计算相对涨跌幅并封装为 DTO 返回。
     *
     * @param stockCode 股票代码，如 "600519"
     * @return 预测结果 DTO，包含预测价格、最新收盘价及预测涨跌幅
     */
    public LstmPredictionResultDto predictNext(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("股票代码不能为空");
        }

        String trimmedCode = stockCode.trim();
        log.info("开始执行 LSTM 预测，股票代码={}", trimmedCode);

        // 1. 从价格仓库获取历史数据，并构建预测输入
        int fetchDays = Math.max(config.getSequenceLength() + 1, config.getSequenceLength() * 2);
        List<StockPrice> prices = getStockPrices(trimmedCode, fetchDays);
        if (prices.isEmpty()) {
            throw new IllegalStateException("未找到该股票的历史价格数据: " + trimmedCode);
        }

        LstmDataPreprocessor.PredictionInput predictionInput = dataPreprocessor.buildPredictionInput(prices);
        if (predictionInput == null) {
            throw new IllegalStateException("构建预测输入失败，历史数据不足: " + trimmedCode);
        }

        double maxPrice = predictionInput.getMaxPrice();
        double lastClosePrice = predictionInput.getLastClosePrice();

        // 2. 构造 DJL 模型并加载参数（根据配置从 MongoDB 或本地文件恢复）
        try (NDManager manager = NDManager.newBaseManager("PyTorch");
             Model model = Model.newInstance("lstm-stock-predict", "PyTorch")) {
            Block block = new StockLSTMModel(
                    config.getInputSize(),
                    config.getHiddenSize(),
                    config.getNumLayers(),
                    (float) config.getDropout(),
                    config.getSequenceLength()
            );
            model.setBlock(block);

            // 根据存储类型加载模型参数
            String storageType = config.getStorageType();
            if ("local".equalsIgnoreCase(storageType)) {
                loadModelFromLocalFiles(trimmedCode, manager, block);
            } else {
                // 默认使用 MongoDB
                LstmModelDocument modelDoc = lstmModelRepository.findTopByModelNameOrderByCreatedAtDesc(trimmedCode);
                if (modelDoc == null || modelDoc.getParams() == null) {
                    throw new IllegalStateException("未找到对应股票的 LSTM 模型，请先完成训练: " + trimmedCode);
                }
                try (ByteArrayInputStream bais = new ByteArrayInputStream(modelDoc.getParams());
                     DataInputStream dis = new DataInputStream(bais)) {
                    block.loadParameters(manager, dis);
                }
            }

            // 3. 构建输入张量并执行前向推理
            float[][][] batchInputs = new float[1][][];
            batchInputs[0] = predictionInput.getInput();
            float[][] flattened = flattenInputs(batchInputs);

            NDArray inputArray = manager.create(flattened);
            NDList inputs = new NDList(inputArray);
            ParameterStore parameterStore = new ParameterStore(manager, false);
            NDList output = model.getBlock().forward(parameterStore, inputs, false);
            NDArray prediction = output.singletonOrThrow();

            float[] values = prediction.toFloatArray();
            if (values.length == 0) {
                throw new IllegalStateException("LSTM 预测结果为空");
            }

            double normalizedPredicted = values[0];
            double predictedPrice = normalizedPredicted * maxPrice;
            Double safeLastClose = lastClosePrice > 0 ? lastClosePrice : null;
            Double changeRatio = null;
            if (safeLastClose != null && safeLastClose > 0) {
                changeRatio = (predictedPrice - safeLastClose) / safeLastClose;
            }

            log.info("LSTM 预测完成，stockCode={}, predictedPrice={}, lastClose={}, changeRatio={}",
                    trimmedCode, predictedPrice, lastClosePrice, changeRatio);

            LstmPredictionResultDto.LstmPredictionResultDtoBuilder builder = LstmPredictionResultDto.builder()
                    .stockCode(trimmedCode)
                    .predictedClosePrice(predictedPrice)
                    .lastClosePrice(safeLastClose)
                    .predictedChangeRatio(changeRatio);

            // 仅在使用 MongoDB 存储时填充 modelId
            if (!"local".equalsIgnoreCase(config.getStorageType())) {
                LstmModelDocument latestDoc = lstmModelRepository.findTopByModelNameOrderByCreatedAtDesc(trimmedCode);
                if (latestDoc != null) {
                    builder.modelId(latestDoc.getId());
                }
            }

            return builder.build();
        } catch (Exception e) {
            log.error("LSTM 预测失败，stockCode={}", trimmedCode, e);
            throw new IllegalStateException("LSTM 预测失败: " + e.getMessage(), e);
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

    private String saveModel(Model model, LstmDataPreprocessor.ProcessedData processedData, int epoch, String modelIdentifier, double trainLoss, double valLoss) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String modelName = "lstm-stock-" + timestamp + "-epoch" + epoch;

        // 1. Serialize model parameters to memory（不直接落盘，便于灵活选择存储介质）
        byte[] paramsBytes = modelBinaryCodec.serialize(model);

        // 2. 准备归一化参数的文本描述，便于后续调试和排查
        String params = String.format(
                "maxPrice=%.6f\nmaxVolume=%.6f\nsequenceLength=%d\ninputSize=%d\nhiddenSize=%d\nnumLayers=%d\ncreatedAt=%s\nmodelName=%s",
                processedData.getMaxPrice(), processedData.getMaxVolume(),
                processedData.getSequenceLength(), processedData.getFeatureCount(),
                config.getHiddenSize(), config.getNumLayers(),
                LocalDateTime.now().toString(), modelName
        );

        String storageType = config.getStorageType();
        if ("local".equalsIgnoreCase(storageType)) {
            return saveModelToLocal(paramsBytes, params, epoch, modelIdentifier, trainLoss, valLoss, timestamp);
        }
        // 默认使用 MongoDB
        return saveModelToMongo(paramsBytes, params, epoch, modelIdentifier, trainLoss, valLoss);
    }

    /**
     * 将 LSTM 模型保存到本地文件系统
     * 目录由配置项 models.lstm.local-base-path 控制，默认挂载到 /models/lstm。
     */
    private String saveModelToLocal(byte[] paramsBytes,
                                    String normalizationParams,
                                    int epoch,
                                    String modelIdentifier,
                                    double trainLoss,
                                    double valLoss,
                                    String timestamp) throws IOException {
        String safeIdentifier = modelIdentifier == null ? "unknown" : modelIdentifier.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path baseDir = Paths.get(config.getLocalBasePath());
        Files.createDirectories(baseDir);

        String filePrefix = safeIdentifier + "-" + timestamp + "-epoch" + epoch;
        Path paramsFile = baseDir.resolve(filePrefix + ".bin");
        Path metaFile = baseDir.resolve(filePrefix + ".meta");

        Files.write(paramsFile, paramsBytes);
        Files.writeString(metaFile, normalizationParams, StandardCharsets.UTF_8);

        log.info("LSTM 模型已保存到本地文件: {}, meta: {}, trainLoss={}, valLoss={}",
                paramsFile, metaFile, trainLoss, valLoss);

        String identifier = "local:" + paramsFile;
        log.info("Model saved successfully (local): {}", identifier);
        return identifier;
    }

    /**
     * 将 LSTM 模型以二进制形式保存到 MongoDB
     */
    private String saveModelToMongo(byte[] paramsBytes,
                                    String normalizationParams,
                                    int epoch,
                                    String modelIdentifier,
                                    double trainLoss,
                                    double valLoss) throws IOException {
        try {
            // 仅保留该标识下的最新模型，避免历史版本无限增长
            lstmModelRepository.deleteByModelName(modelIdentifier);
            log.info("Deleted old LSTM model: {}", modelIdentifier);

            LstmModelDocument doc = new LstmModelDocument();
            doc.setModelName(modelIdentifier);
            doc.setEpoch(epoch);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setParams(paramsBytes);
            doc.setNormalizationParams(normalizationParams);
            doc.setModelVersion("v1");
            doc.setTrainLoss(trainLoss);
            doc.setValLoss(valLoss);

            LstmModelDocument saved = lstmModelRepository.save(doc);
            log.info("New model saved to MongoDB, ID: {}, ModelName: {}", saved.getId(), saved.getModelName());

            String identifier = "mongo:" + saved.getId();
            log.info("Model saved successfully (mongo): {}", identifier);
            return identifier;

        } catch (Exception e) {
            log.error("Failed to save model to MongoDB", e);
            throw new IOException("Failed to save model to MongoDB", e);
        }
    }

    /**
     * 从本地文件系统加载最新的 LSTM 模型参数
     * 通过文件名前缀（股票代码）和修改时间选择最新的模型文件。
     */
    private void loadModelFromLocalFiles(String stockCode, NDManager manager, Block block) throws IOException, ai.djl.MalformedModelException {
        Path baseDir = Paths.get(config.getLocalBasePath());
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            throw new IllegalStateException("本地模型目录不存在或不是目录: " + baseDir);
        }

        String prefix = stockCode.replaceAll("[^a-zA-Z0-9_-]", "_") + "-";
        try (Stream<Path> stream = Files.list(baseDir)) {
            Path latestFile = stream
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.getFileName().toString().endsWith(".bin"))
                    .max(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .orElse(null);

            if (latestFile == null) {
                throw new IllegalStateException("未在本地目录中找到股票 " + stockCode + " 的 LSTM 模型文件");
            }

            log.info("从本地文件加载 LSTM 模型，stockCode={}, file={}", stockCode, latestFile);
            try (InputStream is = Files.newInputStream(latestFile);
                 DataInputStream dis = new DataInputStream(is)) {
                block.loadParameters(manager, dis);
            }
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
