// AI_GENERATE_START --
package com.stock.modelService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.domain.entity.TrainingDatasetSnapshotDocument;
import com.stock.modelService.persistence.TrainingDatasetSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * LSTM 训练数据快照服务。
 * 将行情查询、特征计算和样本切分结果保存为可校验的压缩工件，训练任务只读取 READY 快照。
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
    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final LstmDataPreprocessor dataPreprocessor;
    private final TrainingDatasetSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

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
                .filter(this::isArtifactReadable);
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
     * 从行情数据构建并保存一个新的训练数据快照。
     *
     * @param modelName 模型名称
     * @param stockCodes 股票代码
     * @param days 历史交易日数量
     * @return 已保存的快照
     */
    public TrainingDatasetSnapshotDocument buildSnapshot(String modelName, List<String> stockCodes, int days) {
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream().distinct().toList();
        TrainingDatasetSnapshotDocument document = new TrainingDatasetSnapshotDocument();
        document.setModelName(modelName);
        document.setStatus("BUILDING");
        document.setContractHash(contractHash(modelName, codes, days));
        document.setFeatureVersion(LstmDataPreprocessor.FEATURE_VERSION);
        document.setDays(days);
        document.setStockCount(codes.size());
        document.setCreatedAt(LocalDateTime.now());
        document = snapshotRepository.save(document);
        try {
            Map<String, List<StockPrice>> priceSeries = loadPriceSeries(codes, days);
            Map<String, LstmDataPreprocessor.StockContext> contexts = loadStockContexts(codes);
            LstmDataPreprocessor.ProcessedData processedData = dataPreprocessor.processPanelData(priceSeries, contexts);
            if (processedData == null || processedData.getTrainSamples().isEmpty()) {
                throw new IllegalStateException("训练快照没有可用样本");
            }
            return saveSnapshot(document, processedData);
        } catch (Exception exception) {
            document.setStatus("FAILED");
            document.setErrorMessage(exception.getMessage());
            document.setFinishedAt(LocalDateTime.now());
            snapshotRepository.save(document);
            throw new IllegalStateException("构建 LSTM 训练数据快照失败", exception);
        }
    }

    /**
     * 持久化已经完成预处理的训练数据。
     *
     * @param modelName 模型名称
     * @param stockCodes 股票代码
     * @param days 历史交易日数量
     * @param processedData 已完成预处理的数据
     * @return 已保存的快照
     */
    public TrainingDatasetSnapshotDocument saveSnapshot(String modelName, List<String> stockCodes, int days,
                                                        LstmDataPreprocessor.ProcessedData processedData) {
        TrainingDatasetSnapshotDocument document = new TrainingDatasetSnapshotDocument();
        document.setModelName(modelName);
        document.setStatus("BUILDING");
        document.setContractHash(contractHash(modelName, stockCodes, days));
        document.setFeatureVersion(LstmDataPreprocessor.FEATURE_VERSION);
        document.setDays(days);
        document.setStockCount(stockCodes.size());
        document.setCreatedAt(LocalDateTime.now());
        document = snapshotRepository.save(document);
        return saveSnapshot(document, processedData);
    }

    private TrainingDatasetSnapshotDocument saveSnapshot(
            TrainingDatasetSnapshotDocument document,
            LstmDataPreprocessor.ProcessedData processedData) {
        try {
            byte[] artifact = encode(processedData);
            Path directory = Path.of(config.getArtifactDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path artifactPath = directory.resolve("snapshot-" + document.getId() + ".json.gz");
            Files.write(artifactPath, artifact);
            document.setArtifactPath(artifactPath.toString());
            document.setArtifactSize(artifact.length);
            document.setArtifactSha256(sha256(artifact));
            document.setTrainedStocks(processedData.getSampleCounts().size());
            document.setTrainSamples(processedData.getTrainSamples().size());
            document.setValSamples(processedData.getValSamples().size());
            document.setDatasetEndDate(findDatasetEndDate(processedData));
            document.setStatus(READY);
            document.setFinishedAt(LocalDateTime.now());
            log.info("LSTM 训练数据快照构建完成: snapshotId={}, stocks={}, trainSamples={}, valSamples={}, bytes={}",
                    document.getId(), document.getTrainedStocks(), document.getTrainSamples(),
                    document.getValSamples(), document.getArtifactSize());
            return snapshotRepository.save(document);
        } catch (Exception exception) {
            document.setStatus("FAILED");
            document.setErrorMessage(exception.getMessage());
            document.setFinishedAt(LocalDateTime.now());
            snapshotRepository.save(document);
            throw new IllegalStateException("构建 LSTM 训练数据快照失败", exception);
        }
    }

    /**
     * 读取并校验快照工件。
     *
     * @param snapshot 快照元数据
     * @return 处理后的训练数据
     */
    public LstmDataPreprocessor.ProcessedData loadSnapshot(TrainingDatasetSnapshotDocument snapshot) {
        if (snapshot == null || !READY.equals(snapshot.getStatus()) || !isArtifactReadable(snapshot)) {
            throw new IllegalStateException("训练数据快照不存在、未完成或工件不可读");
        }
        try {
            byte[] artifact = Files.readAllBytes(Path.of(snapshot.getArtifactPath()));
            if (!snapshot.getArtifactSha256().equals(sha256(artifact))) {
                throw new IllegalStateException("训练数据快照校验摘要不一致");
            }
            return decode(artifact);
        } catch (IOException exception) {
            throw new IllegalStateException("读取训练数据快照失败", exception);
        }
    }

    private Map<String, List<StockPrice>> loadPriceSeries(List<String> stockCodes, int days) {
        Map<String, List<StockPrice>> result = new LinkedHashMap<>();
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, days));
        for (String stockCode : stockCodes) {
            List<StockPrice> prices = new ArrayList<>(priceRepository.findByCodeOrderByDateDesc(stockCode, pageRequest));
            if (!prices.isEmpty()) {
                Collections.reverse(prices);
                result.put(stockCode, prices);
            }
        }
        return result;
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

    private byte[] encode(LstmDataPreprocessor.ProcessedData data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            objectMapper.writeValue(gzip, data);
        }
        return output.toByteArray();
    }

    private LstmDataPreprocessor.ProcessedData decode(byte[] artifact) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(artifact))) {
            return objectMapper.readValue(gzip, LstmDataPreprocessor.ProcessedData.class);
        }
    }

    private boolean isArtifactReadable(TrainingDatasetSnapshotDocument document) {
        return document.getArtifactPath() != null && document.getArtifactSha256() != null
                && Files.isRegularFile(Path.of(document.getArtifactPath()));
    }

    private LocalDate findDatasetEndDate(LstmDataPreprocessor.ProcessedData data) {
        return java.util.stream.Stream.concat(data.getTrainSamples().stream(), data.getValSamples().stream())
                .map(LstmDataPreprocessor.TrainingSample::getTargetDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo).orElse(null);
    }

    private String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException("计算训练快照摘要失败", exception);
        }
    }
}
// AI_GENERATE_END --
