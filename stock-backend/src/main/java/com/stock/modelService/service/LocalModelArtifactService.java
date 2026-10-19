// AI_GENERATE_START --
package com.stock.modelService.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.modelService.config.SentimentTrainingConfig;
import com.stock.strategyAgent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 管理 Embedding 和 Sentiment 的固定本地模型制品目录。
 * 模型目录只能由初始化流程创建或替换，正式目录通过 manifest、文件大小、摘要和原子移动保证完整性。
 *
 * @author mwangli
 * @since 2026-10-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalModelArtifactService {

    private static final String EMBEDDING_SCHEMA = "embedding-artifact-v1";
    private static final String SENTIMENT_SCHEMA = "sentiment-artifact-v1";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper objectMapper;
    private final DjlModelConverterService converterService;

    /**
     * 确保 Embedding 固定版本目录存在且可被加载。
     *
     * @param properties Embedding 配置
     * @return 已通过完整性校验的版本目录
     * @throws IOException 目录校验、下载或写入失败
     * @throws InterruptedException 远程下载被中断
     */
    public synchronized Path ensureEmbeddingReady(AgentProperties.EmbeddingProperties properties)
            throws IOException, InterruptedException {
        return ensureReady(properties.getModel(), properties.getModelVersion(), properties.getModelPath(),
                properties.getPretrainedModel(), properties.getPretrainedRevision(), EMBEDDING_SCHEMA, false,
                properties.isDownloadPretrained(), null);
    }

    /**
     * 确保 Sentiment 固定版本目录存在且可被加载。
     *
     * @param config Sentiment 配置
     * @return 已通过完整性校验的版本目录
     * @throws IOException 目录校验、下载或写入失败
     * @throws InterruptedException 远程下载被中断
     */
    public synchronized Path ensureSentimentReady(SentimentTrainingConfig config)
            throws IOException, InterruptedException {
        return ensureReady(config.getModelName(), config.getModelVersion(), config.getModelPath(),
                config.getPretrainedModel(), config.getPretrainedRevision(), SENTIMENT_SCHEMA, true,
                config.isDownloadPretrained(), config);
    }

    private Path ensureReady(String modelName, String modelVersion, String configuredRoot,
                             String repository, String revision, String schema,
                             boolean sentimentModel, boolean allowDownload,
                             SentimentTrainingConfig sentimentConfig)
            throws IOException, InterruptedException {
        Path root = resolveRoot(configuredRoot);
        Path versionDirectory = root.resolve(modelVersion).normalize();
        if (!versionDirectory.startsWith(root)) {
            throw new IOException("模型版本目录越界: " + modelVersion);
        }
        if (isValid(versionDirectory, modelName, modelVersion, schema, sentimentModel)) {
            return versionDirectory;
        }
        if (!allowDownload) {
            throw new IOException("固定本地模型目录不可用且未允许自动初始化: " + versionDirectory);
        }

        deleteDirectory(versionDirectory);
        Files.createDirectories(root);
        Path stagingDirectory = root.resolve(modelVersion + ".initializing-" + UUID.randomUUID()).normalize();
        try {
            Files.createDirectories(stagingDirectory);
            downloadRepository(repository, revision, stagingDirectory);
            if (sentimentModel && sentimentConfig != null && sentimentConfig.isConverterEnabled()) {
                converterService.convertSentiment(stagingDirectory, sentimentConfig);
            }
            normalizeArtifact(stagingDirectory, modelName, modelVersion, repository, revision,
                    schema, sentimentModel);
            if (sentimentModel && sentimentConfig != null) {
                converterService.validateSentimentLoad(stagingDirectory, sentimentConfig);
            }
            if (!isValid(stagingDirectory, modelName, modelVersion, schema, sentimentModel)) {
                throw new IOException("模型初始化后的 manifest 或文件校验失败: " + stagingDirectory);
            }
            Files.move(stagingDirectory, versionDirectory, StandardCopyOption.ATOMIC_MOVE);
            log.info("本地模型制品初始化完成: model={}, version={}, directory={}",
                    modelName, modelVersion, versionDirectory);
            return versionDirectory;
        } finally {
            deleteDirectory(stagingDirectory);
        }
    }

    private boolean isValid(Path directory, String modelName, String modelVersion,
                            String schema, boolean sentimentModel) {
        try {
            Path manifestPath = directory.resolve(MANIFEST_FILE);
            if (!Files.isDirectory(directory) || !Files.isRegularFile(manifestPath)) {
                return false;
            }
            JsonNode manifest;
            try (var reader = Files.newBufferedReader(manifestPath)) {
                manifest = objectMapper.readTree(reader);
            }
            if (!schema.equals(manifest.path("schemaVersion").asText())
                    || !modelName.equals(manifest.path("modelName").asText())
                    || !modelVersion.equals(manifest.path("modelVersion").asText())) {
                return false;
            }
            if (sentimentModel && !"PyTorch".equals(manifest.path("engineName").asText())) {
                return false;
            }
            Set<String> listed = new HashSet<>();
            boolean hasWeights = false;
            for (JsonNode file : manifest.path("files")) {
                String relative = file.path("path").asText("").replace('\\', '/');
                Path target = directory.resolve(relative).normalize();
                if (relative.isBlank() || !listed.add(relative) || !target.startsWith(directory)
                        || !Files.isRegularFile(target)
                        || Files.size(target) != file.path("size").asLong(-1)
                        || !file.path("sha256").asText().equalsIgnoreCase(sha256(target))) {
                    return false;
                }
                hasWeights = hasWeights || isWeightFile(relative);
            }
            return listed.contains("config.json") && listed.contains("tokenizer.json")
                    && (!sentimentModel || listed.contains("serving.properties")) && hasWeights;
        } catch (Exception exception) {
            log.info("本地模型制品校验失败，将执行初始化: directory={}, reason={}", directory,
                    exception.getMessage());
            return false;
        }
    }

    private void normalizeArtifact(Path directory, String modelName, String modelVersion,
                                   String repository, String revision, String schema,
                                   boolean sentimentModel) throws IOException {
        if (sentimentModel && !Files.isRegularFile(directory.resolve("serving.properties"))) {
            Files.writeString(directory.resolve("serving.properties"),
                    "engine=PyTorch\noption.tensor_parallel_degree=1\n");
        }
        ArrayNode files = objectMapper.createArrayNode();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (MANIFEST_FILE.equals(file.getFileName().toString())) {
                    continue;
                }
                ObjectNode entry = objectMapper.createObjectNode();
                entry.put("path", directory.relativize(file).toString().replace('\\', '/'));
                entry.put("size", Files.size(file));
                entry.put("sha256", sha256(file));
                files.add(entry);
            }
        }
        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("schemaVersion", schema);
        manifest.put("modelName", modelName);
        manifest.put("modelVersion", modelVersion);
        manifest.put("sourceRepository", repository);
        manifest.put("sourceRevision", revision);
        manifest.put("engineName", "PyTorch");
        manifest.put("maxSequenceLength", sentimentModel ? 128 : 512);
        if (sentimentModel) {
            manifest.set("labels", objectMapper.valueToTree(
                    java.util.List.of("neutral", "positive", "negative")));
        }
        manifest.set("files", files);
        Files.writeString(directory.resolve(MANIFEST_FILE),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
    }

    private void downloadRepository(String repository, String revision, Path directory)
            throws IOException, InterruptedException {
        if (repository == null || repository.isBlank() || revision == null || revision.isBlank()) {
            throw new IOException("模型仓库或固定 revision 未配置");
        }
        URI api = URI.create("https://huggingface.co/api/models/" + repository
                + "?revision=" + revision);
        HttpRequest request = HttpRequest.newBuilder(api).timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("模型托管平台清单请求失败: status=" + response.statusCode());
        }
        for (JsonNode sibling : objectMapper.readTree(response.body()).path("siblings")) {
            String relative = sibling.path("rfilename").asText("");
            if (relative.isBlank() || relative.contains("..")) {
                continue;
            }
            Path target = directory.resolve(relative).normalize();
            if (!target.startsWith(directory)) {
                throw new IOException("远程模型文件路径越界: " + relative);
            }
            Files.createDirectories(target.getParent());
            URI fileUri = URI.create("https://huggingface.co/" + repository
                    + "/resolve/" + revision + "/" + relative);
            HttpRequest fileRequest = HttpRequest.newBuilder(fileUri)
                    .timeout(Duration.ofMinutes(15)).GET().build();
            HttpResponse<Path> fileResponse = HTTP_CLIENT.send(fileRequest,
                    HttpResponse.BodyHandlers.ofFile(target));
            if (fileResponse.statusCode() / 100 != 2) {
                throw new IOException("模型文件下载失败: " + relative + ", status=" + fileResponse.statusCode());
            }
        }
    }

    private Path resolveRoot(String configuredRoot) {
        Path configured = Path.of(configuredRoot == null || configuredRoot.isBlank()
                ? "data/models" : configuredRoot.trim());
        return configured.isAbsolute()
                ? configured.normalize()
                : Path.of("").toAbsolutePath().normalize().resolve(configured).normalize();
    }

    private boolean isWeightFile(String path) {
        String normalized = path.toLowerCase();
        return normalized.endsWith(".bin") || normalized.endsWith(".pt")
                || normalized.endsWith(".safetensors");
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                int length;
                while ((length = input.read(buffer)) >= 0) {
                    if (length > 0) {
                        digest.update(buffer, 0, length);
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
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
                    log.warn("本地模型制品目录清理失败: path={}, reason={}", path, exception.getMessage());
                }
            });
        } catch (IOException exception) {
            log.warn("本地模型制品目录遍历失败: path={}, reason={}", directory, exception.getMessage());
        }
    }
}
// AI_GENERATE_END --
