// AI_GENERATE_START--------------
package com.stock.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.model.config.SentimentTrainingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * 校验 Embedding 和 Sentiment 的固定本地模型制品目录。
 * 模型制品由发布流程预先准备完成，运行时只解析、校验和返回只读目录。
 *
 * @author mwangli
 * @since 2026-10-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalModelArtifactService {

    private static final String EMBEDDING_SCHEMA = "embedding-artifact-v1";
    private static final String SENTIMENT_SCHEMA = "sentiment-artifact-v1";
    private static final String MANIFEST_FILE = "manifest.json";

    private final ObjectMapper objectMapper;

    /**
     * 校验并返回固定 Embedding 制品目录。
     *
     * @param properties Embedding 配置
     * @return 已通过 manifest、文件大小和 SHA-256 校验的版本目录
     * @throws IOException 固定制品目录不存在或校验失败
     */
    public synchronized Path resolveEmbeddingArtifact(String modelName, String modelVersion, String modelPath)
            throws IOException {
        return resolveReady(modelName, modelVersion, modelPath, EMBEDDING_SCHEMA, false);
    }

    /**
     * 校验并返回固定 Sentiment 制品目录。
     *
     * @param config Sentiment 配置
     * @return 已通过 manifest、文件大小和 SHA-256 校验的版本目录
     * @throws IOException 固定制品目录不存在或校验失败
     */
    public synchronized Path resolveSentimentArtifact(SentimentTrainingConfig config)
            throws IOException {
        return resolveReady(config.getModelName(), config.getModelVersion(), config.getModelPath(),
                SENTIMENT_SCHEMA, true);
    }

    private Path resolveReady(String modelName, String modelVersion, String configuredRoot,
                              String schema, boolean sentimentModel) throws IOException {
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IOException("模型版本未配置: " + modelName);
        }
        Path root = resolveRoot(configuredRoot);
        Path versionDirectory = root.resolve(modelVersion.trim()).normalize();
        if (!versionDirectory.startsWith(root)) {
            throw new IOException("模型版本目录越界: " + modelVersion);
        }
        log.info("[固定制品][检查] model={}, version={}, directory={}",
                modelName, modelVersion, versionDirectory);
        if (!isValid(versionDirectory, modelName, modelVersion.trim(), schema, sentimentModel)) {
            throw new IOException("固定模型制品不存在或完整性校验失败: " + versionDirectory);
        }
        log.info("[固定制品][校验通过] model={}, version={}, directory={}",
                modelName, modelVersion, versionDirectory);
        return versionDirectory;
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
            boolean hasExpectedModel = false;
            String expectedModelFile = modelName + ".pt";
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
                hasExpectedModel = hasExpectedModel || expectedModelFile.equals(relative);
            }
            return listed.contains("config.json") && listed.contains("tokenizer.json")
                    && (!sentimentModel || listed.contains("serving.properties"))
                    && hasWeights && hasExpectedModel;
        } catch (Exception exception) {
            log.warn("固定模型制品校验失败: directory={}, reason={}", directory,
                    exception.getMessage());
            return false;
        }
    }

    private Path resolveRoot(String configuredRoot) {
        Path configured = Path.of(configuredRoot == null || configuredRoot.isBlank()
                ? "./model-files" : configuredRoot.trim());
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
}
// AI_GENERATE_END--------------
