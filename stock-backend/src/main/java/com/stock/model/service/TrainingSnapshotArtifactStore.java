// AI_GENERATE_START----
package com.stock.model.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.stock.model.entity.TrainingDatasetSnapshotDocument;
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

    static final String FORMAT = "CHUNKED_JSON_GZIP_V2";
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
        log.info("LSTM 训练数据快照分片写入完成: chunk={}, stocks={}, trainSamples={}, valSamples={}, "
                        + "testSamples={}, bytes={}",
                fileName, chunk.getSampleCounts().size(), chunk.getTrainSamples().size(),
                chunk.getValSamples().size(), chunk.getTestSamples().size(), file.size());
        return ChunkDescriptor.builder()
                .fileName(fileName)
                .sha256(file.sha256())
                .size(file.size())
                .stockCount(chunk.getSampleCounts().size())
                .trainSamples(chunk.getTrainSamples().size())
                .valSamples(chunk.getValSamples().size())
                .testSamples(chunk.getTestSamples().size())
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
                .sampleLimitPerStock(metadata.sampleLimitPerStock())
                .trainedStocks(metadata.trainedStocks())
                .trainSamples(metadata.trainSamples())
                .valSamples(metadata.valSamples())
                .testSamples(metadata.testSamples())
                .datasetEndDate(metadata.datasetEndDate())
                .stockDictionary(metadata.stockDictionary())
                .industryDictionary(metadata.industryDictionary())
                .marketDictionary(metadata.marketDictionary())
                .featureMeans(metadata.featureMeans())
                .featureStdDeviations(metadata.featureStdDeviations())
                .trainStartDate(metadata.trainStartDate())
                .trainEndDate(metadata.trainEndDate())
                .validationStartDate(metadata.validationStartDate())
                .validationEndDate(metadata.validationEndDate())
                .testStartDate(metadata.testStartDate())
                .testEndDate(metadata.testEndDate())
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
            return readChunks(snapshot.getId(), manifest);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 MongoDB GridFS 训练快照失败", exception);
        }
    }

    boolean isReadable(TrainingDatasetSnapshotDocument snapshot) {
        return snapshot != null
                && STORAGE_GRIDFS.equals(snapshot.getArtifactStorage())
                && snapshot.getArtifactSha256() != null
                && findGridFsFileById(snapshot.getArtifactFileId()) != null;
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
        List<LstmDataPreprocessor.TrainingSample> trainSamples = new ArrayList<>(manifest.getTrainSamples());
        List<LstmDataPreprocessor.TrainingSample> valSamples = new ArrayList<>(manifest.getValSamples());
        List<LstmDataPreprocessor.TrainingSample> testSamples = new ArrayList<>(manifest.getTestSamples());
        Map<String, Integer> sampleCounts = new LinkedHashMap<>();
        List<LstmDataPreprocessor.WalkForwardFold> walkForwardFolds = new ArrayList<>();
        List<LstmDataPreprocessor.WalkForwardFold> strictIsolationFolds = new ArrayList<>();
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
            ChunkPayload chunk;
            try (InputStream source = gridFsTemplate.getResource(chunkFile).getInputStream();
                 InputStream gzip = new GZIPInputStream(source)) {
                chunk = objectMapper.readValue(gzip, ChunkPayload.class);
            }
            trainSamples.addAll(safeList(chunk.getTrainSamples()));
            valSamples.addAll(safeList(chunk.getValSamples()));
            testSamples.addAll(safeList(chunk.getTestSamples()));
            sampleCounts.putAll(chunk.getSampleCounts() == null ? Map.of() : chunk.getSampleCounts());
            walkForwardFolds.addAll(safeList(chunk.getWalkForwardFolds()));
            strictIsolationFolds.addAll(safeList(chunk.getStrictIsolationFolds()));
        }
        if (trainSamples.size() != manifest.getTrainSamples()
                || valSamples.size() != manifest.getValSamples()
                || testSamples.size() != manifest.getTestSamples()
                || sampleCounts.size() != manifest.getTrainedStocks()) {
            throw new IllegalStateException("训练数据快照 GridFS 分片汇总数量不一致");
        }
        return LstmDataPreprocessor.ProcessedData.builder()
                .trainSamples(trainSamples)
                .valSamples(valSamples)
                .testSamples(testSamples)
                .sampleCounts(sampleCounts)
                .featureCount(manifest.getFeatureCount())
                .sequenceLength(manifest.getSequenceLength())
                .targetReturnScale(manifest.getTargetReturnScale())
                .featureVersion(manifest.getFeatureVersion())
                .stockDictionary(manifest.getStockDictionary())
                .industryDictionary(manifest.getIndustryDictionary())
                .marketDictionary(manifest.getMarketDictionary())
                .featureMeans(manifest.getFeatureMeans())
                .featureStdDeviations(manifest.getFeatureStdDeviations())
                .trainStartDate(manifest.getTrainStartDate())
                .trainEndDate(manifest.getTrainEndDate())
                .validationStartDate(manifest.getValidationStartDate())
                .validationEndDate(manifest.getValidationEndDate())
                .testStartDate(manifest.getTestStartDate())
                .testEndDate(manifest.getTestEndDate())
                .walkForwardFolds(walkForwardFolds)
                .strictIsolationFolds(strictIsolationFolds)
                .build();
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
            int sampleLimitPerStock,
            int trainedStocks,
            int trainSamples,
            int valSamples,
            int testSamples,
            LocalDate datasetEndDate,
            Map<String, Integer> stockDictionary,
            Map<String, Integer> industryDictionary,
            Map<String, Integer> marketDictionary,
            double[] featureMeans,
            double[] featureStdDeviations,
            LocalDate trainStartDate,
            LocalDate trainEndDate,
            LocalDate validationStartDate,
            LocalDate validationEndDate,
            LocalDate testStartDate,
            LocalDate testEndDate) {
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
        /** 训练样本。 */
        private List<LstmDataPreprocessor.TrainingSample> trainSamples;
        /** 验证样本。 */
        private List<LstmDataPreprocessor.TrainingSample> valSamples;
        /** 独立测试样本。 */
        private List<LstmDataPreprocessor.TrainingSample> testSamples;
        /** 各股票样本数量。 */
        private Map<String, Integer> sampleCounts;
        /** 主策略 Walk-Forward 折叠。 */
        private List<LstmDataPreprocessor.WalkForwardFold> walkForwardFolds;
        /** 60 日严格隔离敏感性折叠。 */
        private List<LstmDataPreprocessor.WalkForwardFold> strictIsolationFolds;
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
        /** 分片有效股票数量。 */
        private int stockCount;
        /** 分片训练样本数量。 */
        private int trainSamples;
        /** 分片验证样本数量。 */
        private int valSamples;
        /** 分片独立测试样本数量。 */
        private int testSamples;
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
        /** 训练窗口连续特征均值。 */
        private double[] featureMeans;
        /** 训练窗口连续特征标准差。 */
        private double[] featureStdDeviations;
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
        /** 分片描述列表。 */
        private List<ChunkDescriptor> chunks;
    }
}
// AI_GENERATE_END----
