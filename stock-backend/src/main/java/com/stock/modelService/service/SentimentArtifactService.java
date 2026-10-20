// AI_GENERATE_START --------
package com.stock.modelService.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.modelService.config.SentimentTrainingConfig;
import com.stock.modelService.domain.vo.SentimentArtifactInspection;
import com.stock.modelService.domain.vo.SentimentArtifactManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 情感模型大制品检查服务。
 * 以流式方式校验版本目录、manifest、文件大小和 SHA-256，避免把大权重读入 Java 堆。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Service
@RequiredArgsConstructor
public class SentimentArtifactService {

    private static final String MANIFEST_SCHEMA = "sentiment-artifact-v1";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9._-]+$");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-fA-F0-9]{64}");
    private static final List<String> REQUIRED_FILES = List.of(
            "config.json", "tokenizer.json", "serving.properties");

    private final SentimentTrainingConfig config;
    private final ObjectMapper objectMapper;

    /**
     * 执行模型加载前的完整制品检查。
     *
     * @return 已完成 SHA-256 校验的制品描述
     * @throws IOException 目录、清单或任一文件不符合契约时抛出
     */
    public SentimentArtifactInspection inspectForLoad() throws IOException {
        return inspect(true);
    }

    /**
     * 执行健康接口使用的轻量制品检查。
     * 不重复计算大文件摘要，只校验目录、清单、文件存在性和大小。
     *
     * @return 制品检查结果，失败原因写入结果而不是向接口抛出
     */
    public SentimentArtifactInspection inspectForHealth() {
        try {
            return inspect(false);
        } catch (IOException | RuntimeException exception) {
            return SentimentArtifactInspection.builder()
                    .available(false)
                    .manifestPresent(false)
                    .checksumsVerified(false)
                    .modelDirectory(resolveConfiguredRoot())
                    .modelVersion(config.getModelVersion())
                    .errorMessage(exception.getMessage())
                    .build();
        }
    }

    private SentimentArtifactInspection inspect(boolean verifyChecksums) throws IOException {
        Path modelDirectory = resolveVersionDirectory();
        Path manifestPath = modelDirectory.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestPath)) {
            throw new IOException("情感模型缺少 manifest.json: " + manifestPath);
        }

        SentimentArtifactManifest manifest;
        try (var reader = Files.newBufferedReader(manifestPath)) {
            manifest = objectMapper.readValue(reader, SentimentArtifactManifest.class);
        }
        validateManifestContract(manifest);
        long totalSize = validateManifestFiles(modelDirectory, manifest, verifyChecksums);
        return SentimentArtifactInspection.builder()
                .available(true)
                .manifestPresent(true)
                .checksumsVerified(verifyChecksums)
                .modelDirectory(modelDirectory)
                .modelVersion(manifest.getModelVersion())
                .totalSizeBytes(totalSize)
                .manifestSha256(sha256(manifestPath))
                .metrics(manifest.getMetrics())
                .build();
    }

    private Path resolveVersionDirectory() throws IOException {
        String version = config.getModelVersion();
        if (version == null || version.isBlank() || !VERSION_PATTERN.matcher(version.trim()).matches()) {
            throw new IOException("情感模型版本未配置或格式非法: " + version);
        }
        Path root = resolveConfiguredRoot();
        Path versionDirectory = root.resolve(version.trim()).normalize();
        if (!versionDirectory.startsWith(root)) {
            throw new IOException("情感模型版本目录越界: " + version);
        }
        if (Files.isDirectory(versionDirectory)) {
            return versionDirectory;
        }
        throw new IOException("情感模型版本目录不存在: " + versionDirectory);
    }

    private Path resolveConfiguredRoot() {
        String configuredPath = config.getModelPath();
        Path configured = Paths.get(configuredPath == null || configuredPath.isBlank()
                ? "./model-artifacts/sentiment" : configuredPath.trim());
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        return workingDirectory.resolve(configured).normalize();
    }

    private void validateManifestContract(SentimentArtifactManifest manifest) throws IOException {
        if (manifest == null) {
            throw new IOException("情感模型 manifest 为空");
        }
        requireEquals("schemaVersion", MANIFEST_SCHEMA, manifest.getSchemaVersion());
        requireEquals("modelName", config.getModelName(), manifest.getModelName());
        requireEquals("modelVersion", config.getModelVersion(), manifest.getModelVersion());
        requireEquals("engineName", "PyTorch", manifest.getEngineName());
        if (!Integer.valueOf(config.getMaxSequenceLength()).equals(manifest.getMaxSequenceLength())) {
            throw new IOException("情感模型最大序列长度不匹配");
        }
        List<String> expectedLabels = Arrays.asList(config.getLabels());
        if (!expectedLabels.equals(manifest.getLabels())) {
            throw new IOException("情感模型标签顺序不匹配，期望: " + expectedLabels);
        }
        if (manifest.getFiles() == null || manifest.getFiles().isEmpty()) {
            throw new IOException("情感模型 manifest 未声明制品文件");
        }
    }

    private long validateManifestFiles(Path modelDirectory,
                                       SentimentArtifactManifest manifest,
                                       boolean verifyChecksums) throws IOException {
        Set<String> listedFiles = new HashSet<>();
        long totalSize = 0L;
        boolean hasModelWeights = false;
        for (SentimentArtifactManifest.ArtifactFile artifactFile : manifest.getFiles()) {
            validateFileEntry(artifactFile);
            String relativePath = artifactFile.getPath().replace('\\', '/');
            if (!listedFiles.add(relativePath)) {
                throw new IOException("情感模型 manifest 存在重复文件: " + relativePath);
            }
            if (MANIFEST_FILE.equalsIgnoreCase(relativePath)) {
                throw new IOException("manifest.json 不能把自身列入文件清单");
            }
            Path file = modelDirectory.resolve(relativePath).normalize();
            if (!file.startsWith(modelDirectory) || !Files.isRegularFile(file)) {
                throw new IOException("情感模型文件不存在或路径越界: " + relativePath);
            }
            long actualSize = Files.size(file);
            if (actualSize != artifactFile.getSize()) {
                throw new IOException("情感模型文件大小不一致: " + relativePath);
            }
            if (actualSize > config.getMaxSingleFileBytes()) {
                throw new IOException("情感模型单文件超过限制: " + relativePath);
            }
            totalSize = Math.addExact(totalSize, actualSize);
            if (totalSize > config.getMaxTotalArtifactBytes()) {
                throw new IOException("情感模型制品总体积超过限制");
            }
            if (verifyChecksums && !artifactFile.getSha256().equalsIgnoreCase(sha256(file))) {
                throw new IOException("情感模型文件摘要不一致: " + relativePath);
            }
            hasModelWeights = hasModelWeights || isModelWeight(relativePath);
        }
        for (String requiredFile : REQUIRED_FILES) {
            if (!listedFiles.contains(requiredFile)) {
                throw new IOException("情感模型 manifest 缺少必需文件: " + requiredFile);
            }
        }
        if (!hasModelWeights) {
            throw new IOException("情感模型 manifest 未声明模型权重文件");
        }
        return totalSize;
    }

    private void validateFileEntry(SentimentArtifactManifest.ArtifactFile artifactFile) throws IOException {
        if (artifactFile == null || artifactFile.getPath() == null || artifactFile.getPath().isBlank()) {
            throw new IOException("情感模型 manifest 包含空文件路径");
        }
        if (artifactFile.getSize() == null || artifactFile.getSize() < 0L) {
            throw new IOException("情感模型 manifest 文件大小非法: " + artifactFile.getPath());
        }
        if (artifactFile.getSha256() == null
                || !SHA256_PATTERN.matcher(artifactFile.getSha256()).matches()) {
            throw new IOException("情感模型 manifest 文件摘要非法: " + artifactFile.getPath());
        }
    }

    private void requireEquals(String field, String expected, String actual) throws IOException {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IOException("情感模型 " + field + " 不匹配，期望=" + expected + "，实际=" + actual);
        }
    }

    private boolean isModelWeight(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".safetensors")
                || normalized.endsWith(".bin")
                || normalized.endsWith(".pt");
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            try (InputStream inputStream = Files.newInputStream(path)) {
                int length;
                while ((length = inputStream.read(buffer)) >= 0) {
                    if (length > 0) {
                        digest.update(buffer, 0, length);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
// AI_GENERATE_END --------
