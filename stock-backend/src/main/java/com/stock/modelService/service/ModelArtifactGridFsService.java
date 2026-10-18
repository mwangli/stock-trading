// AI_GENERATE_START --
package com.stock.modelService.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.modelService.domain.entity.ModelMetadataDocument;
import com.stock.modelService.persistence.ModelMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 统一管理 Embedding、Sentiment 和其他大模型制品。
 * 制品文件使用 MongoDB GridFS 分片保存，模型加载时只在系统临时目录短暂还原，
 * DJL 完成加载后删除临时文件，避免依赖宿主机模型挂载目录。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelArtifactGridFsService {

    private static final String STORAGE = "MONGO_GRIDFS";
    private static final String ARTIFACT_TYPE = "MODEL_ARTIFACT";
    private final GridFsTemplate gridFsTemplate;
    private final ModelMetadataRepository metadataRepository;
    private final ObjectMapper objectMapper;

    /**
     * 判断指定模型版本是否已经存在完整的 GridFS 制品。
     *
     * @param modelName 模型逻辑名称
     * @param modelVersion 模型版本
     * @return 制品是否存在
     */
    public boolean exists(String modelName, String modelVersion) {
        return !findFiles(modelName, modelVersion).isEmpty();
    }

    /**
     * 从本地规范化目录上传一个不可变模型版本。
     *
     * @param modelName 模型逻辑名称
     * @param modelVersion 模型版本
     * @param sourceDirectory 模型目录
     * @throws IOException 读取或上传失败
     */
    public synchronized void uploadDirectory(String modelName, String modelVersion,
                                              Path sourceDirectory) throws IOException {
        Path root = sourceDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("模型制品目录不存在: " + root);
        }
        delete(modelName, modelVersion);
        List<String> fileIds = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                Document metadata = new Document("modelName", modelName)
                        .append("modelVersion", modelVersion)
                        .append("artifactType", ARTIFACT_TYPE)
                        .append("relativePath", relative)
                        .append("sha256", sha256(file));
                try (InputStream input = Files.newInputStream(file)) {
                    fileIds.add(gridFsTemplate.store(input, relative, contentType(file), metadata).toHexString());
                }
            }
        }
        if (fileIds.isEmpty()) {
            throw new IOException("模型制品目录为空: " + root);
        }
        verifyUploadedFiles(modelName, modelVersion, fileIds.size());
        ModelMetadataDocument metadata = metadataRepository.findByModelName(modelName)
                .orElseGet(ModelMetadataDocument::new);
        metadata.setModelName(modelName);
        metadata.setModelVersion(modelVersion);
        metadata.setArtifactStorage(STORAGE);
        metadata.setArtifactUri("gridfs://" + modelName + "/" + modelVersion);
        metadata.setArtifactFileIdsJson(objectMapper.writeValueAsString(fileIds));
        metadata.setStatus("READY");
        metadata.setUpdatedAt(java.time.LocalDateTime.now());
        metadataRepository.save(metadata);
    }

    /**
     * 从 Hugging Face 模型托管平台下载公开模型，并上传到 GridFS。
     * 下载只写入系统临时目录，上传完成后删除临时目录。
     *
     * @param modelName 模型逻辑名称
     * @param modelVersion 模型版本
     * @param repository Hugging Face 仓库，如 BAAI/bge-base-zh-v1.5
     * @param sentimentModel 是否生成情感模型 manifest
     * @throws IOException 下载、解析或上传失败
     * @throws InterruptedException 下载被中断
     */
    public void downloadAndUpload(String modelName, String modelVersion,
                                  String repository, boolean sentimentModel)
            throws IOException, InterruptedException {
        Path temporaryDirectory = Files.createTempDirectory("stock-model-bootstrap-");
        try {
            downloadRepository(repository, temporaryDirectory);
            if (sentimentModel && !Files.isRegularFile(temporaryDirectory.resolve("manifest.json"))) {
                writeSentimentManifest(temporaryDirectory, modelName, modelVersion, repository);
            }
            uploadDirectory(modelName, modelVersion, temporaryDirectory);
        } finally {
            deleteDirectory(temporaryDirectory);
        }
    }

    /**
     * 将 GridFS 中的模型版本还原为 DJL 可读取的临时目录。
     *
     * @param modelName 模型逻辑名称
     * @param modelVersion 模型版本
     * @return 临时模型目录，调用方负责删除
     * @throws IOException 读取或写入失败
     */
    public Path materialize(String modelName, String modelVersion) throws IOException {
        List<org.springframework.data.mongodb.gridfs.GridFSFile> files = findFiles(modelName, modelVersion);
        if (files.isEmpty()) {
            throw new IOException("MongoDB GridFS 中不存在模型制品: " + modelName + "/" + modelVersion);
        }
        Path directory = Files.createTempDirectory("stock-model-load-");
        try {
            for (org.springframework.data.mongodb.gridfs.GridFSFile file : files) {
                String relative = Objects.requireNonNull(file.getMetadata())
                        .getString("relativePath");
                Path target = directory.resolve(relative).normalize();
                if (!target.startsWith(directory)) {
                    throw new IOException("模型制品路径越界: " + relative);
                }
                Files.createDirectories(target.getParent());
                GridFsResource resource = gridFsTemplate.getResource(file);
                try (InputStream input = resource.getInputStream()) {
                    Files.copy(input, target);
                }
            }
            return directory;
        } catch (Exception exception) {
            deleteDirectory(directory);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("还原 MongoDB 模型制品失败", exception);
        }
    }

    /**
     * 删除指定模型版本的全部 GridFS 文件及元数据引用。
     *
     * @param modelName 模型逻辑名称
     * @param modelVersion 模型版本
     */
    public synchronized void delete(String modelName, String modelVersion) {
        gridFsTemplate.delete(Query.query(new Criteria().andOperator(
                Criteria.where("metadata.modelName").is(modelName),
                Criteria.where("metadata.modelVersion").is(modelVersion),
                Criteria.where("metadata.artifactType").is(ARTIFACT_TYPE))));
    }

    private List<org.springframework.data.mongodb.gridfs.GridFSFile> findFiles(
            String modelName, String modelVersion) {
        return gridFsTemplate.find(Query.query(new Criteria().andOperator(
                        Criteria.where("metadata.modelName").is(modelName),
                        Criteria.where("metadata.modelVersion").is(modelVersion),
                        Criteria.where("metadata.artifactType").is(ARTIFACT_TYPE))))
                .stream()
                .sorted(Comparator.comparing(file -> file.getMetadata().getString("relativePath")))
                .toList();
    }

    /**
     * 校验已存在的 GridFS 模型制品完整性。
     *
     * @param modelName 模型逻辑名称
     * @param modelVersion 模型版本
     * @throws IOException 文件缺失、元数据缺失或摘要不一致时抛出
     */
    public void verifyIntegrity(String modelName, String modelVersion) throws IOException {
        verifyUploadedFiles(modelName, modelVersion, null);
    }

    private void verifyUploadedFiles(String modelName, String modelVersion,
                                     Integer expectedFileCount) throws IOException {
        List<org.springframework.data.mongodb.gridfs.GridFSFile> files = findFiles(modelName, modelVersion);
        if (files.isEmpty()) {
            throw new IOException("GridFS 模型制品不存在: " + modelName + "/" + modelVersion);
        }
        if (expectedFileCount != null && files.size() != expectedFileCount) {
            delete(modelName, modelVersion);
            throw new IOException("GridFS 模型文件数量校验失败: expected="
                    + expectedFileCount + ", actual=" + files.size());
        }
        for (org.springframework.data.mongodb.gridfs.GridFSFile file : files) {
            Document metadata = file.getMetadata();
            String expectedSha256 = metadata == null ? null : metadata.getString("sha256");
            if (expectedSha256 == null || expectedSha256.isBlank()) {
                delete(modelName, modelVersion);
                throw new IOException("GridFS 模型文件缺少 SHA-256: " + file.getFilename());
            }
            GridFsResource resource = gridFsTemplate.getResource(file);
            try (InputStream input = resource.getInputStream()) {
                String actualSha256 = sha256(input);
                if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                    delete(modelName, modelVersion);
                    throw new IOException("GridFS 模型文件摘要校验失败: " + file.getFilename());
                }
            }
        }
    }

    private void downloadRepository(String repository, Path directory)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        URI api = URI.create("https://huggingface.co/api/models/" + repository);
        HttpRequest request = HttpRequest.newBuilder(api)
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("模型托管平台清单请求失败: status=" + response.statusCode());
        }
        JsonNode siblings = objectMapper.readTree(response.body()).path("siblings");
        for (JsonNode sibling : siblings) {
            String relative = sibling.path("rfilename").asText("");
            if (relative.isBlank() || relative.contains("..")) {
                continue;
            }
            Path target = directory.resolve(relative).normalize();
            if (!target.startsWith(directory)) {
                throw new IOException("远程模型文件路径越界: " + relative);
            }
            Files.createDirectories(target.getParent());
            URI fileUri = URI.create("https://huggingface.co/" + repository + "/resolve/main/" + relative);
            HttpRequest fileRequest = HttpRequest.newBuilder(fileUri)
                    .timeout(Duration.ofMinutes(10)).GET().build();
            HttpResponse<Path> fileResponse = client.send(fileRequest,
                    HttpResponse.BodyHandlers.ofFile(target));
            if (fileResponse.statusCode() / 100 != 2) {
                throw new IOException("模型文件下载失败: " + relative + ", status=" + fileResponse.statusCode());
            }
        }
    }

    private void writeSentimentManifest(Path directory, String modelName,
                                        String modelVersion, String repository) throws IOException {
        ArrayNode files = objectMapper.createArrayNode();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("path", directory.relativize(file).toString().replace('\\', '/'));
                entry.put("size", Files.size(file));
                entry.put("sha256", sha256(file));
                files.add(entry);
            }
        }
        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("schemaVersion", "sentiment-artifact-v1");
        manifest.put("modelName", modelName);
        manifest.put("modelVersion", modelVersion);
        manifest.put("baseModel", repository);
        manifest.put("engineName", "PyTorch");
        manifest.put("maxSequenceLength", 128);
        manifest.set("labels", objectMapper.valueToTree(List.of("neutral", "positive", "negative")));
        manifest.set("files", files);
        Files.writeString(directory.resolve("manifest.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
    }

    private String contentType(Path file) {
        try {
            return Files.probeContentType(file);
        } catch (IOException ignored) {
            return "application/octet-stream";
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                return sha256(input, digest);
            }
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private String sha256(InputStream input) throws IOException {
        try {
            return sha256(input, MessageDigest.getInstance("SHA-256"));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private String sha256(InputStream input, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            if (length > 0) {
                digest.update(buffer, 0, length);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    log.warn("模型临时目录清理失败: path={}, reason={}", path, exception.getMessage());
                }
            });
        } catch (IOException exception) {
            log.warn("模型临时目录遍历失败: path={}, reason={}", directory, exception.getMessage());
        }
    }
}
// AI_GENERATE_END --
