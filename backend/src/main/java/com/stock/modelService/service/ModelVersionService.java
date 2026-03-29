package com.stock.modelService.service;

import com.stock.modelService.domain.entity.SentimentModelVersion;
import com.stock.modelService.persistence.SentimentEvaluationRepository;
import com.stock.modelService.persistence.SentimentModelVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 情感模型版本管理服务
 * <p>
 * 负责情感分析模型的版本管理，包括版本保存、回滚、查询和版本号生成。
 * 支持语义化版本号（vX.Y.Z），并记录回滚历史。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelVersionService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_DEPRECATED = "deprecated";
    private static final String STATUS_ROLLBACK = "rollback";
    private static final Pattern VERSION_PATTERN = Pattern.compile("v(\\d+)\\.(\\d+)\\.(\\d+)");

    private final SentimentModelVersionRepository versionRepository;
    private final SentimentEvaluationRepository evaluationRepository;

    /**
     * 保存新版本
     *
     * @param version 版本信息
     * @return 保存后的版本
     */
    public SentimentModelVersion saveVersion(SentimentModelVersion version) {
        if (version.getCreatedAt() == null) {
            version.setCreatedAt(LocalDateTime.now());
        }
        if (version.getStatus() == null) {
            version.setStatus(STATUS_ACTIVE);
        }
        if (version.getDeployedAt() == null && STATUS_ACTIVE.equals(version.getStatus())) {
            version.setDeployedAt(LocalDateTime.now());
        }

        SentimentModelVersion saved = versionRepository.save(version);
        log.info("保存情感模型版本成功, version={}, status={}", saved.getVersion(), saved.getStatus());
        return saved;
    }

    /**
     * 回滚到指定版本
     * <p>
     * 回滚逻辑：
     * 1. 将当前活跃版本状态改为 rollback
     * 2. 将目标版本状态改为 active
     * 3. 记录回滚历史
     * </p>
     *
     * @param versionId 版本ID
     * @return true 如果回滚成功
     */
    public boolean rollback(String versionId) {
        Optional<SentimentModelVersion> currentOpt = versionRepository.findTopByStatusOrderByDeployedAtDesc(STATUS_ACTIVE);
        Optional<SentimentModelVersion> targetOpt = versionRepository.findById(versionId);

        if (targetOpt.isEmpty()) {
            log.warn("回滚失败：目标版本不存在, versionId={}", versionId);
            return false;
        }

        SentimentModelVersion target = targetOpt.get();
        if (STATUS_ROLLBACK.equals(target.getStatus())) {
            log.warn("回滚失败：目标版本已处于回滚状态, versionId={}", versionId);
            return false;
        }

        if (currentOpt.isPresent()) {
            SentimentModelVersion current = currentOpt.get();
            if (current.getId().equals(versionId)) {
                log.warn("回滚失败：目标版本已是当前活跃版本, versionId={}", versionId);
                return false;
            }
            // 1. 将当前活跃版本状态改为 rollback
            current.setStatus(STATUS_ROLLBACK);
            current.setDeprecatedAt(LocalDateTime.now());
            versionRepository.save(current);
            log.info("当前活跃版本已标记为回滚, oldVersion={}", current.getVersion());
        }

        // 2. 将目标版本状态改为 active
        target.setStatus(STATUS_ACTIVE);
        target.setDeployedAt(LocalDateTime.now());
        target.setDeprecatedAt(null);
        versionRepository.save(target);
        log.info("目标版本已激活, targetVersion={}", target.getVersion());

        // 3. 记录回滚历史
        log.info("情感模型版本回滚完成: {} -> {}", currentOpt.map(SentimentModelVersion::getVersion).orElse("none"), target.getVersion());
        return true;
    }

    /**
     * 获取当前活跃版本
     *
     * @return 当前版本，不存在返回null
     */
    public SentimentModelVersion getCurrentVersion() {
        return versionRepository.findTopByStatusOrderByDeployedAtDesc(STATUS_ACTIVE)
                .orElse(null);
    }

    /**
     * 获取版本列表
     *
     * @param status 状态筛选，null表示全部
     * @return 版本列表
     */
    public List<SentimentModelVersion> listVersions(String status) {
        if (status == null || status.trim().isEmpty()) {
            return versionRepository.findAll();
        }
        return versionRepository.findByStatusOrderByDeployedAtDesc(status);
    }

    /**
     * 生成新版本号
     * <p>
     * 版本号格式：vX.Y.Z
     * X=大版本，Y=中版本，Z=小版本
     * </p>
     * <p>
     * 生成规则：
     * - major：大版本全量训练后递增
     * - minor：中版本周期训练后递增
     * - patch：小版本增量训练后递增
     * </p>
     *
     * @param incrementType 增量类型：major/minor/patch
     * @return 新版本号
     */
    public String generateVersion(String incrementType) {
        Optional<SentimentModelVersion> latestOpt = versionRepository.findTopByStatusOrderByDeployedAtDesc(STATUS_ACTIVE);
        if (latestOpt.isEmpty()) {
            return "v1.0.0";
        }

        String latestVersion = latestOpt.get().getVersion();
        int[] numbers = parseVersion(latestVersion);

        int major = numbers[0];
        int minor = numbers[1];
        int patch = numbers[2];

        if ("major".equalsIgnoreCase(incrementType)) {
            major++;
            minor = 0;
            patch = 0;
        } else if ("minor".equalsIgnoreCase(incrementType)) {
            minor++;
            patch = 0;
        } else {
            patch++;
        }

        String newVersion = String.format("v%d.%d.%d", major, minor, patch);
        log.info("生成新版本号: {} -> {}", latestVersion, newVersion);
        return newVersion;
    }

    private int[] parseVersion(String version) {
        int[] numbers = {0, 0, 0};
        if (version == null || version.isEmpty()) {
            return numbers;
        }

        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (matcher.find()) {
            try {
                numbers[0] = Integer.parseInt(matcher.group(1));
                numbers[1] = Integer.parseInt(matcher.group(2));
                numbers[2] = Integer.parseInt(matcher.group(3));
            } catch (NumberFormatException e) {
                log.warn("解析版本号失败，使用默认值: {}", version);
            }
        }
        return numbers;
    }
}