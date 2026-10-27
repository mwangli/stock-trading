// AI_GENERATE_START --------
package com.stock.agent.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 解析 Agent 固定 Embedding 模型目录。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
public class EmbeddingArtifactService {

    /**
     * 返回配置的 Embedding 模型版本目录。
     * 模型内容由 DJL 在实际加载时验证，失败直接抛出加载异常。
     *
     * @param modelName 模型名称
     * @param modelVersion 模型版本
     * @param configuredRoot 模型根目录
     * @return 模型版本目录
     * @throws IOException 版本未配置或目录越界
     */
    public Path resolve(String modelName, String modelVersion, String configuredRoot) throws IOException {
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IOException("Embedding 模型版本未配置: " + modelName);
        }
        Path root = resolveRoot(configuredRoot);
        Path directory = root.resolve(modelVersion.trim()).normalize();
        if (!directory.startsWith(root)) {
            throw new IOException("Embedding 模型目录越界: " + directory);
        }
        return directory;
    }

    private Path resolveRoot(String configuredRoot) {
        Path configured = Path.of(configuredRoot == null || configuredRoot.isBlank()
                ? "./model-files/embedding" : configuredRoot.trim());
        return configured.isAbsolute()
                ? configured.normalize()
                : Path.of("").toAbsolutePath().normalize().resolve(configured).normalize();
    }
}
// AI_GENERATE_END --------
