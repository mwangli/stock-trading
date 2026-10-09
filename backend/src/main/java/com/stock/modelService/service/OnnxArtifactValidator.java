// AI_GENERATE_START -
package com.stock.modelService.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.modelService.domain.dto.OnnxModelMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 在创建 ONNX Session 前验证候选制品完整性和离线验收状态。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Service
@RequiredArgsConstructor
public class OnnxArtifactValidator {

    private final ObjectMapper objectMapper;

    /**
     * 验证候选目录并返回强类型元数据。
     *
     * @param artifactDir 候选制品目录
     * @return 已验证制品
     */
    public ValidatedOnnxArtifact validate(Path artifactDir) {
        try {
            Path root = artifactDir.toAbsolutePath().normalize();
            Path modelPath = requiredFile(root, "model.onnx");
            Path metadataPath = requiredFile(root, "metadata.json");
            Path metricsPath = requiredFile(root, "metrics.json");
            OnnxModelMetadata metadata = objectMapper.readValue(
                    metadataPath.toFile(), OnnxModelMetadata.class);
            JsonNode metrics = objectMapper.readTree(metricsPath.toFile());
            if (!"onnx".equals(metadata.getArtifactFormat())) {
                throw new IllegalStateException("候选模型格式不是 ONNX");
            }
            if (!metrics.path("passed").asBoolean(false)) {
                throw new IllegalStateException("候选模型离线指标未通过");
            }
            String actualSha256 = sha256(modelPath);
            if (!actualSha256.equals(metadata.getModelSha256())) {
                throw new IllegalStateException("候选模型 SHA-256 不匹配");
            }
            return new ValidatedOnnxArtifact(root, modelPath, metadata);
        } catch (IOException exception) {
            throw new IllegalStateException("ONNX 候选制品读取失败", exception);
        }
    }

    private Path requiredFile(Path root, String name) {
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IllegalStateException("ONNX 候选制品缺少文件: " + name);
        }
        return file;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    /** 已验证的候选 ONNX 制品。 */
    public record ValidatedOnnxArtifact(
            Path artifactDir, Path modelPath, OnnxModelMetadata metadata) {
    }
}
// AI_GENERATE_END -
