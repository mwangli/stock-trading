// AI_GENERATE_START ---------
package com.stock.modelService.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.service.TradingCalendarService;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.domain.entity.TrainingDatasetSnapshotDocument;
import com.stock.modelService.persistence.TrainingDatasetSnapshotRepository;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
    private static final String CHUNKED_FORMAT = "CHUNKED_JSON_GZIP_V1";
    private static final String STORAGE_MONGO_GRIDFS = "MONGO_GRIDFS";
    private static final String STORAGE_LOCAL_FILE = "LOCAL_FILE";
    private static final int STOCK_BATCH_SIZE = 100;
    private static final int PRICE_QUERY_THREADS = 6;
    private static final int PROGRESS_LOG_INTERVAL = 100;
    private static final int PRICE_QUERY_MAX_ATTEMPTS = 3;

    private final LstmTrainingConfig config;
    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final TradingCalendarService tradingCalendarService;
    private final LstmDataPreprocessor dataPreprocessor;
    private final TrainingDatasetSnapshotRepository snapshotRepository;
    private final GridFsTemplate gridFsTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 应用启动时清理上次进程中断留下的快照半成品和已持久化快照的本地副本。
     */
    @PostConstruct
    public void cleanupInterruptedSnapshotArtifacts() {
        Path artifactRoot = artifactRoot();
        List<TrainingDatasetSnapshotDocument> interrupted = snapshotRepository.findByStatus("BUILDING");
        for (TrainingDatasetSnapshotDocument snapshot : interrupted) {
            deleteGridFsSnapshot(snapshot.getId());
            cleanupSnapshotDirectory(snapshotDirectory(artifactRoot, snapshot.getId()), artifactRoot);
            snapshot.setStatus("FAILED");
            snapshot.setFinishedAt(LocalDateTime.now());
            snapshot.setErrorMessage("应用重启，已清理未完成的训练快照工件");
            snapshotRepository.save(snapshot);
            log.warn("已清理应用重启前遗留的训练快照: snapshotId={}", snapshot.getId());
        }
        snapshotRepository.findByStatus(READY).stream()
                .filter(snapshot -> STORAGE_MONGO_GRIDFS.equals(snapshot.getArtifactStorage()))
                .forEach(snapshot -> cleanupSnapshotDirectory(
                        snapshotDirectory(artifactRoot, snapshot.getId()), artifactRoot));
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
                .filter(this::isArtifactReadable)
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
        Path artifactRoot = artifactRoot();
        Path snapshotDirectory = snapshotDirectory(artifactRoot, document.getId());
        long startedNanos = System.nanoTime();
        String stage = "LOAD_STOCK_CONTEXTS";
        ExecutorService executor = null;
        log.info("LSTM 训练数据快照开始流式构建: snapshotId={}, modelName={}, stocks={}, days={}, "
                        + "stockBatchSize={}, queryThreads={}",
                document.getId(), modelName, codes.size(), days, STOCK_BATCH_SIZE, PRICE_QUERY_THREADS);
        try {
            Files.createDirectories(snapshotDirectory);
            Map<String, LstmDataPreprocessor.StockContext> contexts = loadStockContexts(codes);
            int sampleLimit = dataPreprocessor.resolveSampleLimit(codes.size());
            executor = createPriceQueryExecutor(codes.size());
            stage = "STREAM_STOCK_SAMPLES";
            SnapshotBuildAccumulator accumulator = new SnapshotBuildAccumulator();

            for (int batchStart = 0; batchStart < codes.size(); batchStart += STOCK_BATCH_SIZE) {
                int batchEnd = Math.min(codes.size(), batchStart + STOCK_BATCH_SIZE);
                List<ProcessedStockResult> batchResults = processStockBatch(
                        executor, codes, batchStart, batchEnd, days, sampleLimit, contexts);
                SnapshotChunk chunk = buildChunk(batchResults, accumulator);
                if (!chunk.getSampleCounts().isEmpty()) {
                    ChunkDescriptor descriptor = writeChunk(
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
            deleteGridFsSnapshot(document.getId());
            cleanupSnapshotDirectory(snapshotDirectory, artifactRoot);
            markFailed(document, stage, startedNanos, exception);
            throw new IllegalStateException("构建 LSTM 训练数据快照失败", exception);
        } finally {
            shutdownExecutor(executor, document.getId());
        }
    }

    /**
     * 持久化已经完成预处理的训练数据。
     *
     * 该兼容入口仍接收完整内存数据，但写盘过程改为分股票分片，避免再次构造超大压缩字节数组。
     *
     * @param modelName 模型名称
     * @param stockCodes 股票代码
     * @param days 历史交易日数量
     * @param processedData 已完成预处理的数据
     * @return 已保存的快照
     */
    public TrainingDatasetSnapshotDocument saveSnapshot(String modelName, List<String> stockCodes, int days,
                                                        LstmDataPreprocessor.ProcessedData processedData) {
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream().distinct().toList();
        TrainingDatasetSnapshotDocument document = createBuildingDocument(modelName, codes, days);
        Path artifactRoot = artifactRoot();
        Path snapshotDirectory = snapshotDirectory(artifactRoot, document.getId());
        long startedNanos = System.nanoTime();
        try {
            Files.createDirectories(snapshotDirectory);
            SnapshotBuildAccumulator accumulator = writeProcessedDataChunks(snapshotDirectory, processedData);
            if (accumulator.getTrainSamples() <= 0) {
                throw new IllegalStateException("训练快照没有可用样本");
            }
            return completeSnapshot(document, snapshotDirectory, accumulator,
                    dataPreprocessor.resolveSampleLimit(codes.size()), startedNanos);
        } catch (Exception exception) {
            deleteGridFsSnapshot(document.getId());
            cleanupSnapshotDirectory(snapshotDirectory, artifactRoot);
            markFailed(document, "WRITE_PROCESSED_DATA", startedNanos, exception);
            throw new IllegalStateException("构建 LSTM 训练数据快照失败", exception);
        }
    }

    /**
     * 读取并校验快照工件。
     *
     * 同时兼容历史单文件 GZIP 快照和新的 manifest + GZIP 分片快照。
     *
     * @param snapshot 快照元数据
     * @return 处理后的训练数据
     */
    public LstmDataPreprocessor.ProcessedData loadSnapshot(TrainingDatasetSnapshotDocument snapshot) {
        if (snapshot == null || !READY.equals(snapshot.getStatus()) || !isArtifactReadable(snapshot)) {
            throw new IllegalStateException("训练数据快照不存在、未完成或工件不可读");
        }
        if (STORAGE_MONGO_GRIDFS.equals(snapshot.getArtifactStorage())) {
            return readGridFsSnapshot(snapshot);
        }
        Path artifactPath = Path.of(snapshot.getArtifactPath()).toAbsolutePath().normalize();
        try {
            if (!snapshot.getArtifactSha256().equalsIgnoreCase(hashFile(artifactPath))) {
                throw new IllegalStateException("训练数据快照校验摘要不一致");
            }
            LstmDataPreprocessor.ProcessedData processedData;
            if (artifactPath.getFileName().toString().endsWith(".json.gz")) {
                processedData = readLegacySnapshot(artifactPath);
            } else {
                processedData = readChunkedSnapshot(artifactPath);
            }
            migrateLocalSnapshotToGridFs(snapshot, artifactPath);
            return processedData;
        } catch (IOException exception) {
            throw new IllegalStateException("读取训练数据快照失败", exception);
        }
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
        document.setArtifactFormat(CHUNKED_FORMAT);
        document.setArtifactStorage(STORAGE_LOCAL_FILE);
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

    private SnapshotChunk buildChunk(
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
        return SnapshotChunk.builder()
                .trainSamples(trainSamples)
                .valSamples(valSamples)
                .sampleCounts(sampleCounts)
                .build();
    }

    private ChunkDescriptor writeChunk(Path snapshotDirectory, int chunkNumber, SnapshotChunk chunk)
            throws IOException {
        String fileName = "chunk-" + String.format("%05d", chunkNumber) + ".json.gz";
        WrittenFile file = writeGzipJson(snapshotDirectory.resolve(fileName), chunk);
        log.info("LSTM 训练数据快照分片写入完成: chunk={}, stocks={}, trainSamples={}, valSamples={}, bytes={}",
                fileName, chunk.getSampleCounts().size(), chunk.getTrainSamples().size(),
                chunk.getValSamples().size(), file.size());
        return ChunkDescriptor.builder()
                .fileName(fileName)
                .sha256(file.sha256())
                .size(file.size())
                .stockCount(chunk.getSampleCounts().size())
                .trainSamples(chunk.getTrainSamples().size())
                .valSamples(chunk.getValSamples().size())
                .build();
    }

    private TrainingDatasetSnapshotDocument completeSnapshot(
            TrainingDatasetSnapshotDocument document,
            Path snapshotDirectory,
            SnapshotBuildAccumulator accumulator,
            int sampleLimit,
            long startedNanos) throws IOException {
        SnapshotManifest manifest = SnapshotManifest.builder()
                .formatVersion(CHUNKED_FORMAT)
                .featureCount(LstmDataPreprocessor.FEATURE_COUNT)
                .sequenceLength(config.getSequenceLength())
                .targetReturnScale(config.getTargetReturnScale())
                .featureVersion(LstmDataPreprocessor.FEATURE_VERSION)
                .sampleLimitPerStock(sampleLimit)
                .trainedStocks(accumulator.getTrainedStocks())
                .trainSamples(accumulator.getTrainSamples())
                .valSamples(accumulator.getValSamples())
                .datasetEndDate(accumulator.getDatasetEndDate())
                .chunks(accumulator.getChunks())
                .build();
        Path manifestPath = snapshotDirectory.resolve("manifest.json");
        WrittenFile manifestFile = writeJson(manifestPath, manifest);
        long artifactSize = manifestFile.size() + accumulator.getChunkBytes();
        String manifestFileId = persistSnapshotToGridFs(
                document.getId(), snapshotDirectory, manifestPath, accumulator.getChunks());

        document.setArtifactStorage(STORAGE_MONGO_GRIDFS);
        document.setArtifactFileId(manifestFileId);
        document.setArtifactPath(null);
        document.setArtifactSha256(manifestFile.sha256());
        document.setArtifactSize(artifactSize);
        document.setArtifactFormat(CHUNKED_FORMAT);
        document.setChunkCount(accumulator.getChunkCount());
        document.setTrainedStocks(accumulator.getTrainedStocks());
        document.setTrainSamples(accumulator.getTrainSamples());
        document.setValSamples(accumulator.getValSamples());
        document.setDatasetEndDate(accumulator.getDatasetEndDate());
        document.setStatus(READY);
        document.setFinishedAt(LocalDateTime.now());
        TrainingDatasetSnapshotDocument saved = snapshotRepository.save(document);
        cleanupSnapshotDirectory(snapshotDirectory, artifactRoot());
        log.info("LSTM 训练数据快照流式构建完成: snapshotId={}, stocks={}, trainSamples={}, valSamples={}, "
                        + "chunks={}, bytes={}, elapsedSeconds={}",
                document.getId(), document.getTrainedStocks(), document.getTrainSamples(),
                document.getValSamples(), document.getChunkCount(), document.getArtifactSize(),
                elapsedSeconds(startedNanos));
        return saved;
    }

    private SnapshotBuildAccumulator writeProcessedDataChunks(
            Path snapshotDirectory,
            LstmDataPreprocessor.ProcessedData processedData) throws IOException {
        if (processedData == null || processedData.getTrainSamples() == null
                || processedData.getTrainSamples().isEmpty()) {
            throw new IllegalStateException("训练快照没有可用样本");
        }
        SnapshotBuildAccumulator accumulator = new SnapshotBuildAccumulator();
        List<String> stockCodes = new ArrayList<>(processedData.getSampleCounts().keySet());
        for (int start = 0; start < stockCodes.size(); start += STOCK_BATCH_SIZE) {
            int end = Math.min(stockCodes.size(), start + STOCK_BATCH_SIZE);
            Set<String> chunkCodes = Set.copyOf(stockCodes.subList(start, end));
            List<LstmDataPreprocessor.TrainingSample> trainSamples = processedData.getTrainSamples().stream()
                    .filter(sample -> chunkCodes.contains(sample.getStockCode())).toList();
            List<LstmDataPreprocessor.TrainingSample> valSamples = processedData.getValSamples().stream()
                    .filter(sample -> chunkCodes.contains(sample.getStockCode())).toList();
            Map<String, Integer> sampleCounts = new LinkedHashMap<>();
            stockCodes.subList(start, end).forEach(code ->
                    sampleCounts.put(code, processedData.getSampleCounts().get(code)));
            SnapshotChunk chunk = SnapshotChunk.builder()
                    .trainSamples(trainSamples)
                    .valSamples(valSamples)
                    .sampleCounts(sampleCounts)
                    .build();
            ChunkDescriptor descriptor = writeChunk(
                    snapshotDirectory, accumulator.getChunkCount() + 1, chunk);
            accumulator.addChunk(descriptor);
            accumulator.addProcessedChunk(chunk);
        }
        accumulator.setDatasetEndDate(findDatasetEndDate(processedData));
        return accumulator;
    }

    private LstmDataPreprocessor.ProcessedData readLegacySnapshot(Path artifactPath) throws IOException {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(artifactPath))) {
            return objectMapper.readValue(input, LstmDataPreprocessor.ProcessedData.class);
        }
    }

    private LstmDataPreprocessor.ProcessedData readChunkedSnapshot(Path manifestPath) throws IOException {
        SnapshotManifest manifest = objectMapper.readValue(manifestPath.toFile(), SnapshotManifest.class);
        if (!CHUNKED_FORMAT.equals(manifest.getFormatVersion())) {
            throw new IllegalStateException("不支持的训练快照格式: " + manifest.getFormatVersion());
        }
        List<LstmDataPreprocessor.TrainingSample> trainSamples = new ArrayList<>(manifest.getTrainSamples());
        List<LstmDataPreprocessor.TrainingSample> valSamples = new ArrayList<>(manifest.getValSamples());
        Map<String, Integer> sampleCounts = new LinkedHashMap<>();
        Path snapshotDirectory = manifestPath.getParent();
        for (ChunkDescriptor descriptor : manifest.getChunks()) {
            Path chunkPath = resolveChunkPath(snapshotDirectory, descriptor.getFileName());
            if (!Files.isRegularFile(chunkPath) || Files.size(chunkPath) != descriptor.getSize()
                    || !descriptor.getSha256().equalsIgnoreCase(hashFile(chunkPath))) {
                throw new IllegalStateException("训练数据快照分片校验失败: " + descriptor.getFileName());
            }
            SnapshotChunk chunk;
            try (InputStream input = new GZIPInputStream(Files.newInputStream(chunkPath))) {
                chunk = objectMapper.readValue(input, SnapshotChunk.class);
            }
            trainSamples.addAll(chunk.getTrainSamples());
            valSamples.addAll(chunk.getValSamples());
            sampleCounts.putAll(chunk.getSampleCounts());
        }
        if (trainSamples.size() != manifest.getTrainSamples()
                || valSamples.size() != manifest.getValSamples()
                || sampleCounts.size() != manifest.getTrainedStocks()) {
            throw new IllegalStateException("训练数据快照分片汇总数量不一致");
        }
        return LstmDataPreprocessor.ProcessedData.builder()
                .trainSamples(trainSamples)
                .valSamples(valSamples)
                .sampleCounts(sampleCounts)
                .featureCount(manifest.getFeatureCount())
                .sequenceLength(manifest.getSequenceLength())
                .targetReturnScale(manifest.getTargetReturnScale())
                .featureVersion(manifest.getFeatureVersion())
                .build();
    }

    private LstmDataPreprocessor.ProcessedData readGridFsSnapshot(
            TrainingDatasetSnapshotDocument snapshot) {
        GridFSFile manifestFile = findGridFsFileById(snapshot.getArtifactFileId());
        if (manifestFile == null) {
            throw new IllegalStateException("训练数据快照 GridFS 清单不存在: " + snapshot.getId());
        }
        if (!CHUNKED_FORMAT.equals(snapshot.getArtifactFormat())) {
            return readLegacyGridFsSnapshot(snapshot, manifestFile);
        }
        try {
            GridFsResource manifestResource = gridFsTemplate.getResource(manifestFile);
            byte[] manifestBytes;
            try (InputStream input = manifestResource.getInputStream()) {
                manifestBytes = input.readAllBytes();
            }
            if (!snapshot.getArtifactSha256().equalsIgnoreCase(sha256(manifestBytes))) {
                throw new IllegalStateException("训练数据快照 GridFS 清单摘要不一致");
            }
            SnapshotManifest manifest = objectMapper.readValue(manifestBytes, SnapshotManifest.class);
            return readGridFsChunks(snapshot.getId(), manifest);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 MongoDB GridFS 训练快照失败", exception);
        }
    }

    private LstmDataPreprocessor.ProcessedData readLegacyGridFsSnapshot(
            TrainingDatasetSnapshotDocument snapshot, GridFSFile artifactFile) {
        if (snapshot.getArtifactSize() > 0 && artifactFile.getLength() != snapshot.getArtifactSize()) {
            throw new IllegalStateException("历史训练快照 GridFS 文件大小不一致");
        }
        if (!snapshot.getArtifactSha256().equalsIgnoreCase(hashGridFsFile(artifactFile))) {
            throw new IllegalStateException("历史训练快照 GridFS 摘要不一致");
        }
        try (InputStream source = gridFsTemplate.getResource(artifactFile).getInputStream();
             InputStream gzip = new GZIPInputStream(source)) {
            return objectMapper.readValue(gzip, LstmDataPreprocessor.ProcessedData.class);
        } catch (IOException exception) {
            throw new IllegalStateException("读取历史 MongoDB GridFS 训练快照失败", exception);
        }
    }

    private LstmDataPreprocessor.ProcessedData readGridFsChunks(
            String snapshotId, SnapshotManifest manifest) throws IOException {
        if (!CHUNKED_FORMAT.equals(manifest.getFormatVersion())) {
            throw new IllegalStateException("不支持的训练快照格式: " + manifest.getFormatVersion());
        }
        List<LstmDataPreprocessor.TrainingSample> trainSamples = new ArrayList<>(manifest.getTrainSamples());
        List<LstmDataPreprocessor.TrainingSample> valSamples = new ArrayList<>(manifest.getValSamples());
        Map<String, Integer> sampleCounts = new LinkedHashMap<>();
        for (ChunkDescriptor descriptor : manifest.getChunks()) {
            GridFSFile chunkFile = findGridFsSnapshotFile(snapshotId, descriptor.getFileName());
            if (chunkFile == null || chunkFile.getLength() != descriptor.getSize()) {
                throw new IllegalStateException("训练数据快照 GridFS 分片不存在或大小不一致: "
                        + descriptor.getFileName());
            }
            if (!descriptor.getSha256().equalsIgnoreCase(hashGridFsFile(chunkFile))) {
                throw new IllegalStateException("训练数据快照 GridFS 分片摘要不一致: "
                        + descriptor.getFileName());
            }
            SnapshotChunk chunk;
            try (InputStream source = gridFsTemplate.getResource(chunkFile).getInputStream();
                 InputStream gzip = new GZIPInputStream(source)) {
                chunk = objectMapper.readValue(gzip, SnapshotChunk.class);
            }
            trainSamples.addAll(chunk.getTrainSamples());
            valSamples.addAll(chunk.getValSamples());
            sampleCounts.putAll(chunk.getSampleCounts());
        }
        if (trainSamples.size() != manifest.getTrainSamples()
                || valSamples.size() != manifest.getValSamples()
                || sampleCounts.size() != manifest.getTrainedStocks()) {
            throw new IllegalStateException("训练数据快照 GridFS 分片汇总数量不一致");
        }
        return LstmDataPreprocessor.ProcessedData.builder()
                .trainSamples(trainSamples)
                .valSamples(valSamples)
                .sampleCounts(sampleCounts)
                .featureCount(manifest.getFeatureCount())
                .sequenceLength(manifest.getSequenceLength())
                .targetReturnScale(manifest.getTargetReturnScale())
                .featureVersion(manifest.getFeatureVersion())
                .build();
    }

    private String persistSnapshotToGridFs(
            String snapshotId,
            Path snapshotDirectory,
            Path manifestPath,
            List<ChunkDescriptor> chunks) throws IOException {
        deleteGridFsSnapshot(snapshotId);
        try {
            for (ChunkDescriptor descriptor : chunks) {
                Path chunkPath = resolveChunkPath(snapshotDirectory, descriptor.getFileName());
                storeGridFsFile(snapshotId, descriptor.getFileName(), "application/gzip", chunkPath);
            }
            return storeGridFsFile(snapshotId, manifestPath.getFileName().toString(),
                    "application/json", manifestPath).toHexString();
        } catch (RuntimeException | IOException exception) {
            deleteGridFsSnapshot(snapshotId);
            throw exception;
        }
    }

    private void migrateLocalSnapshotToGridFs(
            TrainingDatasetSnapshotDocument snapshot, Path artifactPath) {
        String artifactFileId;
        try {
            if (artifactPath.getFileName().toString().endsWith(".json.gz")) {
                deleteGridFsSnapshot(snapshot.getId());
                artifactFileId = storeGridFsFile(snapshot.getId(), artifactPath.getFileName().toString(),
                        "application/gzip", artifactPath).toHexString();
            } else {
                SnapshotManifest manifest = objectMapper.readValue(artifactPath.toFile(), SnapshotManifest.class);
                artifactFileId = persistSnapshotToGridFs(
                        snapshot.getId(), artifactPath.getParent(), artifactPath, manifest.getChunks());
            }
            snapshot.setArtifactStorage(STORAGE_MONGO_GRIDFS);
            snapshot.setArtifactFileId(artifactFileId);
            snapshot.setArtifactPath(null);
            snapshotRepository.save(snapshot);
        } catch (Exception exception) {
            deleteGridFsSnapshot(snapshot.getId());
            log.warn("历史本地训练快照迁移 GridFS 失败，继续保留原文件: snapshotId={}, path={}, reason={}",
                    snapshot.getId(), artifactPath, exception.getMessage());
            return;
        }
        try {
            if (artifactPath.getFileName().toString().endsWith(".json.gz")) {
                Files.deleteIfExists(artifactPath);
            } else {
                cleanupSnapshotDirectory(artifactPath.getParent(), artifactRoot());
            }
            log.info("历史本地训练快照已迁移到 MongoDB GridFS: snapshotId={}, fileId={}",
                    snapshot.getId(), artifactFileId);
        } catch (Exception exception) {
            log.warn("历史本地训练快照已迁移 GridFS，但原文件清理失败: snapshotId={}, path={}, reason={}",
                    snapshot.getId(), artifactPath, exception.getMessage());
        }
    }

    private ObjectId storeGridFsFile(
            String snapshotId, String fileName, String contentType, Path path) throws IOException {
        Document metadata = new Document("snapshotId", snapshotId)
                .append("artifactType", "LSTM_TRAINING_SNAPSHOT")
                .append("fileName", fileName);
        try (InputStream input = Files.newInputStream(path)) {
            return gridFsTemplate.store(input, fileName, contentType, metadata);
        }
    }

    private GridFSFile findGridFsSnapshotFile(String snapshotId, String fileName) {
        return gridFsTemplate.findOne(Query.query(Criteria.where("metadata.snapshotId").is(snapshotId)
                .and("metadata.fileName").is(fileName)));
    }

    private GridFSFile findGridFsFileById(String fileId) {
        if (fileId == null || !ObjectId.isValid(fileId)) {
            return null;
        }
        return gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(fileId))));
    }

    private String hashGridFsFile(GridFSFile file) {
        MessageDigest digest = messageDigest();
        try (InputStream source = gridFsTemplate.getResource(file).getInputStream();
             DigestInputStream digestInput = new DigestInputStream(source, digest)) {
            digestInput.transferTo(OutputStream.nullOutputStream());
            return hexDigest(digest);
        } catch (IOException exception) {
            throw new IllegalStateException("计算 MongoDB GridFS 文件摘要失败", exception);
        }
    }

    private void deleteGridFsSnapshot(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return;
        }
        gridFsTemplate.delete(Query.query(Criteria.where("metadata.snapshotId").is(snapshotId)));
    }

    private Map<String, LstmDataPreprocessor.StockContext> loadStockContexts(List<String> stockCodes) {
        return stockInfoRepository.findByCodeIn(stockCodes).stream()
                .collect(java.util.stream.Collectors.toMap(
                        StockInfo::getCode,
                        stock -> LstmDataPreprocessor.StockContext.builder()
                                .stockCode(stock.getCode()).industryCode(stock.getIndustryCode())
                                .market(stock.getMarket()).build(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private WrittenFile writeGzipJson(Path path, Object value) throws IOException {
        MessageDigest digest = messageDigest();
        try (OutputStream file = Files.newOutputStream(path);
             DigestOutputStream digestOutput = new DigestOutputStream(file, digest);
             GZIPOutputStream gzip = new GZIPOutputStream(digestOutput)) {
            objectMapper.writeValue(gzip, value);
        }
        return new WrittenFile(Files.size(path), hexDigest(digest));
    }

    private WrittenFile writeJson(Path path, Object value) throws IOException {
        MessageDigest digest = messageDigest();
        try (OutputStream file = Files.newOutputStream(path);
             DigestOutputStream digestOutput = new DigestOutputStream(file, digest)) {
            objectMapper.writeValue(digestOutput, value);
        }
        return new WrittenFile(Files.size(path), hexDigest(digest));
    }

    private String hashFile(Path path) throws IOException {
        MessageDigest digest = messageDigest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return hexDigest(digest);
    }

    private Path resolveChunkPath(Path snapshotDirectory, String fileName) {
        Path normalizedDirectory = snapshotDirectory.toAbsolutePath().normalize();
        Path chunkPath = normalizedDirectory.resolve(fileName).normalize();
        if (!chunkPath.startsWith(normalizedDirectory)) {
            throw new IllegalStateException("训练数据快照分片路径越界: " + fileName);
        }
        return chunkPath;
    }

    private boolean isArtifactReadable(TrainingDatasetSnapshotDocument document) {
        if (STORAGE_MONGO_GRIDFS.equals(document.getArtifactStorage())) {
            return findGridFsFileById(document.getArtifactFileId()) != null;
        }
        return document.getArtifactPath() != null && document.getArtifactSha256() != null
                && Files.isRegularFile(Path.of(document.getArtifactPath()));
    }

    private Path artifactRoot() {
        return Path.of(config.getArtifactDirectory()).toAbsolutePath().normalize();
    }

    private Path snapshotDirectory(Path artifactRoot, String snapshotId) {
        Path directory = artifactRoot.resolve("snapshot-" + snapshotId).normalize();
        if (!directory.startsWith(artifactRoot)) {
            throw new IllegalStateException("训练数据快照目录越界");
        }
        return directory;
    }

    private void cleanupSnapshotDirectory(Path snapshotDirectory, Path artifactRoot) {
        Path normalizedRoot = artifactRoot.toAbsolutePath().normalize();
        Path normalizedDirectory = snapshotDirectory.toAbsolutePath().normalize();
        if (!normalizedDirectory.startsWith(normalizedRoot) || normalizedDirectory.equals(normalizedRoot)
                || !Files.exists(normalizedDirectory)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(normalizedDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("清理失败的训练快照临时文件失败: path={}, reason={}", path, exception.getMessage());
                }
            });
        } catch (IOException exception) {
            log.warn("清理失败的训练快照目录失败: path={}, reason={}",
                    normalizedDirectory, exception.getMessage());
        }
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

    private LocalDate findDatasetEndDate(LstmDataPreprocessor.ProcessedData data) {
        return java.util.stream.Stream.concat(data.getTrainSamples().stream(), data.getValSamples().stream())
                .map(LstmDataPreprocessor.TrainingSample::getTargetDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo).orElse(null);
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

    private String hexDigest(MessageDigest digest) {
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private record ProcessedStockResult(
            int index,
            int priceRows,
            LstmDataPreprocessor.StockProcessedData stockData) {
    }

    private record WrittenFile(long size, String sha256) {
    }

    @Data
    private static class SnapshotBuildAccumulator {
        private final List<ChunkDescriptor> chunks = new ArrayList<>();
        private int stocksWithPrices;
        private int trainedStocks;
        private int trainSamples;
        private int valSamples;
        private int chunkCount;
        private long priceRows;
        private long chunkBytes;
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

        private void addChunk(ChunkDescriptor descriptor) {
            chunks.add(descriptor);
            chunkCount++;
            chunkBytes += descriptor.getSize();
        }

        private void addProcessedChunk(SnapshotChunk chunk) {
            trainedStocks += chunk.getSampleCounts().size();
            trainSamples += chunk.getTrainSamples().size();
            valSamples += chunk.getValSamples().size();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SnapshotManifest {
        /** 快照存储格式版本。 */
        private String formatVersion;
        /** 特征数量。 */
        private int featureCount;
        /** 输入序列长度。 */
        private int sequenceLength;
        /** 标签缩放值。 */
        private double targetReturnScale;
        /** 特征定义版本。 */
        private String featureVersion;
        /** 单只股票样本上限。 */
        private int sampleLimitPerStock;
        /** 有效训练股票数量。 */
        private int trainedStocks;
        /** 训练样本数量。 */
        private int trainSamples;
        /** 验证样本数量。 */
        private int valSamples;
        /** 数据截止日期。 */
        private LocalDate datasetEndDate;
        /** 分片描述列表。 */
        private List<ChunkDescriptor> chunks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ChunkDescriptor {
        /** 分片文件名。 */
        private String fileName;
        /** 分片 SHA-256。 */
        private String sha256;
        /** 分片大小，单位字节。 */
        private long size;
        /** 分片有效股票数量。 */
        private int stockCount;
        /** 分片训练样本数量。 */
        private int trainSamples;
        /** 分片验证样本数量。 */
        private int valSamples;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SnapshotChunk {
        /** 训练样本。 */
        private List<LstmDataPreprocessor.TrainingSample> trainSamples;
        /** 验证样本。 */
        private List<LstmDataPreprocessor.TrainingSample> valSamples;
        /** 各股票样本数量。 */
        private Map<String, Integer> sampleCounts;
    }
}
// AI_GENERATE_END ---------
