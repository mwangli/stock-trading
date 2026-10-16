// AI_GENERATE_START -------------------
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
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.modelService.config.LstmDataQualityConfig;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.domain.entity.TrainingDatasetSnapshotDocument;
import com.stock.modelService.model.ModelBinaryCodec;
import com.stock.modelService.model.MultiTaskStockLoss;
import com.stock.modelService.model.StockLSTMModel;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final TrainingProgressListener NO_OP_PROGRESS_LISTENER = progress -> { };

    /** 当前共享模型固定名称。 */
    public static final String DEFAULT_SHARED_MODEL_NAME = LstmModelArtifactService.DEFAULT_SHARED_MODEL_NAME;

    /** 当前网络结构与标签口径版本。 */
    public static final String MODEL_VERSION = LstmModelArtifactService.MODEL_VERSION;

    private final LstmTrainingConfig config;
    private final LstmDataQualityConfig dataQualityConfig;
    private final LstmDataPreprocessor dataPreprocessor;
    private final LstmTrainingDataService trainingDataService;
    private final LstmModelArtifactService artifactService;
    private final TrainingDatasetSnapshotService snapshotService;
    private final ModelBinaryCodec modelBinaryCodec;
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
        return trainSharedModel(stockCodes, days, epochs, batchSize, learningRate,
                NO_OP_PROGRESS_LISTENER);
    }

    /**
     * 训练全市场共享基础模型，并持续上报数据准备和逐轮训练进度。
     *
     * @param stockCodes 参与训练的股票代码
     * @param days 每只股票读取的交易日数量
     * @param epochs 训练轮次，可为空
     * @param batchSize 批次大小，可为空
     * @param learningRate 学习率，可为空
     * @param progressListener 训练进度监听器
     * @return 训练结果
     */
    public TrainingResult trainSharedModel(List<String> stockCodes, int days, Integer epochs,
                                           Integer batchSize, Double learningRate,
                                           TrainingProgressListener progressListener) {
        return trainSharedModel(stockCodes, days, epochs, batchSize, learningRate,
                progressListener, null, null, 0, null);
    }

    /**
     * 训练全市场共享基础模型，并支持从已保存的参数检查点继续训练。
     *
     * @param stockCodes 参与训练的股票代码
     * @param days 每只股票读取的交易日数量
     * @param epochs 训练轮次，可为空
     * @param batchSize 批次大小，可为空
     * @param learningRate 学习率，可为空
     * @param progressListener 训练进度监听器
     * @param snapshotId 指定训练数据快照 ID，可为空
     * @param checkpointPath 检查点文件路径，可为空
     * @param resumeEpoch 已完成轮次
     * @param checkpointParams 检查点模型参数，可为空
     * @return 训练结果
     */
    public TrainingResult trainSharedModel(List<String> stockCodes, int days, Integer epochs,
                                           Integer batchSize, Double learningRate,
                                           TrainingProgressListener progressListener,
                                           String snapshotId,
                                           String checkpointPath, int resumeEpoch,
                                           byte[] checkpointParams) {
        String trainingId = "shared_training_" + System.currentTimeMillis();
        long trainingStartedNanos = System.nanoTime();
        TrainingProgressListener listener = progressListener == null
                ? NO_OP_PROGRESS_LISTENER : progressListener;
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
            validateTrainingConfiguration(trainEpochs, trainBatchSize, trainLearningRate);
            int fetchDays = Math.max(days, config.getSequenceLength() + config.getValidationGap() + 2);

            log.info("开始共享 LSTM 离线训练: stocks={}, days={}, epochs={}, batchSize={}, learningRate={}",
                    codes.size(), fetchDays, trainEpochs, trainBatchSize, trainLearningRate);
            reportProgress(listener, TrainingProgress.builder()
                    .stage("PREPARING_DATA")
                    .message("正在加载股票历史行情")
                    .progressPercent(5)
                    .currentEpoch(0)
                    .totalEpochs(trainEpochs)
                    .elapsedSeconds(elapsedSeconds(trainingStartedNanos))
                    .build());

            TrainingDatasetSnapshotDocument snapshot = snapshotId == null
                    ? snapshotService.findReusableSnapshot(artifactService.getModelName(), codes, fetchDays).orElse(null)
                    : snapshotService.findById(snapshotId)
                    .orElseThrow(() -> new IllegalStateException("指定训练数据快照不存在: " + snapshotId));
            LstmDataPreprocessor.ProcessedData processedData;
            if (snapshot != null) {
                reportProgress(listener, TrainingProgress.builder()
                        .stage("PREPARING_DATA")
                        .message("复用训练数据快照 " + snapshot.getId())
                        .progressPercent(35)
                        .currentEpoch(0)
                        .totalEpochs(trainEpochs)
                        .elapsedSeconds(elapsedSeconds(trainingStartedNanos))
                        .snapshotId(snapshot.getId())
                        .build());
                processedData = snapshotService.loadSnapshot(snapshot);
                log.info("共享 LSTM 复用训练数据快照: snapshotId={}, endDate={}, trainSamples={}, valSamples={}",
                        snapshot.getId(), snapshot.getDatasetEndDate(), snapshot.getTrainSamples(), snapshot.getValSamples());
            } else {
                log.info("共享 LSTM 数据准备开始: stocks={}, days={}", codes.size(), fetchDays);
                Map<String, List<StockPrice>> priceSeries = trainingDataService.loadPriceSeries(
                        codes, fetchDays, (completedStocks, totalStocks, loadedStocks, emptyStocks) -> {
                            int progressPercent = 5 + (int) Math.round(completedStocks * 15D / totalStocks);
                            long elapsedSeconds = elapsedSeconds(trainingStartedNanos);
                            log.info("共享 LSTM 数据准备进度: stocks={}/{}, loadedStocks={}, emptyStocks={}, progress={}%, elapsedSeconds={}",
                                    completedStocks, totalStocks, loadedStocks, emptyStocks,
                                    progressPercent, elapsedSeconds);
                            reportProgress(listener, TrainingProgress.builder()
                                    .stage("PREPARING_DATA")
                                    .message("正在加载历史行情 " + completedStocks + "/" + totalStocks)
                                    .progressPercent(progressPercent).currentEpoch(0).totalEpochs(trainEpochs)
                                    .elapsedSeconds(elapsedSeconds).build());
                        });
                reportProgress(listener, TrainingProgress.builder()
                        .stage("PREPARING_DATA")
                        .message("历史行情加载完成，正在加载股票上下文")
                        .progressPercent(20)
                        .currentEpoch(0)
                        .totalEpochs(trainEpochs)
                        .elapsedSeconds(elapsedSeconds(trainingStartedNanos))
                        .build());
                Map<String, LstmDataPreprocessor.StockContext> contexts = trainingDataService.loadStockContexts(codes);
                processedData = dataPreprocessor.processPanelData(priceSeries, contexts);
                if (processedData != null) {
                    snapshot = snapshotService.saveSnapshot(artifactService.getModelName(), codes, fetchDays, processedData);
                }
            }
            if (processedData == null || processedData.getTrainSamples().isEmpty()) {
                return TrainingResult.builder().success(false).message("共享训练数据不足").build();
            }

            int trainedStocks = processedData.getSampleCounts().size();
            int trainSize = processedData.getTrainSamples().size();
            int valSize = processedData.getValSamples().size();
            reportProgress(listener, TrainingProgress.builder()
                    .stage("PROCESSING_SAMPLES")
                    .message("训练样本构建完成")
                    .progressPercent(35)
                    .currentEpoch(0)
                    .totalEpochs(trainEpochs)
                    .elapsedSeconds(elapsedSeconds(trainingStartedNanos))
                    .trainedStocks(trainedStocks)
                    .trainSamples(trainSize)
                    .valSamples(valSize)
                    .snapshotId(snapshot == null ? null : snapshot.getId())
                    .build());
            String dataQualityError = validateTrainingData(trainedStocks, trainSize, valSize);
            if (dataQualityError != null) {
                return TrainingResult.builder()
                        .success(false)
                        .message(dataQualityError)
                        .trainingId(trainingId)
                        .trainedStocks(trainedStocks)
                        .trainSamples(trainSize)
                    .valSamples(valSize)
                        .build();
            }
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
                DirectionClassWeights directionClassWeights = calculateDirectionClassWeights(
                        processedData.getTrainTargets());
                log.info("共享 LSTM 方向任务启用自动类别加权: positiveWeight={}, negativeWeight={}",
                        directionClassWeights.positiveWeight(), directionClassWeights.negativeWeight());
                DefaultTrainingConfig trainingConfig = new DefaultTrainingConfig(new MultiTaskStockLoss(
                        (float) config.getReturnLossWeight(),
                        (float) config.getDirectionLossWeight(),
                        (float) config.getDownsideLossWeight(),
                        directionClassWeights.positiveWeight(), directionClassWeights.negativeWeight()))
                        .optOptimizer(optimizer)
                        .optDevices(new Device[]{device})
                        .addTrainingListeners(TrainingListener.Defaults.logging());

                try (Trainer trainer = model.newTrainer(trainingConfig)) {
                    int initBatchSize = Math.min(trainBatchSize, Math.max(1, trainSize));
                    trainer.initialize(new Shape(initBatchSize,
                            config.getSequenceLength() * config.getInputSize()));
                    if (checkpointParams != null && checkpointParams.length > 0) {
                        modelBinaryCodec.deserialize(checkpointParams, model);
                        log.info("共享 LSTM 从检查点恢复参数: epoch={}, path={}", resumeEpoch, checkpointPath);
                    }
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
                    long epochTrainingStartedNanos = System.nanoTime();
                    reportProgress(listener, TrainingProgress.builder()
                            .stage("TRAINING")
                            .message("模型训练已开始")
                            .progressPercent(40)
                            .currentEpoch(0)
                            .totalEpochs(trainEpochs)
                            .elapsedSeconds(elapsedSeconds(trainingStartedNanos))
                            .trainedStocks(trainedStocks)
                            .trainSamples(trainSize)
                            .valSamples(valSize)
                            .build());

                    for (int epoch = Math.max(0, resumeEpoch); epoch < trainEpochs; epoch++) {
                        float trainLoss = trainEpoch(trainer, trainDataset);
                        float valLoss = valDataset == null ? trainLoss : evaluateModel(trainer, valDataset);
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("epoch", epoch + 1);
                        entry.put("trainLoss", (double) trainLoss);
                        entry.put("valLoss", (double) valLoss);
                        trainingLog.add(entry);

                        boolean improved = false;
                        if (valLoss < bestValLoss - config.getMinDelta()) {
                            bestValLoss = valLoss;
                            bestTrainLoss = trainLoss;
                            bestEpoch = epoch + 1;
                            bestParams = modelBinaryCodec.serialize(model);
                            patienceCounter = 0;
                            improved = true;
                        } else if (config.isEarlyStopping()) {
                            patienceCounter++;
                        }
                        int completedEpochs = epoch + 1;
                        int progressPercent = 40 + (int) Math.round(
                                completedEpochs * 50D / trainEpochs);
                        long elapsedSeconds = elapsedSeconds(trainingStartedNanos);
                        long estimatedRemainingSeconds = estimateRemainingSeconds(
                                epochTrainingStartedNanos, completedEpochs,
                                trainEpochs - completedEpochs);
                        log.info("共享 LSTM 训练迭代 {}/{}: progress={}%, trainLoss={}, valLoss={}, "
                                        + "bestValLoss={}, improved={}, patience={}/{}, elapsedSeconds={}, "
                                        + "estimatedRemainingSeconds={}",
                                completedEpochs, trainEpochs, progressPercent, trainLoss, valLoss,
                                bestValLoss, improved, patienceCounter, config.getPatience(),
                                elapsedSeconds, estimatedRemainingSeconds);
                        saveCheckpoint(checkpointPath, model, completedEpochs);
                        reportProgress(listener, TrainingProgress.builder()
                                .stage("TRAINING")
                                .message(improved ? "验证损失改善" : "验证损失未改善")
                                .progressPercent(progressPercent)
                                .currentEpoch(completedEpochs)
                                .totalEpochs(trainEpochs)
                                .elapsedSeconds(elapsedSeconds)
                                .estimatedRemainingSeconds(estimatedRemainingSeconds)
                                .trainLoss((double) trainLoss)
                                .valLoss((double) valLoss)
                                .trainedStocks(trainedStocks)
                                .trainSamples(trainSize)
                                .valSamples(valSize)
                                .build());
                        if (config.isEarlyStopping() && patienceCounter >= config.getPatience()) {
                            log.info("共享 LSTM 早停: currentEpoch={}, bestEpoch={}, bestValLoss={}, patience={}",
                                    completedEpochs, bestEpoch, bestValLoss, patienceCounter);
                            break;
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

            reportProgress(listener, TrainingProgress.builder()
                    .stage("SAVING_MODEL")
                    .message("正在保存最佳模型参数")
                    .progressPercent(95)
                    .currentEpoch(trainingLog.size())
                    .totalEpochs(trainEpochs)
                    .elapsedSeconds(elapsedSeconds(trainingStartedNanos))
                    .estimatedRemainingSeconds(0L)
                    .trainLoss(bestTrainLoss)
                    .valLoss(bestValLoss)
                    .trainedStocks(trainedStocks)
                    .trainSamples(trainSize)
                    .valSamples(valSize)
                    .build());
            currentModelPath = artifactService.saveModel(bestParams, processedData, bestEpoch,
                    bestTrainLoss, bestValLoss);
            return TrainingResult.builder()
                    .success(true)
                    .message("共享基础模型训练完成")
                    .trainingId(trainingId)
                    .epochs(bestEpoch)
                    .trainLoss(bestTrainLoss)
                    .valLoss(bestValLoss)
                    .modelPath(currentModelPath)
                    .trainedStocks(trainedStocks)
                    .trainSamples(trainSize)
                    .valSamples(valSize)
                    .snapshotId(snapshot == null ? null : snapshot.getId())
                    .details(trainingLog)
                    .build();
        } catch (TrainingCancelledException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("共享 LSTM 训练失败", exception);
            return TrainingResult.builder()
                    .success(false)
                    .trainingId(trainingId)
                    .message("共享 LSTM 训练失败：" + exception.getMessage())
                    .build();
        }
    }

    private void reportProgress(TrainingProgressListener listener, TrainingProgress progress) {
        try {
            listener.onProgress(progress);
        } catch (TrainingCancelledException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("共享 LSTM 训练进度上报失败: stage={}, epoch={}, reason={}",
                    progress.getStage(), progress.getCurrentEpoch(), exception.getMessage());
        }
    }

    private void saveCheckpoint(String checkpointPath, Model model, int completedEpochs) {
        if (checkpointPath == null || checkpointPath.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(checkpointPath).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Files.write(path, modelBinaryCodec.serialize(model));
            log.info("共享 LSTM 检查点已保存: epoch={}, path={}", completedEpochs, path);
        } catch (IOException exception) {
            throw new IllegalStateException("保存 LSTM 训练检查点失败", exception);
        }
    }

    /**
     * 训练任务取消信号，允许停止请求穿透进度回调和训练服务异常兜底。
     */
    public static class TrainingCancelledException extends RuntimeException {

        public TrainingCancelledException(String message) {
            super(message);
        }
    }

    private long elapsedSeconds(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000_000L);
    }

    private long estimateRemainingSeconds(long startedNanos, int completedEpochs, int remainingEpochs) {
        if (completedEpochs <= 0 || remainingEpochs <= 0) {
            return 0L;
        }
        long elapsedNanos = Math.max(0L, System.nanoTime() - startedNanos);
        double remainingNanos = (double) elapsedNanos * remainingEpochs / completedEpochs;
        return Math.max(1L, (long) Math.ceil(remainingNanos / 1_000_000_000D));
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
        return predictNextBatch(stockCodes, null);
    }

    /**
     * 使用指定模型版本批量预测股票下一交易日结果。
     * 指定版本可保证长批次预测不受执行期间激活指针切换影响。
     *
     * @param stockCodes 股票代码集合
     * @param modelVersionId 固定使用的模型文档 ID；为空时使用当前激活版本
     * @return 股票代码到预测结果的映射
     */
    public Map<String, LstmPredictionResultDto> predictNextBatch(
            List<String> stockCodes, String modelVersionId) {
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        LstmModelArtifactService.ModelArtifact artifact = artifactService.loadArtifact(modelVersionId);
        artifactService.validateMetadata(artifact.metadata());

        int fetchDays = Math.max(config.getSequenceLength() + 21, config.getSequenceLength() * 2);
        Map<String, List<StockPrice>> priceSeries = trainingDataService.loadPriceSeries(codes, fetchDays);
        Map<String, LstmDataPreprocessor.StockContext> contexts = trainingDataService.loadStockContexts(codes);
        List<String> validCodes = new ArrayList<>();
        List<float[][]> inputs = new ArrayList<>();
        List<Double> lastClosePrices = new ArrayList<>();
        List<java.time.LocalDate> baseTradeDates = new ArrayList<>();
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
            baseTradeDates.add(input.getBaseTradeDate());
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
                            .baseTradeDate(baseTradeDates.get(index))
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
            LstmModelArtifactService.ModelArtifact artifact = artifactService.loadArtifact(null);
            artifactService.validateMetadata(artifact.metadata());
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
            LstmModelDocument document = artifactService.loadActiveDocument();
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

    /**
     * 根据训练集方向标签计算平衡权重，避免模型仅学习全市场多数方向先验。
     * 权重限制在 0.25 到 4.0 之间，防止极端样本比例导致训练梯度失控。
     *
     * @param targets 训练标签，第二列为方向标签
     * @return 上涨和下跌类别权重
     */
    private DirectionClassWeights calculateDirectionClassWeights(float[][] targets) {
        int positiveCount = 0;
        int negativeCount = 0;
        for (float[] target : targets) {
            if (target != null && target.length > 1 && target[1] >= 0.5F) {
                positiveCount++;
            } else {
                negativeCount++;
            }
        }
        int total = positiveCount + negativeCount;
        if (positiveCount == 0 || negativeCount == 0 || total == 0) {
            return new DirectionClassWeights(1F, 1F);
        }
        float positiveWeight = clampClassWeight(total / (2F * positiveCount));
        float negativeWeight = clampClassWeight(total / (2F * negativeCount));
        return new DirectionClassWeights(positiveWeight, negativeWeight);
    }

    private float clampClassWeight(float weight) {
        return Math.max(0.25F, Math.min(4F, weight));
    }

    private record DirectionClassWeights(float positiveWeight, float negativeWeight) {
    }

    private void validateTrainingConfiguration(int epochs, int batchSize, float learningRate) {
        double lossWeightTotal = config.getReturnLossWeight()
                + config.getDirectionLossWeight()
                + config.getDownsideLossWeight();
        if (epochs <= 0 || batchSize <= 0 || !Float.isFinite(learningRate) || learningRate <= 0F) {
            throw new IllegalArgumentException("LSTM 训练轮次、批次大小和学习率必须大于 0");
        }
        if (!Double.isFinite(lossWeightTotal) || lossWeightTotal <= 0D) {
            throw new IllegalArgumentException("LSTM 多任务损失权重总和必须大于 0");
        }
    }

    private String validateTrainingData(int trainedStocks, int trainSamples, int validationSamples) {
        if (trainedStocks < dataQualityConfig.getMinimumTrainingStockCount()) {
            return "有效训练股票数量不足，要求至少 "
                    + dataQualityConfig.getMinimumTrainingStockCount() + "，实际 " + trainedStocks;
        }
        if (trainSamples < dataQualityConfig.getMinimumTrainSamples()) {
            return "训练样本数量不足，要求至少 "
                    + dataQualityConfig.getMinimumTrainSamples() + "，实际 " + trainSamples;
        }
        if (validationSamples < dataQualityConfig.getMinimumValidationSamples()) {
            return "验证样本数量不足，要求至少 "
                    + dataQualityConfig.getMinimumValidationSamples() + "，实际 " + validationSamples;
        }
        return null;
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

    /**
     * 校验指定模型版本的参数完整性和当前代码兼容性。
     *
     * @param versionId 模型版本文档 ID
     * @throws IllegalStateException 当版本不存在、参数损坏或元数据不兼容时抛出
     */
    public void validateModelVersion(String versionId) {
        artifactService.validateModelVersion(versionId);
    }

    /**
     * 获取当前共享模型逻辑名称。
     *
     * @return 模型逻辑名称
     */
    public String getModelName() {
        return artifactService.getModelName();
    }

    /**
     * 训练进度监听器。
     *
     * @author mwangli
     * @since 2026-10-11
     */
    @FunctionalInterface
    public interface TrainingProgressListener {

        /**
         * 接收最新训练进度。
         *
         * @param progress 最新训练进度
         */
        void onProgress(TrainingProgress progress);
    }

    /**
     * LSTM 训练进度快照。
     *
     * @author mwangli
     * @since 2026-10-11
     */
    @Data
    @Builder
    public static class TrainingProgress {
        /** 当前训练阶段。 */
        private String stage;

        /** 当前阶段说明。 */
        private String message;

        /** 当前完成比例，范围 0-100。 */
        private int progressPercent;

        /** 当前已完成轮次。 */
        private int currentEpoch;

        /** 计划总轮次。 */
        private int totalEpochs;

        /** 已耗时，单位秒。 */
        private long elapsedSeconds;

        /** 预计剩余时间，单位秒。 */
        private Long estimatedRemainingSeconds;

        /** 当前训练损失。 */
        private Double trainLoss;

        /** 当前验证损失。 */
        private Double valLoss;

        /** 有效训练股票数量。 */
        private Integer trainedStocks;

        /** 训练样本数量。 */
        private Integer trainSamples;

        /** 验证样本数量。 */
        private Integer valSamples;

        /** 当前使用的训练数据快照 ID。 */
        private String snapshotId;
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

        /** 实际参与训练并产生有效样本的股票数量。 */
        private int trainedStocks;

        /** 训练样本数量。 */
        private int trainSamples;

        /** 验证样本数量。 */
        private int valSamples;

        /** 每轮训练摘要。 */
        private List<Map<String, Object>> details;

        /** 本次训练使用的数据快照 ID。 */
        private String snapshotId;
    }
}
// AI_GENERATE_END -------------------
