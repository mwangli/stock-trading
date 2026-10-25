// AI_GENERATE_START-----------------
package com.stock.model.service;

import com.stock.market.service.MarketDataQueryService;
import com.stock.market.service.TradingCalendarService;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.entity.TrainingDatasetSnapshotDocument;
import com.stock.model.repository.TrainingDatasetSnapshotRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * LSTM 训练数据快照服务。
 * 使用版本化模型数据完成横截面特征、Walk-Forward 切分和快照制品持久化。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingDatasetSnapshotService {

    private static final String READY = "READY";
    private final LstmTrainingConfig config;
    private final MarketDataQueryService marketDataQueryService;
    private final TradingCalendarService tradingCalendarService;
    private final TrainingDatasetSnapshotRepository snapshotRepository;
    private final TrainingSnapshotArtifactStore artifactStore;
    private final RankingLstmTrainingDataService rankingTrainingDataService;
    private final RankingLstmDataPreprocessor rankingDataPreprocessor;

    /**
     * 应用启动时清理上次进程中断留下的快照半成品和已完成快照的临时目录。
     */
    @PostConstruct
    public void cleanupInterruptedSnapshotArtifacts() {
        List<TrainingDatasetSnapshotDocument> interrupted = snapshotRepository.findByStatus("BUILDING");
        for (TrainingDatasetSnapshotDocument snapshot : interrupted) {
            artifactStore.deleteArtifacts(snapshot.getId());
            snapshot.setStatus("FAILED");
            snapshot.setFinishedAt(LocalDateTime.now());
            snapshot.setErrorMessage("应用重启，已清理未完成的训练快照工件");
            snapshotRepository.save(snapshot);
            log.warn("已清理应用重启前遗留的训练快照: snapshotId={}", snapshot.getId());
        }
        snapshotRepository.findByStatus(READY).stream()
                .filter(snapshot -> TrainingSnapshotArtifactStore.STORAGE_GRIDFS.equals(snapshot.getArtifactStorage()))
                .forEach(snapshot -> artifactStore.cleanupTemporaryDirectory(snapshot.getId()));
    }

    /**
     * 计算训练数据契约摘要。
     *
     * @param modelName 模型名称
     * @param stockCodes 股票代码
     * @param days 历史交易日数量
     * @return 契约摘要
     */
    public String contractHash(String modelName, List<String> stockCodes, int days) {
        List<String> sortedCodes = stockCodes == null ? List.of() : stockCodes.stream().sorted().toList();
        String contract = String.join("|", modelName, String.valueOf(days),
                RankingLstmDataPreprocessor.FEATURE_VERSION,
                RankingLstmDataPreprocessor.LABEL_VERSION,
                RankingLstmDataPreprocessor.SPLIT_POLICY_VERSION,
                RankingLstmDataPreprocessor.COST_MODEL_VERSION,
                config.getDataSource(), config.getDataSourceVersion(),
                config.getBenchmarkSeriesCode(), String.valueOf(config.getSequenceLength()),
                String.valueOf(config.getInputSize()), String.valueOf(config.getCostThreshold()),
                String.valueOf(config.getTrainingWindowYears()),
                String.valueOf(config.getValidationWindowMonths()),
                String.valueOf(config.getTestWindowMonths()),
                String.valueOf(config.getWalkForwardStepMonths()),
                String.valueOf(config.getPurgeTradingDays()),
                String.valueOf(config.getEmbargoTradingDays()),
                String.valueOf(config.getStrictIsolationTradingDays()),
                String.join(",", sortedCodes));
        return sha256(contract.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 查询可复用的最新快照。
     *
     * @param modelName 模型名称
     * @param stockCodes 股票代码
     * @param days 历史交易日数量
     * @return 最新可复用快照，不存在时返回空
     */
    private Optional<TrainingDatasetSnapshotDocument> findReusableSnapshot(
            String modelName, List<String> stockCodes, int days) {
        return snapshotRepository.findFirstByModelNameAndContractHashAndStatusOrderByCreatedAtDesc(
                modelName, contractHash(modelName, stockCodes, days), READY)
                .filter(artifactStore::isReadable)
                .filter(this::isFreshEnoughForNewTraining);
    }

    /**
     * 为训练准备可复用的数据快照，并在快照元数据创建后通知调用方持久化关联关系。
     *
     * @param modelName 模型名称
     * @param stockCodes 股票代码
     * @param days 历史交易日数量
     * @param snapshotObserver 快照状态观察器
     * @return 快照准备结果
     */
    public synchronized SnapshotPreparationResult prepareSnapshot(
            String modelName,
            List<String> stockCodes,
            int days,
            Consumer<TrainingDatasetSnapshotDocument> snapshotObserver) {
        Optional<TrainingDatasetSnapshotDocument> reusable = findReusableSnapshot(
                modelName, stockCodes, days);
        if (reusable.isPresent()) {
            TrainingDatasetSnapshotDocument snapshot = reusable.get();
            notifySnapshotObserver(snapshotObserver, snapshot);
            return new SnapshotPreparationResult(snapshot, true);
        }
        TrainingDatasetSnapshotDocument snapshot = buildSnapshot(
                modelName, stockCodes, days, snapshotObserver);
        return new SnapshotPreparationResult(snapshot, false);
    }

    /**
     * 判断快照是否仍可用于一次新的训练任务。
     *
     * 每日预测不调用此方法；只有训练任务准备数据时才会检查。快照最多允许落后一个已确认交易日，
     * 超过该范围则返回空值，由训练流程构建新快照，避免把旧快照误认为实时数据。
     *
     * @param snapshot 待复用快照
     * @return 是否允许复用
     */
    private boolean isFreshEnoughForNewTraining(TrainingDatasetSnapshotDocument snapshot) {
        LocalDate latestMarketDate = marketDataQueryService.findLatestModelStockBarDate(
                config.getDataSource(), config.getDataSourceVersion());
        LocalDate datasetEndDate = snapshot.getDatasetEndDate();
        if (latestMarketDate == null || datasetEndDate == null || !latestMarketDate.isAfter(datasetEndDate)) {
            return latestMarketDate != null && datasetEndDate != null;
        }
        LocalDate cursor = datasetEndDate;
        int tradingDayGap = 0;
        while (cursor.isBefore(latestMarketDate) && tradingDayGap <= 1) {
            cursor = tradingCalendarService.nextTradingDay(cursor);
            tradingDayGap++;
        }
        boolean reusable = !cursor.isBefore(latestMarketDate) && tradingDayGap <= 1;
        if (!reusable) {
            log.info("训练快照超过允许的新鲜度，仅在新训练任务中重建: snapshotId={}, datasetEndDate={}, latestMarketDate={}, tradingDayGap={}",
                    snapshot.getId(), datasetEndDate, latestMarketDate, tradingDayGap);
        }
        return reusable;
    }

    /**
     * 按 ID 查询并校验快照元数据。
     *
     * @param snapshotId 快照 ID
     * @return 快照元数据
     */
    public Optional<TrainingDatasetSnapshotDocument> findById(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        return snapshotRepository.findById(snapshotId);
    }

    private TrainingDatasetSnapshotDocument buildSnapshot(
            String modelName,
            List<String> stockCodes,
            int days,
            Consumer<TrainingDatasetSnapshotDocument> snapshotObserver) {
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream().distinct().toList();
        TrainingDatasetSnapshotDocument document = createBuildingDocument(modelName, codes, days);
        Path snapshotDirectory = null;
        long startedNanos = System.nanoTime();
        String stage = "LOAD_VERSIONED_MODEL_DATA";
        log.info("v5 LSTM 训练数据快照开始构建: snapshotId={}, modelName={}, stocks={}, days={}, "
                        + "architecture={}, source={}, sourceVersion={}",
                document.getId(), modelName, codes.size(), days, config.getArchitectureType(),
                config.getDataSource(), config.getDataSourceVersion());
        try {
            notifySnapshotObserver(snapshotObserver, document);
            snapshotDirectory = artifactStore.prepareSnapshotDirectory(document.getId());
            RankingLstmTrainingDataService.RankingTrainingDataBundle bundle =
                    rankingTrainingDataService.loadTrainingData(codes, days);
            stage = "BUILD_CROSS_SECTIONAL_SAMPLES";
            LstmDataPreprocessor.ProcessedData processedData = rankingDataPreprocessor.process(bundle);
            if (processedData.getTrainSamples() == null || processedData.getTrainSamples().isEmpty()) {
                throw new IllegalStateException("训练快照没有可用样本");
            }
            TrainingSnapshotArtifactStore.ChunkPayload chunk = TrainingSnapshotArtifactStore.ChunkPayload.builder()
                    .trainSamples(processedData.getTrainSamples())
                    .valSamples(processedData.getValSamples())
                    .testSamples(processedData.getTestSamples())
                    .sampleCounts(processedData.getSampleCounts())
                    .walkForwardFolds(processedData.getWalkForwardFolds())
                    .strictIsolationFolds(processedData.getStrictIsolationFolds())
                    .build();
            stage = "WRITE_CHUNK";
            TrainingSnapshotArtifactStore.ChunkDescriptor descriptor = artifactStore.writeChunk(
                    snapshotDirectory, 1, chunk);
            stage = "WRITE_MANIFEST";
            return completeRankingSnapshot(
                    document, snapshotDirectory, processedData, List.of(descriptor), startedNanos);
        } catch (Exception exception) {
            artifactStore.deleteArtifacts(document.getId());
            markFailed(document, stage, startedNanos, exception);
            if (("LOAD_VERSIONED_MODEL_DATA".equals(stage)
                    || "BUILD_CROSS_SECTIONAL_SAMPLES".equals(stage))
                    && exception instanceof IllegalStateException) {
                throw new ModelTrainingDataNotReadyException(
                        "训练数据无法形成可用快照: " + rootCauseMessage(exception), exception);
            }
            throw new IllegalStateException("构建 LSTM 训练数据快照失败", exception);
        }
    }

    private TrainingDatasetSnapshotDocument completeRankingSnapshot(
            TrainingDatasetSnapshotDocument document,
            Path snapshotDirectory,
            LstmDataPreprocessor.ProcessedData data,
            List<TrainingSnapshotArtifactStore.ChunkDescriptor> chunks,
            long startedNanos) throws IOException {
        LocalDate datasetEndDate = data.getTestEndDate() != null
                ? data.getTestEndDate() : data.getValidationEndDate();
        TrainingSnapshotArtifactStore.ManifestMetadata metadata =
                new TrainingSnapshotArtifactStore.ManifestMetadata(
                        data.getFeatureCount(), data.getSequenceLength(), data.getTargetReturnScale(),
                        data.getFeatureVersion(), 0, data.getSampleCounts().size(),
                        data.getTrainSamples().size(), data.getValSamples().size(), data.getTestSamples().size(),
                        datasetEndDate, data.getStockDictionary(), data.getIndustryDictionary(),
                        data.getMarketDictionary(), data.getFeatureMeans(), data.getFeatureStdDeviations(),
                        data.getTrainStartDate(), data.getTrainEndDate(), data.getValidationStartDate(),
                        data.getValidationEndDate(), data.getTestStartDate(), data.getTestEndDate());
        TrainingSnapshotArtifactStore.StoredArtifact artifact = artifactStore.persist(
                document.getId(), snapshotDirectory, metadata, chunks);
        document.setArtifactStorage(TrainingSnapshotArtifactStore.STORAGE_GRIDFS);
        document.setArtifactFileId(artifact.manifestFileId());
        document.setArtifactSha256(artifact.manifestSha256());
        document.setArtifactSize(artifact.artifactSize());
        document.setArtifactFormat(TrainingSnapshotArtifactStore.FORMAT);
        document.setChunkCount(chunks.size());
        document.setTrainedStocks(data.getSampleCounts().size());
        document.setTrainSamples(data.getTrainSamples().size());
        document.setValSamples(data.getValSamples().size());
        document.setTestSamples(data.getTestSamples().size());
        document.setWalkForwardFoldCount(data.getWalkForwardFolds() == null
                ? 0 : data.getWalkForwardFolds().size());
        document.setStrictIsolationFoldCount(data.getStrictIsolationFolds() == null
                ? 0 : data.getStrictIsolationFolds().size());
        document.setDatasetEndDate(datasetEndDate);
        document.setSplitPolicyVersion(RankingLstmDataPreprocessor.SPLIT_POLICY_VERSION);
        document.setDataSourceVersion(config.getDataSourceVersion());
        document.setTrainStartDate(data.getTrainStartDate());
        document.setTrainEndDate(data.getTrainEndDate());
        document.setValidationStartDate(data.getValidationStartDate());
        document.setValidationEndDate(data.getValidationEndDate());
        document.setTestStartDate(data.getTestStartDate());
        document.setTestEndDate(data.getTestEndDate());
        document.setStatus(READY);
        document.setFinishedAt(LocalDateTime.now());
        TrainingDatasetSnapshotDocument saved = snapshotRepository.save(document);
        artifactStore.cleanupTemporaryDirectory(document.getId());
        log.info("v5 LSTM 训练数据快照完成: snapshotId={}, stocks={}, train={}, validation={}, test={}, "
                        + "bytes={}, elapsedSeconds={}", document.getId(), document.getTrainedStocks(),
                document.getTrainSamples(), document.getValSamples(), document.getTestSamples(),
                document.getArtifactSize(), elapsedSeconds(startedNanos));
        return saved;
    }

    /**
     * 读取并校验 GridFS 分片快照工件。
     *
     * @param snapshot 快照元数据
     * @return 处理后的训练数据
     */
    public LstmDataPreprocessor.ProcessedData loadSnapshot(TrainingDatasetSnapshotDocument snapshot) {
        return artifactStore.load(snapshot);
    }

    private TrainingDatasetSnapshotDocument createBuildingDocument(
            String modelName, List<String> stockCodes, int days) {
        TrainingDatasetSnapshotDocument document = new TrainingDatasetSnapshotDocument();
        document.setModelName(modelName);
        document.setStatus("BUILDING");
        document.setContractHash(contractHash(modelName, stockCodes, days));
        document.setFeatureVersion(RankingLstmDataPreprocessor.FEATURE_VERSION);
        document.setDays(days);
        document.setStockCount(stockCodes.size());
        document.setArtifactFormat(TrainingSnapshotArtifactStore.FORMAT);
        document.setArtifactStorage(TrainingSnapshotArtifactStore.STORAGE_TEMPORARY);
        document.setCreatedAt(LocalDateTime.now());
        return snapshotRepository.save(document);
    }

    private void markFailed(
            TrainingDatasetSnapshotDocument document,
            String stage,
            long startedNanos,
            Exception exception) {
        document.setStatus("FAILED");
        document.setErrorMessage(exception.getMessage());
        document.setFinishedAt(LocalDateTime.now());
        snapshotRepository.save(document);
        log.error("LSTM 训练数据快照构建失败: snapshotId={}, stage={}, elapsedSeconds={}, reason={}",
                document.getId(), stage, elapsedSeconds(startedNanos), exception.getMessage(), exception);
    }

    private void notifySnapshotObserver(
            Consumer<TrainingDatasetSnapshotDocument> snapshotObserver,
            TrainingDatasetSnapshotDocument snapshot) {
        if (snapshotObserver != null) {
            snapshotObserver.accept(snapshot);
        }
    }

    private long elapsedSeconds(long startedNanos) {
        return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedNanos);
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new IllegalStateException("创建 SHA-256 摘要失败", exception);
        }
    }

    private String sha256(byte[] content) {
        return java.util.HexFormat.of().formatHex(messageDigest().digest(content));
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    /**
     * 训练数据快照准备结果。
     *
     * @param snapshot 已就绪快照
     * @param reused 是否复用了已有快照
     */
    public record SnapshotPreparationResult(
            TrainingDatasetSnapshotDocument snapshot,
            boolean reused) {
    }

}
// AI_GENERATE_END-----------------
