// AI_GENERATE_START ---
package com.stock.agent.knowledge.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 校验 Agent 固定 Embedding 模型制品目录。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingArtifactService {

    private static final String SCHEMA_VERSION = "embedding-artifact-v1";
    private static final String MANIFEST_FILE = "manifest.json";

    private final ObjectMapper objectMapper;

    /**
     * 校验并返回固定 Embedding 制品版本目录。
     *
     * @param modelName 模型名称
     * @param modelVersion 模型版本
     * @param configuredRoot 制品根目录
     * @return 已通过清单和 SHA-256 校验的目录
     * @throws IOException 制品不存在或完整性校验失败时抛出
     */
    public synchronized Path resolve(String modelName, String modelVersion, String configuredRoot)
            throws IOException {
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IOException("Embedding 模型版本未配置: " + modelName);
        }
        Path root = resolveRoot(configuredRoot);
        Path directory = root.resolve(modelVersion.trim()).normalize();
        if (!directory.startsWith(root) || !isValid(directory, modelName, modelVersion.trim())) {
            throw new IOException("固定 Embedding 模型制品不存在或完整性校验失败: " + directory);
        }
        log.info("Embedding 固定制品校验通过: model={}, version={}, directory={}",
                modelName, modelVersion, directory);
        return directory;
    }

    private boolean isValid(Path directory, String modelName, String modelVersion) {
        try {
            Path manifestPath = directory.resolve(MANIFEST_FILE);
            if (!Files.isDirectory(directory) || !Files.isRegularFile(manifestPath)) {
                return false;
            }
            JsonNode manifest;
            try (var reader = Files.newBufferedReader(manifestPath)) {
                manifest = objectMapper.readTree(reader);
            }
            if (!SCHEMA_VERSION.equals(manifest.path("schemaVersion").asText())
                    || !modelName.equals(manifest.path("modelName").asText())
                    || !modelVersion.equals(manifest.path("modelVersion").asText())) {
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
                    && hasWeights && hasExpectedModel;
        } catch (Exception exception) {
            log.warn("Embedding 固定制品校验失败: directory={}, reason={}", directory, exception.getMessage());
            return false;
        }
    }

    private Path resolveRoot(String configuredRoot) {
        Path configured = Path.of(configuredRoot == null || configuredRoot.isBlank()
                ? "./model-files/embedding" : configuredRoot.trim());
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
// AI_GENERATE_END --
