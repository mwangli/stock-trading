// AI_GENERATE_START--------------------------------------------------
package com.stock.model.service;

import ai.djl.Device;
import ai.djl.Model;
import ai.djl.engine.Engine;
import ai.djl.nn.Block;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
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
import com.stock.model.config.LstmDataQualityConfig;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.entity.TrainingDatasetSnapshotDocument;
import com.stock.model.algorithm.ModelBinaryCodec;
import com.stock.model.algorithm.LstmArchitectureType;
import com.stock.model.algorithm.MasterTransformerModel;
import com.stock.model.algorithm.RankingLSTMModel;
import com.stock.model.algorithm.RankingStockLoss;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
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
@Data
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
    private final LstmModelArtifactService artifactService;
    private final LstmModelRuntimeService modelRuntimeService;
    private final TrainingDatasetSnapshotService snapshotService;
    private final ModelBinaryCodec modelBinaryCodec;
    private final RankingValidationMetricsCalculator rankingMetricsCalculator;
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
                progressListener, null);
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
     * @return 训练结果
     */
    public TrainingResult trainSharedModel(List<String> stockCodes, int days, Integer epochs,
                                           Integer batchSize, Double learningRate,
                                           TrainingProgressListener progressListener,
                                           String snapshotId) {
        return trainSharedModel(stockCodes, days, epochs, batchSize, learningRate,
                progressListener, snapshotId, config.getArchitectureType());
    }

    /**
     * 训练指定架构的 `v5` 横截面模型。
     *
     * @param stockCodes 参与训练的股票代码
     * @param days 每只股票读取的交易日数量
     * @param epochs 训练轮次，可为空
     * @param batchSize 批次大小，可为空
     * @param learningRate 学习率，可为空
     * @param progressListener 训练进度监听器
     * @param snapshotId 指定训练数据快照 ID，可为空
     * @param architectureType 本次候选模型架构
     * @return 训练结果
     */
    public TrainingResult trainSharedModel(List<String> stockCodes, int days, Integer epochs,
                                           Integer batchSize, Double learningRate,
                                           TrainingProgressListener progressListener,
                                           String snapshotId,
                                           LstmArchitectureType architectureType) {
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
            validateArchitectureType(architectureType);
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
                log.info("共享 LSTM 数据快照构建开始: stocks={}, days={}", codes.size(), fetchDays);
                snapshot = snapshotService.buildSnapshot(artifactService.getModelName(), codes, fetchDays);
                reportProgress(listener, TrainingProgress.builder()
                        .stage("PREPARING_DATA")
                        .message("训练数据快照构建完成 " + snapshot.getId())
                        .progressPercent(35)
                        .currentEpoch(0)
                        .totalEpochs(trainEpochs)
                        .elapsedSeconds(elapsedSeconds(trainingStartedNanos))
                        .snapshotId(snapshot.getId())
                        .build());
                processedData = snapshotService.loadSnapshot(snapshot);
            }
            if (processedData == null || processedData.getTrainSamples().isEmpty()) {
                return TrainingResult.builder().success(false).message("共享训练数据不足").build();
            }

            int trainedStocks = processedData.getSampleCounts().size();
            int trainSize = processedData.getTrainSamples().size();
            int valSize = processedData.getValSamples().size();
            int testSize = processedData.getTestSamples() == null ? 0 : processedData.getTestSamples().size();
            DirectionLabelStats trainLabelStats = summarizeDirectionLabels(processedData.getTrainTargets());
            DirectionLabelStats validationLabelStats = summarizeDirectionLabels(processedData.getValTargets());
            DirectionClassWeights directionClassWeights = calculateDirectionClassWeights(trainLabelStats);
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
            int walkForwardFoldCount = processedData.getWalkForwardFolds() == null
                    ? 0 : processedData.getWalkForwardFolds().size();
            String dataQualityError = validateTrainingData(
                    codes.size(), trainedStocks, trainSize, valSize, testSize, walkForwardFoldCount);
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
            double bestSelectionScore = Double.NEGATIVE_INFINITY;
            RankingValidationMetricsCalculator.RankingValidationMetrics bestValidationMetrics = null;
            RankingValidationMetricsCalculator.RankingValidationMetrics bestTestMetrics = null;
            double probabilityTemperature = 1D;
            int bestEpoch = 0;
            int patienceCounter = 0;

            Engine.getInstance().setRandomSeed(config.getRandomSeed());
            try (Model model = Model.newInstance("lstm-global-shared", "PyTorch")) {
                Block block = createModelBlock(processedData, architectureType);
                model.setBlock(block);
                Optimizer optimizer = Optimizer.adam()
                        .optLearningRateTracker(Tracker.fixed(trainLearningRate))
                        .optWeightDecays(0.001f)
                        .build();
                Device[] availableDevices = Engine.getInstance().getDevices(1);
                Device device = availableDevices.length == 0 ? Device.cpu() : availableDevices[0];
                log.info("共享 LSTM 方向任务启用自动类别加权: positiveWeight={}, negativeWeight={}",
                        directionClassWeights.positiveWeight(), directionClassWeights.negativeWeight());
                DefaultTrainingConfig trainingConfig = new DefaultTrainingConfig(new RankingStockLoss(
                        (float) config.getRankingLossWeight(),
                        (float) config.getExcessReturnLossWeight(),
                        (float) config.getCostAwareDirectionLossWeight(),
                        (float) config.getDownsideQuantileLossWeight(),
                        (float) config.getConsistencyLossWeight(),
                        (float) (config.getCostThreshold() / config.getTargetReturnScale()),
                        (float) config.getNeutralDirectionWeight(),
                        directionClassWeights.positiveWeight(),
                        directionClassWeights.negativeWeight()))
                        .optOptimizer(optimizer)
                        .optDevices(new Device[]{device})
                        .addTrainingListeners(TrainingListener.Defaults.logging());

                try (Trainer trainer = model.newTrainer(trainingConfig)) {
                    int initBatchSize = Math.min(trainBatchSize, Math.max(1, trainSize));
                    trainer.initialize(new Shape(initBatchSize,
                            config.getSequenceLength() * config.getInputSize()));
                    List<ArrayDataset> trainDatasets = createDateGroupedDatasets(
                            trainer.getManager(), processedData.getTrainSamples(), trainBatchSize);
                    List<ArrayDataset> valDatasets = createDateGroupedDatasets(
                            trainer.getManager(), processedData.getValSamples(), trainBatchSize);
                    List<ArrayDataset> testDatasets = createDateGroupedDatasets(
                            trainer.getManager(), processedData.getTestSamples(), trainBatchSize);
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

                    for (int epoch = 0; epoch < trainEpochs; epoch++) {
                        float trainLoss = trainEpoch(trainer, trainDatasets);
                        RankingEvaluation validationEvaluation = evaluateRankingModel(trainer, valDatasets);
                        float valLoss = validationEvaluation.loss();
                        RankingValidationMetricsCalculator.RankingValidationMetrics validationMetrics =
                                rankingMetricsCalculator.calculate(
                                        validationEvaluation.rankScores(), validationEvaluation.actualReturns(),
                                        validationEvaluation.upProbabilities(), validationEvaluation.directionLabels(),
                                        validationEvaluation.groupSizes(), valLoss,
                                        config.getCostThreshold(), 1D);
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("epoch", epoch + 1);
                        entry.put("trainLoss", (double) trainLoss);
                        entry.put("valLoss", (double) valLoss);
                        entry.put("rankIc", validationMetrics.rankIc());
                        entry.put("topDecileNetReturn", validationMetrics.topDecileNetReturn());
                        entry.put("selectionScore", validationMetrics.selectionScore());
                        entry.put("collapsed", validationMetrics.collapsed());
                        trainingLog.add(entry);

                        boolean improved = false;
                        if (!validationMetrics.collapsed()
                                && validationMetrics.selectionScore() > bestSelectionScore + config.getMinDelta()) {
                            bestValLoss = valLoss;
                            bestTrainLoss = trainLoss;
                            bestSelectionScore = validationMetrics.selectionScore();
                            bestValidationMetrics = validationMetrics;
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
                        reportProgress(listener, TrainingProgress.builder()
                                .stage("TRAINING")
                                .message(improved ? "横截面验证组合分数改善" : "横截面验证组合分数未改善")
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

                    if (bestParams == null || bestValidationMetrics == null) {
                        throw new IllegalStateException("所有训练轮次均发生输出塌缩或未形成有效横截面排序");
                    }
                    modelBinaryCodec.deserialize(bestParams, model);
                    RankingEvaluation calibratedValidationEvaluation = evaluateRankingModel(trainer, valDatasets);
                    probabilityTemperature = rankingMetricsCalculator.fitProbabilityTemperature(
                            calibratedValidationEvaluation.upProbabilities(),
                            calibratedValidationEvaluation.directionLabels());
                    bestValidationMetrics = rankingMetricsCalculator.calculate(
                            calibratedValidationEvaluation.rankScores(),
                            calibratedValidationEvaluation.actualReturns(),
                            calibratedValidationEvaluation.upProbabilities(),
                            calibratedValidationEvaluation.directionLabels(),
                            calibratedValidationEvaluation.groupSizes(),
                            calibratedValidationEvaluation.loss(), config.getCostThreshold(),
                            probabilityTemperature);
                    RankingEvaluation testEvaluation = evaluateRankingModel(trainer, testDatasets);
                    RankingValidationMetricsCalculator.RankingValidationMetrics testMetrics =
                            rankingMetricsCalculator.calculate(
                                    testEvaluation.rankScores(), testEvaluation.actualReturns(),
                                    testEvaluation.upProbabilities(), testEvaluation.directionLabels(),
                                    testEvaluation.groupSizes(), testEvaluation.loss(),
                                    config.getCostThreshold(), probabilityTemperature);
                    if (testMetrics.collapsed()) {
                        throw new IllegalStateException("最佳候选模型在独立测试集发生输出塌缩");
                    }
                    trainingLog.add(Map.of(
                            "stage", "INDEPENDENT_TEST",
                            "rankIc", testMetrics.rankIc(),
                            "topDecileNetReturn", testMetrics.topDecileNetReturn(),
                            "selectionScore", testMetrics.selectionScore(),
                            "loss", (double) testEvaluation.loss()));
                    bestTestMetrics = testMetrics;
                }
            }

            RankingValidationMetricsCalculator.WalkForwardMetrics walkForwardMetrics =
                    evaluateWalkForwardFolds(processedData, processedData.getWalkForwardFolds(), architectureType,
                            bestEpoch, trainBatchSize, trainLearningRate, bestTestMetrics, trainingLog);
            RankingValidationMetricsCalculator.WalkForwardMetrics strictIsolationMetrics =
                    evaluateWalkForwardFolds(processedData, processedData.getStrictIsolationFolds(), architectureType,
                            bestEpoch, trainBatchSize, trainLearningRate, null, trainingLog);

            reportProgress(listener, TrainingProgress.builder()
                    .stage("SAVING_MODEL")
                    .message("正在保存最佳模型参数")
                    .progressPercent(95)
                    .currentEpoch(bestEpoch)
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
                    bestTrainLoss, bestValLoss, trainLabelStats, validationLabelStats,
                    directionClassWeights, bestValidationMetrics, bestTestMetrics,
                    walkForwardMetrics, strictIsolationMetrics, probabilityTemperature,
                    architectureType, trainBatchSize);
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
                    .trainUpSamples(trainLabelStats.positiveCount())
                    .trainDownSamples(trainLabelStats.negativeCount())
                    .valUpSamples(validationLabelStats.positiveCount())
                    .valDownSamples(validationLabelStats.negativeCount())
                    .directionPositiveWeight(directionClassWeights.positiveWeight())
                    .directionNegativeWeight(directionClassWeights.negativeWeight())
                    .validationRankIc(bestValidationMetrics.rankIc())
                    .validationTopDecileNetReturn(bestValidationMetrics.topDecileNetReturn())
                    .validationSelectionScore(bestValidationMetrics.selectionScore())
                    .testRankIc(bestTestMetrics.rankIc())
                    .testTopDecileNetReturn(bestTestMetrics.topDecileNetReturn())
                    .testSelectionScore(bestTestMetrics.selectionScore())
                    .walkForwardFoldCount(walkForwardMetrics.foldCount())
                    .walkForwardMeanRankIc(walkForwardMetrics.meanRankIc())
                    .walkForwardRankIcIr(walkForwardMetrics.rankIcIr())
                    .positiveRankIcWindowRatio(walkForwardMetrics.positiveRankIcRatio())
                    .walkForwardTopDecileNetReturn(walkForwardMetrics.averageTopDecileNetReturn())
                    .brierScore(walkForwardMetrics.brierScore())
                    .brierBaseline(walkForwardMetrics.brierBaseline())
                    .probabilityTemperature(probabilityTemperature)
                    .maximumDrawdown(walkForwardMetrics.maximumDrawdown())
                    .strictIsolationMeanRankIc(strictIsolationMetrics.meanRankIc())
                    .outputCollapsed(walkForwardMetrics.collapsed())
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

    /**
     * 训练任务取消信号，允许停止请求穿透进度回调和训练服务异常兜底。
     */
    public static class TrainingCancelledException extends RuntimeException {

        /**
         * 创建训练取消异常。
         *
         * @param message 训练取消原因
         */
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

    private Block createModelBlock(
            LstmDataPreprocessor.ProcessedData data, LstmArchitectureType architectureType) {
        if (architectureType == LstmArchitectureType.MASTER_TRANSFORMER) {
            return new MasterTransformerModel(
                    config.getInputSize(), config.getHiddenSize(), config.getSequenceLength());
        }
        int stockDictionarySize = Math.max(2, data.getStockDictionary().size());
        int industryDictionarySize = Math.max(2, data.getIndustryDictionary().size());
        int marketDictionarySize = Math.max(2, data.getMarketDictionary().size());
        return new RankingLSTMModel(
                config.getInputSize(),
                config.getHiddenSize(),
                config.getNumLayers(),
                (float) config.getDropout(),
                config.getSequenceLength(),
                stockDictionarySize,
                config.getStockEmbeddingSize(),
                industryDictionarySize,
                config.getIndustryEmbeddingSize(),
                marketDictionarySize,
                config.getMarketEmbeddingSize());
    }

    private void validateArchitectureType(LstmArchitectureType architectureType) {
        if (architectureType != LstmArchitectureType.RANKING_LSTM
                && architectureType != LstmArchitectureType.MASTER_TRANSFORMER) {
            throw new IllegalArgumentException(
                    "训练仅支持 RANKING_LSTM 或 MASTER_TRANSFORMER");
        }
    }

    /**
     * 统计训练或验证数据中的方向标签分布。
     *
     * @param targets 训练或验证标签，第三列为方向标签
     * @return 上涨和下跌样本数量
     */
    private DirectionLabelStats summarizeDirectionLabels(float[][] targets) {
        int positiveCount = 0;
        int negativeCount = 0;
        if (targets != null) {
            for (float[] target : targets) {
                if (target != null && target.length > 2 && target[2] > 0.5F) {
                    positiveCount++;
                } else if (target != null && target.length > 2 && target[2] < 0.5F) {
                    negativeCount++;
                }
            }
        }
        return new DirectionLabelStats(positiveCount, negativeCount);
    }

    private DirectionClassWeights calculateDirectionClassWeights(DirectionLabelStats labelStats) {
        int positiveCount = labelStats.positiveCount();
        int negativeCount = labelStats.negativeCount();
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

    static record DirectionClassWeights(float positiveWeight, float negativeWeight) {
    }

    static record DirectionLabelStats(int positiveCount, int negativeCount) {
    }

    private void validateTrainingConfiguration(int epochs, int batchSize, float learningRate) {
        double lossWeightTotal = config.getRankingLossWeight()
                + config.getExcessReturnLossWeight()
                + config.getCostAwareDirectionLossWeight()
                + config.getDownsideQuantileLossWeight()
                + config.getConsistencyLossWeight();
        if (epochs <= 0 || batchSize <= 0 || !Float.isFinite(learningRate) || learningRate <= 0F) {
            throw new IllegalArgumentException("LSTM 训练轮次、批次大小和学习率必须大于 0");
        }
        if (!Double.isFinite(lossWeightTotal) || lossWeightTotal <= 0D) {
            throw new IllegalArgumentException("LSTM 多任务损失权重总和必须大于 0");
        }
    }

    private String validateTrainingData(
            int requestedStocks,
            int trainedStocks,
            int trainSamples,
            int validationSamples,
            int testSamples,
            int walkForwardFoldCount) {
        if (trainedStocks < dataQualityConfig.getMinimumTrainingStockCount()) {
            return "有效训练股票数量不足，要求至少 "
                    + dataQualityConfig.getMinimumTrainingStockCount() + "，实际 " + trainedStocks;
        }
        double minimumCoverageRate = dataQualityConfig.getMinimumSourceCoverageRate();
        if (!Double.isFinite(minimumCoverageRate)
                || minimumCoverageRate <= 0D || minimumCoverageRate > 1D) {
            return "关键数据源最低覆盖率配置必须在 0 到 1 之间";
        }
        double processedCoverageRate = requestedStocks <= 0
                ? 0D : trainedStocks / (double) requestedStocks;
        if (processedCoverageRate < minimumCoverageRate) {
            return "预处理后有效股票覆盖率不足，要求至少 " + minimumCoverageRate
                    + "，实际 " + processedCoverageRate + "，请求 " + requestedStocks
                    + "，有效 " + trainedStocks;
        }
        if (trainSamples < dataQualityConfig.getMinimumTrainSamples()) {
            return "训练样本数量不足，要求至少 "
                    + dataQualityConfig.getMinimumTrainSamples() + "，实际 " + trainSamples;
        }
        if (validationSamples < dataQualityConfig.getMinimumValidationSamples()) {
            return "验证样本数量不足，要求至少 "
                    + dataQualityConfig.getMinimumValidationSamples() + "，实际 " + validationSamples;
        }
        if (testSamples < dataQualityConfig.getMinimumTestSamples()) {
            return "独立测试样本数量不足，要求至少 "
                    + dataQualityConfig.getMinimumTestSamples() + "，实际 " + testSamples;
        }
        if (walkForwardFoldCount < dataQualityConfig.getMinimumWalkForwardFoldCount()) {
            return "Walk-Forward 测试窗口数量不足，要求至少 "
                    + dataQualityConfig.getMinimumWalkForwardFoldCount() + "，实际 " + walkForwardFoldCount;
        }
        return null;
    }

    private float trainEpoch(Trainer trainer, List<ArrayDataset> trainDatasets)
            throws IOException, ai.djl.translate.TranslateException {
        float totalLoss = 0F;
        int batchCount = 0;
        Loss loss = trainer.getLoss();
        for (ArrayDataset trainDataset : trainDatasets) {
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
        }
        return batchCount == 0 ? totalLoss : totalLoss / batchCount;
    }

    private RankingEvaluation evaluateRankingModel(Trainer trainer, List<ArrayDataset> datasets)
            throws IOException, ai.djl.translate.TranslateException {
        float totalLoss = 0F;
        int batchCount = 0;
        Loss loss = trainer.getLoss();
        List<Double> rankScores = new ArrayList<>();
        List<Double> actualReturns = new ArrayList<>();
        List<Double> upProbabilities = new ArrayList<>();
        List<Double> directionLabels = new ArrayList<>();
        List<Integer> groupSizes = new ArrayList<>();
        for (ArrayDataset dataset : datasets) {
            for (Batch batch : trainer.iterateDataset(dataset)) {
                try {
                    try (NDList predictions = trainer.forward(batch.getData());
                         NDArray lossValue = loss.evaluate(batch.getLabels(), predictions)) {
                        totalLoss += lossValue.toFloatArray()[0];
                        float[] predictionValues = predictions.singletonOrThrow().toFloatArray();
                        float[] labelValues = batch.getLabels().singletonOrThrow().toFloatArray();
                        int rows = (int) predictions.singletonOrThrow().getShape().get(0);
                        groupSizes.add(rows);
                        for (int row = 0; row < rows; row++) {
                            rankScores.add((double) predictionValues[row * RankingLSTMModel.OUTPUT_SIZE]);
                            actualReturns.add(labelValues[row * RankingLSTMModel.OUTPUT_SIZE]
                                    * config.getTargetReturnScale());
                            upProbabilities.add((double) predictionValues[
                                    row * RankingLSTMModel.OUTPUT_SIZE + 2]);
                            directionLabels.add((double) labelValues[
                                    row * RankingLSTMModel.OUTPUT_SIZE + 2]);
                        }
                        batchCount++;
                    }
                } finally {
                    batch.close();
                }
            }
        }
        return new RankingEvaluation(
                batchCount == 0 ? totalLoss : totalLoss / batchCount,
                rankScores.stream().mapToDouble(Double::doubleValue).toArray(),
                actualReturns.stream().mapToDouble(Double::doubleValue).toArray(),
                upProbabilities.stream().mapToDouble(Double::doubleValue).toArray(),
                directionLabels.stream().mapToDouble(Double::doubleValue).toArray(),
                groupSizes.stream().mapToInt(Integer::intValue).toArray());
    }

    private List<ArrayDataset> createDateGroupedDatasets(
            NDManager manager, List<LstmDataPreprocessor.TrainingSample> samples, int maximumBatchSize) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        Map<java.time.LocalDate, List<LstmDataPreprocessor.TrainingSample>> byDate = samples.stream()
                .sorted(Comparator.comparing(LstmDataPreprocessor.TrainingSample::getTargetDate)
                        .thenComparing(LstmDataPreprocessor.TrainingSample::getStockCode))
                .collect(Collectors.groupingBy(LstmDataPreprocessor.TrainingSample::getTargetDate,
                        LinkedHashMap::new, Collectors.toList()));
        List<ArrayDataset> datasets = new ArrayList<>();
        for (List<LstmDataPreprocessor.TrainingSample> dateSamples : byDate.values()) {
            for (int start = 0; start < dateSamples.size(); start += maximumBatchSize) {
                int end = Math.min(dateSamples.size(), start + maximumBatchSize);
                List<LstmDataPreprocessor.TrainingSample> chunk = dateSamples.subList(start, end);
                float[][][] inputs = chunk.stream().map(LstmDataPreprocessor.TrainingSample::getInput)
                        .toArray(float[][][]::new);
                float[][] targets = chunk.stream().map(sample -> new float[]{
                        sample.getRankingTarget(), sample.getReturnTarget(),
                        sample.getDirectionTarget(), sample.getDownsideTarget()}).toArray(float[][]::new);
                datasets.add(createDataset(manager, flattenInputs(inputs), targets));
            }
        }
        return datasets;
    }

    private ArrayDataset createDataset(NDManager manager, float[][] inputs, float[][] targets) {
        if (inputs == null || inputs.length == 0 || targets == null || targets.length == 0) {
            throw new IllegalArgumentException("同日横截面训练批次不能为空");
        }
        NDArray inputArray = manager.create(inputs);
        NDArray targetArray = manager.create(targets);
        return new ArrayDataset.Builder()
                .setData(inputArray)
                .optLabels(targetArray)
                .setSampling(inputs.length, false)
                .build();
    }

    private RankingValidationMetricsCalculator.WalkForwardMetrics evaluateWalkForwardFolds(
            LstmDataPreprocessor.ProcessedData processedData,
            List<LstmDataPreprocessor.WalkForwardFold> folds,
            LstmArchitectureType architectureType,
            int epochs,
            int batchSize,
            float learningRate,
            RankingValidationMetricsCalculator.RankingValidationMetrics latestFoldMetrics,
            List<Map<String, Object>> trainingLog) throws IOException, ai.djl.translate.TranslateException {
        if (folds == null || folds.isEmpty()) {
            return rankingMetricsCalculator.aggregate(List.of());
        }
        List<RankingValidationMetricsCalculator.RankingValidationMetrics> metrics = new ArrayList<>();
        int lastFoldNumber = folds.get(folds.size() - 1).getFoldNumber();
        for (LstmDataPreprocessor.WalkForwardFold fold : folds) {
            RankingValidationMetricsCalculator.RankingValidationMetrics foldMetrics;
            if (latestFoldMetrics != null && fold.getFoldNumber() == lastFoldNumber) {
                foldMetrics = latestFoldMetrics;
            } else {
                foldMetrics = trainAndEvaluateFold(
                        processedData, fold, architectureType, epochs, batchSize, learningRate);
            }
            metrics.add(foldMetrics);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("stage", fold.isStrictIsolation() ? "STRICT_ISOLATION_FOLD" : "WALK_FORWARD_FOLD");
            entry.put("fold", fold.getFoldNumber());
            entry.put("testStartDate", fold.getTestStartDate());
            entry.put("testEndDate", fold.getTestEndDate());
            entry.put("rankIc", foldMetrics.rankIc());
            entry.put("topDecileNetReturn", foldMetrics.topDecileNetReturn());
            entry.put("brierScore", foldMetrics.brierScore());
            entry.put("brierBaseline", foldMetrics.brierBaseline());
            entry.put("collapsed", foldMetrics.collapsed());
            trainingLog.add(entry);
        }
        return rankingMetricsCalculator.aggregate(metrics);
    }

    private RankingValidationMetricsCalculator.RankingValidationMetrics trainAndEvaluateFold(
            LstmDataPreprocessor.ProcessedData processedData,
            LstmDataPreprocessor.WalkForwardFold fold,
            LstmArchitectureType architectureType,
            int epochs,
            int batchSize,
            float learningRate) throws IOException, ai.djl.translate.TranslateException {
        try (Model model = Model.newInstance("lstm-walk-forward-fold-" + fold.getFoldNumber(), "PyTorch")) {
            Engine.getInstance().setRandomSeed(config.getRandomSeed() + fold.getFoldNumber());
            Block block = createModelBlock(processedData, architectureType);
            model.setBlock(block);
            Optimizer optimizer = Optimizer.adam()
                    .optLearningRateTracker(Tracker.fixed(learningRate))
                    .optWeightDecays(0.001f)
                    .build();
            Device[] availableDevices = Engine.getInstance().getDevices(1);
            Device device = availableDevices.length == 0 ? Device.cpu() : availableDevices[0];
            DirectionClassWeights foldClassWeights = calculateDirectionClassWeights(
                    summarizeDirectionLabels(fold.getTrainSamples()));
            DefaultTrainingConfig trainingConfig = new DefaultTrainingConfig(
                    createRankingLoss(foldClassWeights))
                    .optOptimizer(optimizer)
                    .optDevices(new Device[]{device})
                    .addTrainingListeners(TrainingListener.Defaults.logging());
            try (Trainer trainer = model.newTrainer(trainingConfig)) {
                trainer.initialize(new Shape(Math.min(batchSize, Math.max(1, fold.getTrainSamples().size())),
                        config.getSequenceLength() * config.getInputSize()));
                List<ArrayDataset> trainDatasets = createDateGroupedDatasets(
                        trainer.getManager(), fold.getTrainSamples(), batchSize);
                List<ArrayDataset> validationDatasets = createDateGroupedDatasets(
                        trainer.getManager(), fold.getValSamples(), batchSize);
                List<ArrayDataset> testDatasets = createDateGroupedDatasets(
                        trainer.getManager(), fold.getTestSamples(), batchSize);
                byte[] bestParameters = null;
                double bestSelectionScore = Double.NEGATIVE_INFINITY;
                int patienceCounter = 0;
                for (int epoch = 0; epoch < Math.max(1, epochs); epoch++) {
                    trainEpoch(trainer, trainDatasets);
                    RankingEvaluation validation = evaluateRankingModel(trainer, validationDatasets);
                    RankingValidationMetricsCalculator.RankingValidationMetrics validationMetrics =
                            rankingMetricsCalculator.calculate(
                                    validation.rankScores(), validation.actualReturns(),
                                    validation.upProbabilities(), validation.directionLabels(),
                                    validation.groupSizes(), validation.loss(),
                                    config.getCostThreshold(), 1D);
                    if (!validationMetrics.collapsed()
                            && validationMetrics.selectionScore() > bestSelectionScore + config.getMinDelta()) {
                        bestSelectionScore = validationMetrics.selectionScore();
                        bestParameters = modelBinaryCodec.serialize(model);
                        patienceCounter = 0;
                    } else if (config.isEarlyStopping()) {
                        patienceCounter++;
                    }
                    if (config.isEarlyStopping() && patienceCounter >= config.getPatience()) {
                        break;
                    }
                }
                if (bestParameters == null) {
                    throw new IllegalStateException("Walk-Forward 折叠未形成有效候选: fold="
                            + fold.getFoldNumber());
                }
                modelBinaryCodec.deserialize(bestParameters, model);
                RankingEvaluation validation = evaluateRankingModel(trainer, validationDatasets);
                double temperature = rankingMetricsCalculator.fitProbabilityTemperature(
                        validation.upProbabilities(), validation.directionLabels());
                RankingEvaluation test = evaluateRankingModel(trainer, testDatasets);
                return rankingMetricsCalculator.calculate(
                        test.rankScores(), test.actualReturns(), test.upProbabilities(),
                        test.directionLabels(), test.groupSizes(), test.loss(),
                        config.getCostThreshold(), temperature);
            }
        }
    }

    private DirectionLabelStats summarizeDirectionLabels(
            List<LstmDataPreprocessor.TrainingSample> samples) {
        int positiveCount = 0;
        int negativeCount = 0;
        for (LstmDataPreprocessor.TrainingSample sample : samples) {
            if (sample.getDirectionTarget() > 0.5F) {
                positiveCount++;
            } else if (sample.getDirectionTarget() < 0.5F) {
                negativeCount++;
            }
        }
        return new DirectionLabelStats(positiveCount, negativeCount);
    }

    private RankingStockLoss createRankingLoss(DirectionClassWeights classWeights) {
        return new RankingStockLoss(
                (float) config.getRankingLossWeight(),
                (float) config.getExcessReturnLossWeight(),
                (float) config.getCostAwareDirectionLossWeight(),
                (float) config.getDownsideQuantileLossWeight(),
                (float) config.getConsistencyLossWeight(),
                (float) (config.getCostThreshold() / config.getTargetReturnScale()),
                (float) config.getNeutralDirectionWeight(),
                classWeights.positiveWeight(),
                classWeights.negativeWeight());
    }

    private record RankingEvaluation(
            float loss,
            double[] rankScores,
            double[] actualReturns,
            double[] upProbabilities,
            double[] directionLabels,
            int[] groupSizes) {
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
        modelRuntimeService.validateModelVersionRuntime(versionId);
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

        /** 训练集上涨方向样本数量。 */
        private int trainUpSamples;

        /** 训练集下跌方向样本数量。 */
        private int trainDownSamples;

        /** 验证集上涨方向样本数量。 */
        private int valUpSamples;

        /** 验证集下跌方向样本数量。 */
        private int valDownSamples;

        /** 方向任务上涨类别权重。 */
        private float directionPositiveWeight;

        /** 方向任务下跌类别权重。 */
        private float directionNegativeWeight;

        /** 每轮训练摘要。 */
        private List<Map<String, Object>> details;

        /** 本次训练使用的数据快照 ID。 */
        private String snapshotId;

        /** 验证集 Rank IC。 */
        private Double validationRankIc;

        /** 验证集 Top 10% 成本后收益。 */
        private Double validationTopDecileNetReturn;

        /** 验证集组合选模分数。 */
        private Double validationSelectionScore;

        /** 独立测试集 Rank IC。 */
        private Double testRankIc;

        /** 独立测试集 Top 10% 成本后收益。 */
        private Double testTopDecileNetReturn;

        /** 独立测试集组合分数。 */
        private Double testSelectionScore;

        /** 是否检测到输出塌缩。 */
        private boolean outputCollapsed;

        /** Walk-Forward 有效测试窗口数量。 */
        private int walkForwardFoldCount;

        /** Walk-Forward 平均 Rank IC。 */
        private Double walkForwardMeanRankIc;

        /** Walk-Forward Rank ICIR。 */
        private Double walkForwardRankIcIr;

        /** Rank IC 为正的测试窗口占比。 */
        private Double positiveRankIcWindowRatio;

        /** Walk-Forward 平均 Top 10% 成本后收益。 */
        private Double walkForwardTopDecileNetReturn;

        /** 温度校准后的 Walk-Forward Brier Score。 */
        private Double brierScore;

        /** 实际上涨比例常数基线 Brier Score。 */
        private Double brierBaseline;

        /** 概率温度校准参数。 */
        private Double probabilityTemperature;

        /** Top 10% 窗口收益序列最大回撤。 */
        private Double maximumDrawdown;

        /** 60 日严格隔离敏感性平均 Rank IC。 */
        private Double strictIsolationMeanRankIc;
    }
}
// AI_GENERATE_END--------------------------------------------------
