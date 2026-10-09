// AI_GENERATE_START --
package com.stock.modelService.service;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.stock.modelService.config.OnnxInferenceConfig;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 复用 ONNX Runtime Environment 和 Session，并在应用关闭时释放原生资源。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Service
@RequiredArgsConstructor
public class OnnxSessionRegistry {

    private final OnnxInferenceConfig config;
    private final OnnxArtifactValidator artifactValidator;
    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final Map<Path, SessionHandle> sessions = new ConcurrentHashMap<>();

    /**
     * 获取或创建候选制品 Session。
     *
     * @param artifactDir 候选制品目录
     * @return Session 句柄
     */
    public SessionHandle get(Path artifactDir) {
        if (!config.isEnabled()) {
            throw new IllegalStateException("ONNX 推理未启用");
        }
        Path key = artifactDir.toAbsolutePath().normalize();
        return sessions.computeIfAbsent(key, this::createSession);
    }

    private SessionHandle createSession(Path artifactDir) {
        OnnxArtifactValidator.ValidatedOnnxArtifact artifact = artifactValidator.validate(artifactDir);
        try {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(config.getIntraOpThreads());
            options.setInterOpNumThreads(config.getInterOpThreads());
            OrtSession session = environment.createSession(artifact.modelPath().toString(), options);
            return new SessionHandle(environment, session, artifact);
        } catch (OrtException exception) {
            throw new IllegalStateException("创建 ONNX Session 失败", exception);
        }
    }

    /** 关闭全部 ONNX Session。 */
    @PreDestroy
    public void close() {
        for (SessionHandle handle : sessions.values()) {
            try {
                handle.session().close();
            } catch (OrtException ignored) {
                // 应用关闭阶段不覆盖原始关闭流程。
            }
        }
        sessions.clear();
    }

    /** Environment、Session 与其已验证元数据。 */
    public record SessionHandle(
            OrtEnvironment environment,
            OrtSession session,
            OnnxArtifactValidator.ValidatedOnnxArtifact artifact) {
    }
}
// AI_GENERATE_END --
