// AI_GENERATE_START------------
package com.stock.model.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.stock.model.entity.TrainingDatasetSnapshotDocument;
import com.stock.model.dto.ModelFeatureMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 训练数据快照工件存储组件。
 *
 * 负责分片文件的临时写入、GridFS 持久化、摘要校验、安全读取和临时目录清理。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TrainingSnapshotArtifactStore {

    static final String FORMAT = "CHUNKED_JSON_GZIP_V4";
    static final String STORAGE_GRIDFS = "MONGO_GRIDFS";
    static final String STORAGE_TEMPORARY = "LOCAL_TEMPORARY";

    private static final String ARTIFACT_TYPE = "LSTM_TRAINING_SNAPSHOT";

    private final GridFsTemplate gridFsTemplate;
    private final ObjectMapper objectMapper;

    Path prepareSnapshotDirectory(String snapshotId) throws IOException {
        Path directory = snapshotDirectory(snapshotId);
        Files.createDirectories(directory);
        return directory;
    }

    ChunkDescriptor writeChunk(Path snapshotDirectory, int chunkNumber, ChunkPayload chunk) throws IOException {
        String fileName = "chunk-" + String.format("%05d", chunkNumber) + ".json.gz";
        WrittenFile file = writeGzipJson(resolveChunkPath(snapshotDirectory, fileName), chunk);
        int sampleCount = chunk.getSamples() == null ? 0 : chunk.getSamples().size();
        log.info("LSTM 训练数据快照分片写入完成: chunk={}, samples={}, bytes={}",
                fileName, sampleCount, file.size());
        return ChunkDescriptor.builder()
                .fileName(fileName)
                .sha256(file.sha256())
                .size(file.size())
                .sampleCount(sampleCount)
                .build();
    }

    StoredArtifact persist(String snapshotId, Path snapshotDirectory,
                           ManifestMetadata metadata, List<ChunkDescriptor> chunks) throws IOException {
        SnapshotManifest manifest = SnapshotManifest.builder()
                .formatVersion(FORMAT)
                .featureCount(metadata.featureCount())
                .sequenceLength(metadata.sequenceLength())
                .targetReturnScale(metadata.targetReturnScale())
                .featureVersion(metadata.featureVersion())
                .featureMetadata(metadata.featureMetadata())
                .sampleSetHash(metadata.sampleSetHash())
                .totalSamples(metadata.totalSamples())
                .sampleLimitPerStock(metadata.sampleLimitPerStock())
                .trainedStocks(metadata.trainedStocks())
                .trainSamples(metadata.trainSamples())
                .valSamples(metadata.valSamples())
                .testSamples(metadata.testSamples())
                .datasetEndDate(metadata.datasetEndDate())
                .stockDictionary(metadata.stockDictionary())
                .industryDictionary(metadata.industryDictionary())
                .marketDictionary(metadata.marketDictionary())
                .sampleCounts(metadata.sampleCounts())
                .trainStartDate(metadata.trainStartDate())
                .trainEndDate(metadata.trainEndDate())
                .validationStartDate(metadata.validationStartDate())
                .validationEndDate(metadata.validationEndDate())
                .testStartDate(metadata.testStartDate())
                .testEndDate(metadata.testEndDate())
                .walkForwardFolds(boundaryOnlyFolds(metadata.walkForwardFolds()))
                .strictIsolationFolds(boundaryOnlyFolds(metadata.strictIsolationFolds()))
                .chunks(chunks)
                .build();
        Path manifestPath = snapshotDirectory.resolve("manifest.json");
        WrittenFile manifestFile = writeJson(manifestPath, manifest);
        long artifactSize = manifestFile.size() + chunks.stream().mapToLong(ChunkDescriptor::getSize).sum();

        deleteGridFsSnapshot(snapshotId);
        try {
            for (ChunkDescriptor descriptor : chunks) {
                Path chunkPath = resolveChunkPath(snapshotDirectory, descriptor.getFileName());
                storeGridFsFile(snapshotId, descriptor.getFileName(), "application/gzip", chunkPath);
            }
            ObjectId manifestId = storeGridFsFile(snapshotId, manifestPath.getFileName().toString(),
                    "application/json", manifestPath);
            return new StoredArtifact(manifestId.toHexString(), manifestFile.sha256(), artifactSize);
        } catch (RuntimeException | IOException exception) {
            deleteGridFsSnapshot(snapshotId);
            throw exception;
        }
    }

    LstmDataPreprocessor.ProcessedData load(TrainingDatasetSnapshotDocument snapshot) {
        if (snapshot == null || !"READY".equals(snapshot.getStatus()) || !isReadable(snapshot)) {
            throw new IllegalStateException("训练数据快照不存在、未完成或工件不可读");
        }
        if (!FORMAT.equals(snapshot.getArtifactFormat())) {
            throw new IllegalStateException("不支持的训练快照格式: " + snapshot.getArtifactFormat());
        }
        GridFSFile manifestFile = findGridFsFileById(snapshot.getArtifactFileId());
        if (manifestFile == null) {
            throw new IllegalStateException("训练数据快照 GridFS 清单不存在: " + snapshot.getId());
        }
        try {
            byte[] manifestBytes;
            GridFsResource manifestResource = gridFsTemplate.getResource(manifestFile);
            try (InputStream input = manifestResource.getInputStream()) {
                manifestBytes = input.readAllBytes();
            }
            if (!snapshot.getArtifactSha256().equalsIgnoreCase(sha256(manifestBytes))) {
                throw new IllegalStateException("训练数据快照 GridFS 清单摘要不一致");
            }
            SnapshotManifest manifest = objectMapper.readValue(manifestBytes, SnapshotManifest.class);
            validateManifest(snapshot, manifest);
            return readChunks(snapshot.getId(), manifest);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 MongoDB GridFS 训练快照失败", exception);
        }
    }

    boolean isReadable(TrainingDatasetSnapshotDocument snapshot) {
        return snapshot != null
                && STORAGE_GRIDFS.equals(snapshot.getArtifactStorage())
                && FORMAT.equals(snapshot.getArtifactFormat())
                && snapshot.getArtifactSha256() != null
                && findGridFsFileById(snapshot.getArtifactFileId()) != null;
    }

    private void validateManifest(
            TrainingDatasetSnapshotDocument snapshot, SnapshotManifest manifest) {
        if (manifest == null || !FORMAT.equals(manifest.getFormatVersion())) {
            throw new IllegalStateException("训练数据快照清单格式不受支持");
        }
        if (manifest.getFeatureMetadata() == null) {
            throw new IllegalStateException("训练数据快照清单缺少特征元数据");
        }
        manifest.getFeatureMetadata().validate();
        if (manifest.getTotalSamples() <= 0
                || manifest.getTrainSamples() != snapshot.getTrainSamples()
                || manifest.getValSamples() != snapshot.getValSamples()
                || manifest.getTestSamples() != snapshot.getTestSamples()
                || manifest.getTrainedStocks() != snapshot.getTrainedStocks()
                || safeList(manifest.getWalkForwardFolds()).size() != snapshot.getWalkForwardFoldCount()
                || safeList(manifest.getStrictIsolationFolds()).size()
                != snapshot.getStrictIsolationFoldCount()
                || manifest.getChunks() == null
                || manifest.getChunks().size() != snapshot.getChunkCount()) {
            throw new IllegalStateException("训练数据快照清单与元数据数量不一致");
        }
    }

    void deleteArtifacts(String snapshotId) {
        deleteGridFsSnapshot(snapshotId);
        cleanupTemporaryDirectory(snapshotId);
    }

    void cleanupTemporaryDirectory(String snapshotId) {
        Path root = artifactRoot();
        Path directory = snapshotDirectory(snapshotId);
        if (directory.equals(root) || !Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("清理训练快照临时文件失败: path={}, reason={}", path, exception.getMessage());
                }
            });
        } catch (IOException exception) {
            log.warn("清理训练快照临时目录失败: path={}, reason={}", directory, exception.getMessage());
        }
    }

    private LstmDataPreprocessor.ProcessedData readChunks(String snapshotId, SnapshotManifest manifest)
            throws IOException {
        if (!FORMAT.equals(manifest.getFormatVersion())) {
            throw new IllegalStateException("不支持的训练快照格式: " + manifest.getFormatVersion());
        }
        if (manifest.getChunks() == null || manifest.getChunks().isEmpty()) {
            throw new IllegalStateException("训练数据快照清单缺少样本分片");
        }
        List<LstmDataPreprocessor.TrainingSample> allSamples = new ArrayList<>(manifest.getTotalSamples());
        for (ChunkDescriptor descriptor : manifest.getChunks()) {
            GridFSFile chunkFile = findGridFsSnapshotFile(snapshotId, descriptor.getFileName());
            if (chunkFile == null || chunkFile.getLength() != descriptor.getSize()) {
                throw new IllegalStateException("训练数据快照 GridFS 分片不存在或大小不一致: "
                        + descriptor.getFileName());
            }
            ChunkPayload chunk;
            MessageDigest digest = messageDigest();
            try (InputStream source = gridFsTemplate.getResource(chunkFile).getInputStream();
                 DigestInputStream digestInput = new DigestInputStream(source, digest);
                 InputStream gzip = new GZIPInputStream(digestInput)) {
                try (JsonParser parser = objectMapper.getFactory().createParser(gzip)) {
                    parser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
                    chunk = objectMapper.readValue(parser, ChunkPayload.class);
                }
                gzip.transferTo(OutputStream.nullOutputStream());
            }
            if (!descriptor.getSha256().equalsIgnoreCase(hexDigest(digest))) {
                throw new IllegalStateException("训练数据快照 GridFS 分片摘要不一致: "
                        + descriptor.getFileName());
            }
            List<LstmDataPreprocessor.TrainingSample> chunkSamples = safeList(chunk.getSamples());
            if (chunkSamples.size() != descriptor.getSampleCount()) {
                throw new IllegalStateException("训练数据快照分片样本数量不一致: "
                        + descriptor.getFileName());
            }
            allSamples.addAll(chunkSamples);
        }
        if (allSamples.size() != manifest.getTotalSamples()) {
            throw new IllegalStateException("训练数据快照 GridFS 分片汇总样本数量不一致");
        }
        Map<String, Integer> actualSampleCounts = calculateSampleCounts(allSamples);
        if (!actualSampleCounts.equals(manifest.getSampleCounts())
                || actualSampleCounts.size() != manifest.getTrainedStocks()) {
            throw new IllegalStateException("训练数据快照股票样本统计不一致");
        }
        String actualSampleSetHash = sampleSetHash(allSamples);
        if (!actualSampleSetHash.equalsIgnoreCase(manifest.getSampleSetHash())) {
            throw new IllegalStateException("训练数据快照样本集合摘要不一致");
        }
        return LstmDataPreprocessor.ProcessedData.builder()
                .allSamples(allSamples)
                .sampleCounts(actualSampleCounts)
                .featureCount(manifest.getFeatureCount())
                .sequenceLength(manifest.getSequenceLength())
                .targetReturnScale(manifest.getTargetReturnScale())
                .featureVersion(manifest.getFeatureVersion())
                .featureMetadata(manifest.getFeatureMetadata())
                .sampleSetHash(manifest.getSampleSetHash())
                .stockDictionary(manifest.getStockDictionary())
                .industryDictionary(manifest.getIndustryDictionary())
                .marketDictionary(manifest.getMarketDictionary())
                .trainStartDate(manifest.getTrainStartDate())
                .trainEndDate(manifest.getTrainEndDate())
                .validationStartDate(manifest.getValidationStartDate())
                .validationEndDate(manifest.getValidationEndDate())
                .testStartDate(manifest.getTestStartDate())
                .testEndDate(manifest.getTestEndDate())
                .walkForwardFolds(safeList(manifest.getWalkForwardFolds()))
                .strictIsolationFolds(safeList(manifest.getStrictIsolationFolds()))
                .build();
    }

    private Map<String, Integer> calculateSampleCounts(
            List<LstmDataPreprocessor.TrainingSample> samples) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (LstmDataPreprocessor.TrainingSample sample : samples) {
            if (sample.getStockCode() == null || sample.getStockCode().isBlank()) {
                throw new IllegalStateException("训练数据快照包含无股票代码样本");
            }
            if (sample.getTargetDate() == null) {
                throw new IllegalStateException("训练数据快照包含无目标交易日样本");
            }
            result.merge(sample.getStockCode(), 1, Integer::sum);
        }
        return result;
    }

    private String sampleSetHash(List<LstmDataPreprocessor.TrainingSample> samples) {
        List<String> keys = samples.stream()
                .map(sample -> sample.getStockCode() + "|" + sample.getTargetDate())
                .sorted()
                .toList();
        long distinctKeys = keys.stream().distinct().count();
        if (keys.isEmpty() || distinctKeys != keys.size()) {
            throw new IllegalStateException("训练数据快照包含空样本集合或重复样本键");
        }
        return sha256(String.join("\n", keys).getBytes(StandardCharsets.UTF_8));
    }

    private List<LstmDataPreprocessor.WalkForwardFold> boundaryOnlyFolds(
            List<LstmDataPreprocessor.WalkForwardFold> folds) {
        return safeList(folds).stream()
                .map(fold -> LstmDataPreprocessor.WalkForwardFold.builder()
                        .foldNumber(fold.getFoldNumber())
                        .strictIsolation(fold.isStrictIsolation())
                        .trainStartDate(fold.getTrainStartDate())
                        .trainEndDate(fold.getTrainEndDate())
                        .validationStartDate(fold.getValidationStartDate())
                        .validationEndDate(fold.getValidationEndDate())
                        .testStartDate(fold.getTestStartDate())
                        .testEndDate(fold.getTestEndDate())
                        .build())
                .toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private ObjectId storeGridFsFile(String snapshotId, String fileName, String contentType, Path path)
            throws IOException {
        Document metadata = new Document("snapshotId", snapshotId)
                .append("artifactType", ARTIFACT_TYPE)
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

    private void deleteGridFsSnapshot(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return;
        }
        gridFsTemplate.delete(Query.query(Criteria.where("metadata.snapshotId").is(snapshotId)));
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

    private Path resolveChunkPath(Path snapshotDirectory, String fileName) {
        Path normalizedDirectory = snapshotDirectory.toAbsolutePath().normalize();
        Path chunkPath = normalizedDirectory.resolve(fileName).normalize();
        if (!chunkPath.startsWith(normalizedDirectory)) {
            throw new IllegalStateException("训练数据快照分片路径越界: " + fileName);
        }
        return chunkPath;
    }

    private Path snapshotDirectory(String snapshotId) {
        Path root = artifactRoot();
        Path directory = root.resolve("snapshot-" + snapshotId).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalStateException("训练数据快照目录越界");
        }
        return directory;
    }

    private Path artifactRoot() {
        return Path.of(System.getProperty("java.io.tmpdir"), "stock-trading-lstm-snapshots")
                .toAbsolutePath().normalize();
    }

    private String sha256(byte[] content) {
        return java.util.HexFormat.of().formatHex(messageDigest().digest(content));
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new IllegalStateException("创建 SHA-256 摘要失败", exception);
        }
    }

    private String hexDigest(MessageDigest digest) {
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    record ManifestMetadata(
            int featureCount,
            int sequenceLength,
            double targetReturnScale,
            String featureVersion,
            ModelFeatureMetadata featureMetadata,
            String sampleSetHash,
            int totalSamples,
            int sampleLimitPerStock,
            int trainedStocks,
            int trainSamples,
            int valSamples,
            int testSamples,
            LocalDate datasetEndDate,
            Map<String, Integer> stockDictionary,
            Map<String, Integer> industryDictionary,
            Map<String, Integer> marketDictionary,
            Map<String, Integer> sampleCounts,
            LocalDate trainStartDate,
            LocalDate trainEndDate,
            LocalDate validationStartDate,
            LocalDate validationEndDate,
            LocalDate testStartDate,
            LocalDate testEndDate,
            List<LstmDataPreprocessor.WalkForwardFold> walkForwardFolds,
            List<LstmDataPreprocessor.WalkForwardFold> strictIsolationFolds) {
    }

    record StoredArtifact(String manifestFileId, String manifestSha256, long artifactSize) {
    }

    private record WrittenFile(long size, String sha256) {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ChunkPayload {
        /** 本分片包含的唯一原始训练样本。 */
        private List<LstmDataPreprocessor.TrainingSample> samples;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ChunkDescriptor {
        /** 分片文件名。 */
        private String fileName;
        /** 分片 SHA-256。 */
        private String sha256;
        /** 分片大小，单位字节。 */
        private long size;
        /** 分片原始样本数量。 */
        private int sampleCount;
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
        /** 模型不可变特征元数据。 */
        private ModelFeatureMetadata featureMetadata;
        /** 排序后样本键集合的 SHA-256。 */
        private String sampleSetHash;
        /** 唯一原始样本总数。 */
        private int totalSamples;
        /** 单只股票样本上限。 */
        private int sampleLimitPerStock;
        /** 有效训练股票数量。 */
        private int trainedStocks;
        /** 训练样本数量。 */
        private int trainSamples;
        /** 验证样本数量。 */
        private int valSamples;
        /** 独立测试样本数量。 */
        private int testSamples;
        /** 数据截止日期。 */
        private LocalDate datasetEndDate;
        /** 股票稳定类别字典。 */
        private Map<String, Integer> stockDictionary;
        /** 行业稳定类别字典。 */
        private Map<String, Integer> industryDictionary;
        /** 市场稳定类别字典。 */
        private Map<String, Integer> marketDictionary;
        /** 各股票原始样本数量。 */
        private Map<String, Integer> sampleCounts;
        /** 训练集开始日期。 */
        private LocalDate trainStartDate;
        /** 训练集结束日期。 */
        private LocalDate trainEndDate;
        /** 验证集开始日期。 */
        private LocalDate validationStartDate;
        /** 验证集结束日期。 */
        private LocalDate validationEndDate;
        /** 独立测试集开始日期。 */
        private LocalDate testStartDate;
        /** 独立测试集结束日期。 */
        private LocalDate testEndDate;
        /** 主策略 Walk-Forward 日期边界。 */
        private List<LstmDataPreprocessor.WalkForwardFold> walkForwardFolds;
        /** 60 日严格隔离敏感性日期边界。 */
        private List<LstmDataPreprocessor.WalkForwardFold> strictIsolationFolds;
        /** 分片描述列表。 */
        private List<ChunkDescriptor> chunks;
    }
}
// AI_GENERATE_END------------
