// AI_GENERATE_START ----
package com.stock.modelService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.config.LstmModelConfig;
import com.stock.modelService.config.SentimentModelConfig;
import com.stock.modelService.domain.dto.TrainingSample;
import com.stock.modelService.domain.dto.TrainingSnapshotManifest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 Java 权威特征和文本清洗结果导出为 Python 可严格校验的不可变训练快照。
 * 本服务不训练模型、不激活模型，也不注册为生产接口或定时任务。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingSnapshotExportService {

    /** 当前快照 Schema 契约版本。 */
    public static final String SCHEMA_VERSION = "training-snapshot-v1";

    /** LSTM 三任务标签版本。 */
    public static final String LSTM_LABEL_VERSION = "return-direction-downside-v3";

    /** 情感标签版本。 */
    public static final String SENTIMENT_LABEL_VERSION = "finbert-labels-v1";

    /** 情感文本清洗特征版本。 */
    public static final String SENTIMENT_FEATURE_VERSION = "sentiment-clean-v1";

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATASET_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final LstmDataPreprocessor lstmDataPreprocessor;
    private final SentimentDataPreprocessor sentimentDataPreprocessor;
    private final LstmModelConfig lstmModelConfig;
    private final SentimentModelConfig sentimentModelConfig;

    /**
     * 导出 LSTM 面板训练快照。
     *
     * @param stockCodes 股票代码列表
     * @param days 每只股票读取的最近交易日数量
     * @param outputRoot 快照输出根目录
     * @return 新快照目录
     * @throws IOException 文件写入或摘要计算失败
     */
    public Path exportLstmSnapshot(List<String> stockCodes, int days, Path outputRoot) throws IOException {
        List<String> codes = normalizeStockCodes(stockCodes);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("LSTM 快照股票代码不能为空");
        }
        int fetchDays = Math.max(days,
                lstmModelConfig.getSequenceLength() + lstmModelConfig.getValidationGap() + 2);
        Map<String, List<StockPrice>> priceSeries = loadPriceSeries(codes, fetchDays);
        Map<String, LstmDataPreprocessor.StockContext> contexts = loadStockContexts(codes);
        LstmDataPreprocessor.ProcessedData processedData =
                lstmDataPreprocessor.processPanelData(priceSeries, contexts);
        if (processedData == null || processedData.getTrainSamples().isEmpty()) {
            throw new IllegalStateException("没有可导出的 LSTM 训练样本");
        }

        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        Path snapshotDir = createSnapshotDirectory(outputRoot, "lstm-panel", createdAt);
        TrainingSnapshotManifest.DatasetSchema schema = buildLstmSchema(processedData);
        Path schemaPath = snapshotDir.resolve("schema.json");
        writePrettyJson(schemaPath, schema);

        Path trainPath = snapshotDir.resolve("train.jsonl");
        long trainCount = writeLstmSamples(trainPath, "train", processedData.getTrainSamples());
        Path validationPath = snapshotDir.resolve("validation.jsonl");
        long validationCount = writeLstmSamples(
                validationPath, "validation", processedData.getValSamples());
        OffsetDateTime dataCutoff = lstmDataCutoff(processedData);
        validateDataCutoff(dataCutoff, createdAt);

        List<TrainingSnapshotManifest.FileEntry> files = List.of(
                fileEntry(snapshotDir, schemaPath, "application/json", "schema", 0),
                fileEntry(snapshotDir, trainPath, "application/x-ndjson", "train", trainCount),
                fileEntry(snapshotDir, validationPath, "application/x-ndjson",
                        "validation", validationCount));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schema_file", "schema.json");
        metadata.put("format", "jsonl");
        metadata.put("encoding", "utf-8");
        metadata.put("sequence_length", processedData.getSequenceLength());
        metadata.put("feature_count", processedData.getFeatureCount());
        metadata.put("target_return_scale", processedData.getTargetReturnScale());
        metadata.put("validation_gap", lstmModelConfig.getValidationGap());
        metadata.put("sample_counts_by_stock", processedData.getSampleCounts());
        metadata.put("split_strategy", "per-stock-time-order-with-purge-gap");
        metadata.put("stock_codes", codes);
        metadata.put("source_days_per_stock", fetchDays);

        TrainingSnapshotManifest manifest = TrainingSnapshotManifest.builder()
                .datasetId(snapshotDir.getFileName().toString())
                .datasetType("lstm-panel")
                .featureVersion(processedData.getFeatureVersion())
                .labelVersion(LSTM_LABEL_VERSION)
                .createdAt(createdAt)
                .dataCutoff(dataCutoff)
                .schemaVersion(SCHEMA_VERSION)
                .files(files)
                .metadata(metadata)
                .build();
        writePrettyJson(snapshotDir.resolve("manifest.json"), manifest);
        log.info("LSTM 训练快照已导出: datasetId={}, trainSamples={}, validationSamples={}, dataCutoff={}",
                manifest.getDatasetId(), trainCount, validationCount, dataCutoff);
        return snapshotDir;
    }

    /**
     * 导出情感文本训练快照，并按发布时间顺序划分训练集和验证集。
     *
     * @param maxSamples 最大样本数，-1 表示全部
     * @param autoLabel 是否使用当前规则自动标注
     * @param outputRoot 快照输出根目录
     * @return 新快照目录
     * @throws IOException 文件写入或摘要计算失败
     */
    public Path exportSentimentSnapshot(int maxSamples, boolean autoLabel, Path outputRoot)
            throws IOException {
        List<TrainingSample> samples = sentimentDataPreprocessor.loadTrainingData(maxSamples, autoLabel)
                .stream()
                .filter(sample -> sample.getPublishedAt() != null)
                .sorted(Comparator.comparing(TrainingSample::getPublishedAt)
                        .thenComparing(TrainingSample::getSampleId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (samples.isEmpty()) {
            throw new IllegalStateException("没有包含发布时间的情感训练样本");
        }

        int trainEnd = Math.max(1, Math.min(samples.size(),
                (int) Math.floor(samples.size() * sentimentModelConfig.getTrainRatio())));
        List<TrainingSample> trainSamples = samples.subList(0, trainEnd);
        List<TrainingSample> validationSamples = samples.subList(trainEnd, samples.size());
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        Path snapshotDir = createSnapshotDirectory(outputRoot, "sentiment-text", createdAt);
        Path schemaPath = snapshotDir.resolve("schema.json");
        writePrettyJson(schemaPath, buildSentimentSchema());
        Path trainPath = snapshotDir.resolve("train.jsonl");
        long trainCount = writeSentimentSamples(trainPath, "train", trainSamples);
        Path validationPath = snapshotDir.resolve("validation.jsonl");
        long validationCount = writeSentimentSamples(
                validationPath, "validation", validationSamples);
        OffsetDateTime dataCutoff = samples.get(samples.size() - 1).getPublishedAt()
                .atZone(BUSINESS_ZONE).toOffsetDateTime();
        validateDataCutoff(dataCutoff, createdAt);

        List<TrainingSnapshotManifest.FileEntry> files = List.of(
                fileEntry(snapshotDir, schemaPath, "application/json", "schema", 0),
                fileEntry(snapshotDir, trainPath, "application/x-ndjson", "train", trainCount),
                fileEntry(snapshotDir, validationPath, "application/x-ndjson",
                        "validation", validationCount));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schema_file", "schema.json");
        metadata.put("format", "jsonl");
        metadata.put("encoding", "utf-8");
        metadata.put("split_strategy", "global-publish-time-order");
        metadata.put("train_ratio", sentimentModelConfig.getTrainRatio());
        metadata.put("auto_label", autoLabel);
        metadata.put("label_mapping", Map.of("neutral", 0, "positive", 1, "negative", 2));

        TrainingSnapshotManifest manifest = TrainingSnapshotManifest.builder()
                .datasetId(snapshotDir.getFileName().toString())
                .datasetType("sentiment-text")
                .featureVersion(SENTIMENT_FEATURE_VERSION)
                .labelVersion(SENTIMENT_LABEL_VERSION)
                .createdAt(createdAt)
                .dataCutoff(dataCutoff)
                .schemaVersion(SCHEMA_VERSION)
                .files(files)
                .metadata(metadata)
                .build();
        writePrettyJson(snapshotDir.resolve("manifest.json"), manifest);
        log.info("情感训练快照已导出: datasetId={}, trainSamples={}, validationSamples={}, dataCutoff={}",
                manifest.getDatasetId(), trainCount, validationCount, dataCutoff);
        return snapshotDir;
    }

    private List<String> normalizeStockCodes(List<String> stockCodes) {
        if (stockCodes == null) {
            return List.of();
        }
        return stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<String, List<StockPrice>> loadPriceSeries(List<String> stockCodes, int days) {
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, days));
        Map<String, List<StockPrice>> result = new LinkedHashMap<>();
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

    private TrainingSnapshotManifest.DatasetSchema buildLstmSchema(
            LstmDataPreprocessor.ProcessedData processedData) {
        List<Integer> featureShape = List.of(
                processedData.getSequenceLength(), processedData.getFeatureCount());
        return TrainingSnapshotManifest.DatasetSchema.builder()
                .schemaVersion(SCHEMA_VERSION)
                .datasetType("lstm-panel")
                .columns(List.of(
                        column("split", "string", null),
                        column("sample_index", "int64", null),
                        column("stock_code", "string", null),
                        column("target_date", "date", null),
                        column("features", "float32_matrix", featureShape),
                        column("return_target", "float32", null),
                        column("direction_target", "float32", null),
                        column("downside_target", "float32", null)))
                .build();
    }

    private TrainingSnapshotManifest.DatasetSchema buildSentimentSchema() {
        return TrainingSnapshotManifest.DatasetSchema.builder()
                .schemaVersion(SCHEMA_VERSION)
                .datasetType("sentiment-text")
                .columns(List.of(
                        column("split", "string", null),
                        column("sample_index", "int64", null),
                        column("sample_id", "string", null),
                        column("published_at", "datetime", null),
                        column("stock_code", "string", null),
                        column("text", "string", null),
                        column("label", "int64", null)))
                .build();
    }

    private TrainingSnapshotManifest.ColumnDefinition column(
            String name, String dtype, List<Integer> shape) {
        return TrainingSnapshotManifest.ColumnDefinition.builder()
                .name(name)
                .dtype(dtype)
                .nullable(false)
                .shape(shape)
                .build();
    }

    private long writeLstmSamples(Path path, String split,
                                  List<LstmDataPreprocessor.TrainingSample> samples)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                path, StandardCharsets.UTF_8)) {
            long index = 0;
            for (LstmDataPreprocessor.TrainingSample sample : samples) {
                if (sample.getTargetDate() == null) {
                    throw new IllegalStateException("LSTM 训练样本缺少目标交易日");
                }
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("split", split);
                record.put("sample_index", index);
                record.put("stock_code", sample.getStockCode());
                record.put("target_date", sample.getTargetDate());
                record.put("features", sample.getInput());
                record.put("return_target", sample.getReturnTarget());
                record.put("direction_target", sample.getDirectionTarget());
                record.put("downside_target", sample.getDownsideTarget());
                writer.write(objectMapper.writeValueAsString(record));
                writer.newLine();
                index++;
            }
            return index;
        }
    }

    private long writeSentimentSamples(Path path, String split, List<TrainingSample> samples)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                path, StandardCharsets.UTF_8)) {
            long index = 0;
            for (TrainingSample sample : samples) {
                if (sample.getSampleId() == null || sample.getSampleId().isBlank()) {
                    throw new IllegalStateException("情感训练样本缺少可追溯标识");
                }
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("split", split);
                record.put("sample_index", index);
                record.put("sample_id", sample.getSampleId());
                record.put("published_at", sample.getPublishedAt().atZone(BUSINESS_ZONE).toOffsetDateTime());
                record.put("stock_code", sample.getSource());
                record.put("text", sample.getText());
                record.put("label", sample.getLabel());
                writer.write(objectMapper.writeValueAsString(record));
                writer.newLine();
                index++;
            }
            return index;
        }
    }

    private OffsetDateTime lstmDataCutoff(LstmDataPreprocessor.ProcessedData processedData) {
        return java.util.stream.Stream.concat(
                        processedData.getTrainSamples().stream(),
                        processedData.getValSamples().stream())
                .map(LstmDataPreprocessor.TrainingSample::getTargetDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElseThrow(() -> new IllegalStateException("LSTM 快照缺少数据截止日期"))
                .atStartOfDay(BUSINESS_ZONE)
                .toOffsetDateTime();
    }

    private Path createSnapshotDirectory(Path outputRoot, String datasetType,
                                         OffsetDateTime createdAt) throws IOException {
        if (outputRoot == null) {
            throw new IllegalArgumentException("快照输出目录不能为空");
        }
        Path normalizedRoot = outputRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        String datasetId = datasetType + "-" + DATASET_TIME_FORMAT.format(createdAt);
        Path snapshotDir = normalizedRoot.resolve(datasetId).normalize();
        if (!snapshotDir.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("快照目录超出输出根目录");
        }
        if (Files.exists(snapshotDir)) {
            throw new IllegalStateException("快照目录已存在，禁止覆盖: " + snapshotDir);
        }
        Files.createDirectory(snapshotDir);
        return snapshotDir;
    }

    private void validateDataCutoff(OffsetDateTime dataCutoff, OffsetDateTime createdAt) {
        if (dataCutoff.isAfter(createdAt)) {
            throw new IllegalStateException("训练快照包含晚于导出时间的数据: " + dataCutoff);
        }
    }

    private TrainingSnapshotManifest.FileEntry fileEntry(
            Path root, Path file, String contentType, String split, long recordCount)
            throws IOException {
        return TrainingSnapshotManifest.FileEntry.builder()
                .path(root.relativize(file).toString().replace('\\', '/'))
                .sha256(sha256(file))
                .sizeBytes(Files.size(file))
                .contentType(contentType)
                .split(split)
                .recordCount(recordCount)
                .build();
    }

    private void writePrettyJson(Path path, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
// AI_GENERATE_END ----
