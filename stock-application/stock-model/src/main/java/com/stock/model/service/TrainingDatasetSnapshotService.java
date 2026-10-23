// AI_GENERATE_START-------
package com.stock.model.service;

import com.stock.data.entity.StockPrice;
import com.stock.data.repository.PriceRepository;
import com.stock.data.service.TradingCalendarService;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.entity.TrainingDatasetSnapshotDocument;
import com.stock.model.repository.TrainingDatasetSnapshotRepository;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * LSTM 训练数据快照服务。
 * 按股票流式完成行情查询、特征计算和样本切分，并将结果写入可校验的分片工件。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingDatasetSnapshotService {

    private static final String READY = "READY";
    private static final int STOCK_BATCH_SIZE = 100;
    private static final int PRICE_QUERY_THREADS = 6;
    private static final int PROGRESS_LOG_INTERVAL = 100;
    private static final int PRICE_QUERY_MAX_ATTEMPTS = 3;

    private final LstmTrainingConfig config;
    private final PriceRepository priceRepository;
    private final TradingCalendarService tradingCalendarService;
    private final LstmDataPreprocessor dataPreprocessor;
    private final LstmTrainingDataService trainingDataService;
    private final TrainingDatasetSnapshotRepository snapshotRepository;
    private final TrainingSnapshotArtifactStore artifactStore;

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
                LstmDataPreprocessor.FEATURE_VERSION, String.valueOf(config.getSequenceLength()),
                String.valueOf(config.getInputSize()), String.join(",", sortedCodes));
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
    public Optional<TrainingDatasetSnapshotDocument> findReusableSnapshot(
            String modelName, List<String> stockCodes, int days) {
        return snapshotRepository.findFirstByModelNameAndContractHashAndStatusOrderByCreatedAtDesc(
                modelName, contractHash(modelName, stockCodes, days), READY)
                .filter(artifactStore::isReadable)
                .filter(this::isFreshEnoughForNewTraining);
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
        LocalDate latestMarketDate = priceRepository.findTopByOrderByDateDesc()
                .map(StockPrice::getDate).orElse(null);
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

    /**
     * 从行情数据构建并保存一个新的分片训练数据快照。
     *
     * @param modelName 模型名称
     * @param stockCodes 股票代码
     * @param days 历史交易日数量
     * @return 已保存的快照
     */
    public TrainingDatasetSnapshotDocument buildSnapshot(String modelName, List<String> stockCodes, int days) {
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream().distinct().toList();
        TrainingDatasetSnapshotDocument document = createBuildingDocument(modelName, codes, days);
        Path snapshotDirectory = null;
        long startedNanos = System.nanoTime();
        String stage = "LOAD_STOCK_CONTEXTS";
        ExecutorService executor = null;
        log.info("LSTM 训练数据快照开始流式构建: snapshotId={}, modelName={}, stocks={}, days={}, "
                        + "stockBatchSize={}, queryThreads={}",
                document.getId(), modelName, codes.size(), days, STOCK_BATCH_SIZE, PRICE_QUERY_THREADS);
        try {
            snapshotDirectory = artifactStore.prepareSnapshotDirectory(document.getId());
            Map<String, LstmDataPreprocessor.StockContext> contexts = trainingDataService.loadStockContexts(codes);
            int sampleLimit = dataPreprocessor.resolveSampleLimit(codes.size());
            executor = createPriceQueryExecutor(codes.size());
            stage = "STREAM_STOCK_SAMPLES";
            SnapshotBuildAccumulator accumulator = new SnapshotBuildAccumulator();

            for (int batchStart = 0; batchStart < codes.size(); batchStart += STOCK_BATCH_SIZE) {
                int batchEnd = Math.min(codes.size(), batchStart + STOCK_BATCH_SIZE);
                List<ProcessedStockResult> batchResults = processStockBatch(
                        executor, codes, batchStart, batchEnd, days, sampleLimit, contexts);
                TrainingSnapshotArtifactStore.ChunkPayload chunk = buildChunk(batchResults, accumulator);
                if (!chunk.getSampleCounts().isEmpty()) {
                    TrainingSnapshotArtifactStore.ChunkDescriptor descriptor = artifactStore.writeChunk(
                            snapshotDirectory, accumulator.getChunkCount() + 1, chunk);
                    accumulator.addChunk(descriptor);
                }
                logBuildProgress(document.getId(), batchEnd, codes.size(), accumulator, startedNanos);
            }

            if (accumulator.getTrainSamples() <= 0) {
                throw new IllegalStateException("训练快照没有可用样本");
            }
            stage = "WRITE_MANIFEST";
            return completeSnapshot(document, snapshotDirectory, accumulator, sampleLimit, startedNanos);
        } catch (Exception exception) {
            artifactStore.deleteArtifacts(document.getId());
            markFailed(document, stage, startedNanos, exception);
            throw new IllegalStateException("构建 LSTM 训练数据快照失败", exception);
        } finally {
            shutdownExecutor(executor, document.getId());
        }
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
        document.setFeatureVersion(LstmDataPreprocessor.FEATURE_VERSION);
        document.setDays(days);
        document.setStockCount(stockCodes.size());
        document.setArtifactFormat(TrainingSnapshotArtifactStore.FORMAT);
        document.setArtifactStorage(TrainingSnapshotArtifactStore.STORAGE_TEMPORARY);
        document.setCreatedAt(LocalDateTime.now());
        return snapshotRepository.save(document);
    }

    private ExecutorService createPriceQueryExecutor(int stockCount) {
        return Executors.newFixedThreadPool(Math.min(PRICE_QUERY_THREADS, Math.max(1, stockCount)), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("snapshot-stock-stream-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    private List<ProcessedStockResult> processStockBatch(
            ExecutorService executor,
            List<String> stockCodes,
            int batchStart,
            int batchEnd,
            int days,
            int sampleLimit,
            Map<String, LstmDataPreprocessor.StockContext> contexts) throws Exception {
        CompletionService<ProcessedStockResult> completionService = new ExecutorCompletionService<>(executor);
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, days));
        for (int index = batchStart; index < batchEnd; index++) {
            int resultIndex = index;
            String stockCode = stockCodes.get(index);
            LstmDataPreprocessor.StockContext context = contexts.getOrDefault(
                    stockCode, LstmDataPreprocessor.StockContext.basic(stockCode));
            completionService.submit(() -> processStock(resultIndex, stockCode, pageRequest, context, sampleLimit));
        }

        List<ProcessedStockResult> orderedResults = new ArrayList<>(
                Collections.nCopies(batchEnd - batchStart, null));
        for (int completed = batchStart; completed < batchEnd; completed++) {
            ProcessedStockResult result = completionService.take().get();
            orderedResults.set(result.index() - batchStart, result);
        }
        return orderedResults;
    }

    private ProcessedStockResult processStock(
            int index,
            String stockCode,
            PageRequest pageRequest,
            LstmDataPreprocessor.StockContext context,
            int sampleLimit) {
        List<StockPrice> prices = loadPriceSeries(stockCode, pageRequest);
        if (prices.isEmpty()) {
            return new ProcessedStockResult(index, 0, null);
        }
        Collections.reverse(prices);
        LstmDataPreprocessor.StockProcessedData stockData =
                dataPreprocessor.processStockData(prices, context, sampleLimit);
        return new ProcessedStockResult(index, prices.size(), stockData);
    }

    private List<StockPrice> loadPriceSeries(String stockCode, PageRequest pageRequest) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= PRICE_QUERY_MAX_ATTEMPTS; attempt++) {
            try {
                return new ArrayList<>(priceRepository.findByCodeOrderByDateDesc(stockCode, pageRequest));
            } catch (RuntimeException exception) {
                lastException = exception;
                if (attempt >= PRICE_QUERY_MAX_ATTEMPTS) {
                    break;
                }
                long retryDelayMillis = attempt * 500L;
                log.warn("训练数据快照行情查询失败，准备重试: stockCode={}, attempt={}/{}, delayMillis={}, reason={}",
                        stockCode, attempt, PRICE_QUERY_MAX_ATTEMPTS, retryDelayMillis, exception.getMessage());
                sleepBeforeRetry(retryDelayMillis);
            }
        }
        throw new IllegalStateException("股票行情查询连续失败: " + stockCode, lastException);
    }

    private TrainingSnapshotArtifactStore.ChunkPayload buildChunk(
            List<ProcessedStockResult> results,
            SnapshotBuildAccumulator accumulator) {
        List<LstmDataPreprocessor.TrainingSample> trainSamples = new ArrayList<>();
        List<LstmDataPreprocessor.TrainingSample> valSamples = new ArrayList<>();
        Map<String, Integer> sampleCounts = new LinkedHashMap<>();
        for (ProcessedStockResult result : results) {
            accumulator.addPriceSeries(result.priceRows());
            if (result.priceRows() > 0) {
                accumulator.incrementStocksWithPrices();
            }
            LstmDataPreprocessor.StockProcessedData stockData = result.stockData();
            if (stockData == null) {
                continue;
            }
            trainSamples.addAll(stockData.getTrainSamples());
            valSamples.addAll(stockData.getValSamples());
            sampleCounts.put(stockData.getStockCode(), stockData.getSampleCount());
            accumulator.addStockData(stockData);
        }
        return TrainingSnapshotArtifactStore.ChunkPayload.builder()
                .trainSamples(trainSamples)
                .valSamples(valSamples)
                .sampleCounts(sampleCounts)
                .build();
    }

    private TrainingDatasetSnapshotDocument completeSnapshot(
            TrainingDatasetSnapshotDocument document,
            Path snapshotDirectory,
            SnapshotBuildAccumulator accumulator,
            int sampleLimit,
            long startedNanos) throws IOException {
        TrainingSnapshotArtifactStore.ManifestMetadata metadata =
                new TrainingSnapshotArtifactStore.ManifestMetadata(
                        LstmDataPreprocessor.FEATURE_COUNT,
                        config.getSequenceLength(),
                        config.getTargetReturnScale(),
                        LstmDataPreprocessor.FEATURE_VERSION,
                        sampleLimit,
                        accumulator.getTrainedStocks(),
                        accumulator.getTrainSamples(),
                        accumulator.getValSamples(),
                        accumulator.getDatasetEndDate());
        TrainingSnapshotArtifactStore.StoredArtifact artifact = artifactStore.persist(
                document.getId(), snapshotDirectory, metadata, accumulator.getChunks());

        document.setArtifactStorage(TrainingSnapshotArtifactStore.STORAGE_GRIDFS);
        document.setArtifactFileId(artifact.manifestFileId());
        document.setArtifactSha256(artifact.manifestSha256());
        document.setArtifactSize(artifact.artifactSize());
        document.setArtifactFormat(TrainingSnapshotArtifactStore.FORMAT);
        document.setChunkCount(accumulator.getChunkCount());
        document.setTrainedStocks(accumulator.getTrainedStocks());
        document.setTrainSamples(accumulator.getTrainSamples());
        document.setValSamples(accumulator.getValSamples());
        document.setDatasetEndDate(accumulator.getDatasetEndDate());
        document.setStatus(READY);
        document.setFinishedAt(LocalDateTime.now());
        TrainingDatasetSnapshotDocument saved = snapshotRepository.save(document);
        artifactStore.cleanupTemporaryDirectory(document.getId());
        log.info("LSTM 训练数据快照流式构建完成: snapshotId={}, stocks={}, trainSamples={}, valSamples={}, "
                        + "chunks={}, bytes={}, elapsedSeconds={}",
                document.getId(), document.getTrainedStocks(), document.getTrainSamples(),
                document.getValSamples(), document.getChunkCount(), document.getArtifactSize(),
                elapsedSeconds(startedNanos));
        return saved;
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

    private void logBuildProgress(
            String snapshotId,
            int completed,
            int total,
            SnapshotBuildAccumulator accumulator,
            long startedNanos) {
        if (completed % PROGRESS_LOG_INTERVAL != 0 && completed != total) {
            return;
        }
        long elapsedSeconds = elapsedSeconds(startedNanos);
        log.info("LSTM 训练数据快照流式进度: snapshotId={}, completed={}/{}, percent={}%, "
                        + "stocksWithPrices={}, trainedStocks={}, priceRows={}, trainSamples={}, valSamples={}, "
                        + "chunks={}, elapsedSeconds={}, estimatedRemainingSeconds={}",
                snapshotId, completed, total, completed * 100 / total,
                accumulator.getStocksWithPrices(), accumulator.getTrainedStocks(), accumulator.getPriceRows(),
                accumulator.getTrainSamples(), accumulator.getValSamples(), accumulator.getChunkCount(),
                elapsedSeconds, estimateRemainingSeconds(elapsedSeconds, completed, total));
    }

    private void sleepBeforeRetry(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("训练数据快照行情查询重试被中断", exception);
        }
    }

    private void shutdownExecutor(ExecutorService executor, String snapshotId) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("LSTM 训练数据快照查询线程未在限定时间退出: snapshotId={}", snapshotId);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private long elapsedSeconds(long startedNanos) {
        return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedNanos);
    }

    private long estimateRemainingSeconds(long elapsedSeconds, int completed, int total) {
        if (completed <= 0 || completed >= total) {
            return 0L;
        }
        return Math.max(0L, Math.round((double) elapsedSeconds * (total - completed) / completed));
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

    private record ProcessedStockResult(
            int index,
            int priceRows,
            LstmDataPreprocessor.StockProcessedData stockData) {
    }

    @Data
    private static class SnapshotBuildAccumulator {
        private final List<TrainingSnapshotArtifactStore.ChunkDescriptor> chunks = new ArrayList<>();
        private int stocksWithPrices;
        private int trainedStocks;
        private int trainSamples;
        private int valSamples;
        private int chunkCount;
        private long priceRows;
        private LocalDate datasetEndDate;

        private void incrementStocksWithPrices() {
            stocksWithPrices++;
        }

        private void addPriceSeries(int rows) {
            priceRows += rows;
        }

        private void addStockData(LstmDataPreprocessor.StockProcessedData stockData) {
            trainedStocks++;
            trainSamples += stockData.getTrainSamples().size();
            valSamples += stockData.getValSamples().size();
            if (stockData.getDatasetEndDate() != null
                    && (datasetEndDate == null || stockData.getDatasetEndDate().isAfter(datasetEndDate))) {
                datasetEndDate = stockData.getDatasetEndDate();
            }
        }

        private void addChunk(TrainingSnapshotArtifactStore.ChunkDescriptor descriptor) {
            chunks.add(descriptor);
            chunkCount++;
        }

    }

}
// AI_GENERATE_END-------
