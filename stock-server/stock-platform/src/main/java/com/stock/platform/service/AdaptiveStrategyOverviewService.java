// AI_GENERATE_START------
package com.stock.platform.service;

import com.stock.model.dto.ModelOverviewResponse;
import com.stock.model.dto.ModelTrainingRunResponse;
import com.stock.model.dto.ModelVersionResponse;
import com.stock.model.service.ModelOperationsQueryService;
import com.stock.strategy.config.StrategyConfigManager;
import com.stock.strategy.config.StrategyStateManager;
import com.stock.strategy.dto.AdaptiveStrategyEvolutionEvent;
import com.stock.strategy.dto.AdaptiveStrategyHealthItem;
import com.stock.strategy.dto.AdaptiveStrategyOverviewResponse;
import com.stock.strategy.dto.AdaptiveStrategyPerformanceResponse;
import com.stock.strategy.dto.AdaptiveStrategyVersionItem;
import com.stock.strategy.dto.StrategyConfigDto;
import com.stock.strategy.dto.StrategyStateDto;
import com.stock.trading.dto.PerformanceOverviewDto;
import com.stock.trading.dto.RealTradingStatusDto;
import com.stock.trading.service.PerformanceReportService;
import com.stock.trading.service.RealTradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 自适应策略观察页面聚合服务。
 *
 * 只聚合已有运行事实，不触发训练、激活、交易或 Agent 模型调用。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveStrategyOverviewService {

    private final StrategyConfigManager strategyConfigManager;
    private final StrategyStateManager strategyStateManager;
    private final ModelOperationsQueryService modelOperationsQueryService;
    private final RealTradingService realTradingService;
    private final PerformanceReportService performanceReportService;

    /**
     * 聚合当前自适应策略运行事实。
     *
     * @return 自适应策略页面聚合响应
     */
    public AdaptiveStrategyOverviewResponse getOverview() {
        List<String> anomalies = new ArrayList<>();
        StrategyConfigDto config = strategyConfigManager.getCurrentConfigDto();
        StrategyStateDto state = strategyStateManager.getCurrentState();
        ModelOverviewResponse model = loadModelOverview(anomalies);
        RealTradingStatusDto trading = loadTradingStatus(anomalies);
        PerformanceOverviewDto performance = loadPerformance(anomalies);
        ModelVersionResponse candidate = loadCandidateVersion(model, anomalies);
        List<AdaptiveStrategyVersionItem> versions = buildVersions(model, candidate);
        List<AdaptiveStrategyEvolutionEvent> events = buildEvolutionEvents(model, candidate, trading);
        String lifecycleStage = lifecycleStage(model, candidate);

        return AdaptiveStrategyOverviewResponse.builder()
                .lifecycleStage(lifecycleStage)
                .lifecycleDescription(lifecycleDescription(lifecycleStage))
                .strategyVersion(config.getVersion())
                .currentMode(state.getCurrentMode() == null ? config.getMode().name() : state.getCurrentMode().name())
                .strategyEnabled(config.isEnabled())
                .targetTradeDate(model == null || model.getActiveVersionScore() == null
                        ? null : model.getActiveVersionScore().getTargetTradeDate())
                .lastEvolutionAt(resolveLastEvolutionAt(model))
                .automaticTradingPaused(trading != null && trading.isAutomaticTradingPaused())
                .emergencyStopReason(trading == null ? null : trading.getEmergencyStopReason())
                .emergencyStoppedAt(trading == null ? null : trading.getEmergencyStoppedAt())
                .emergencyStopOperator(trading == null ? null : trading.getEmergencyStopOperator())
                .emergencyStopStrategyVersion(trading == null ? null : trading.getEmergencyStopStrategyVersion())
                .emergencyStopResult(trading == null ? null : trading.getEmergencyStopResult())
                .healthItems(buildHealthItems(config, model, trading))
                .versions(versions)
                .evolutionEvents(events)
                .performance(toPerformance(performance))
                .anomalies(anomalies)
                .build();
    }

    private ModelOverviewResponse loadModelOverview(List<String> anomalies) {
        try {
            return modelOperationsQueryService.getOverview();
        } catch (RuntimeException exception) {
            log.warn("自适应策略概览读取模型状态失败: {}", exception.getMessage());
            anomalies.add("模型状态读取失败：" + safeMessage(exception));
            return null;
        }
    }

    private RealTradingStatusDto loadTradingStatus(List<String> anomalies) {
        try {
            return realTradingService.getStatus();
        } catch (RuntimeException exception) {
            log.warn("自适应策略概览读取交易状态失败: {}", exception.getMessage());
            anomalies.add("交易执行状态读取失败：" + safeMessage(exception));
            return null;
        }
    }

    private PerformanceOverviewDto loadPerformance(List<String> anomalies) {
        try {
            return performanceReportService.getOverview();
        } catch (RuntimeException exception) {
            log.warn("自适应策略概览读取真实绩效失败: {}", exception.getMessage());
            anomalies.add("真实绩效读取失败：" + safeMessage(exception));
            return null;
        }
    }

    private ModelVersionResponse loadCandidateVersion(ModelOverviewResponse model, List<String> anomalies) {
        if (model == null || model.getLatestTrainingRun() == null
                || model.getLatestTrainingRun().getCandidateModelVersionId() == null) {
            return null;
        }
        String candidateId = model.getLatestTrainingRun().getCandidateModelVersionId();
        if (model.getActiveVersion() != null && candidateId.equals(model.getActiveVersion().getId())) {
            return null;
        }
        try {
            return modelOperationsQueryService.getVersionDetails(candidateId);
        } catch (RuntimeException exception) {
            anomalies.add("候选模型版本读取失败：" + safeMessage(exception));
            return null;
        }
    }

    private List<AdaptiveStrategyHealthItem> buildHealthItems(
            StrategyConfigDto config, ModelOverviewResponse model, RealTradingStatusDto trading) {
        List<AdaptiveStrategyHealthItem> items = new ArrayList<>();
        boolean evaluated = model != null && model.getActiveVersionScore() != null
                && model.getActiveVersionScore().getEvaluatedCount() > 0;
        items.add(health("data", "数据与评估", evaluated ? "HEALTHY" : "WARNING",
                evaluated ? "已存在生产模型的有效评估样本" : "尚未形成生产模型有效评估样本"));
        items.add(health("model", "模型", model == null ? "UNAVAILABLE"
                        : model.isActiveModelHealthy() ? "HEALTHY" : "WARNING",
                model == null ? "模型状态无法确认"
                        : model.isActiveModelHealthy() ? "激活模型完整性与兼容性检查通过"
                        : "激活模型健康状态未通过或尚未确认"));
        items.add(health("strategy", "策略", config.isEnabled() ? "HEALTHY" : "BLOCKED",
                config.isEnabled() ? "策略版本已启用并由系统管理" : "策略总开关处于关闭状态"));
        items.add(health("risk", "Java 风控", "WARNING",
                "风控链路在委托前强制执行；本概览未执行实时委托验证"));
        String executionStatus = trading == null ? "UNAVAILABLE"
                : trading.isAutomaticTradingPaused() ? "BLOCKED"
                : trading.isAutomaticExecutionEnabled() ? "HEALTHY" : "WARNING";
        String executionDetail = trading == null ? "交易执行状态无法确认"
                : trading.isAutomaticTradingPaused() ? "后续自动交易已被紧急停止"
                : trading.isAutomaticExecutionEnabled() ? "券商会话与真实写入门禁允许自动执行"
                : "当前处于观察状态或券商会话不可用";
        items.add(health("execution", "交易执行", executionStatus, executionDetail));
        return items;
    }

    private AdaptiveStrategyHealthItem health(String key, String label, String status, String detail) {
        return AdaptiveStrategyHealthItem.builder()
                .key(key).label(label).status(status).detail(detail).build();
    }

    private List<AdaptiveStrategyVersionItem> buildVersions(
            ModelOverviewResponse model, ModelVersionResponse candidate) {
        if (model == null) {
            return List.of();
        }
        List<AdaptiveStrategyVersionItem> items = new ArrayList<>();
        addVersion(items, "ACTIVE", model.getActiveVersion());
        addVersion(items, "PREVIOUS", model.getPreviousVersion());
        addVersion(items, "CANDIDATE", candidate);
        return items;
    }

    private void addVersion(List<AdaptiveStrategyVersionItem> items, String role, ModelVersionResponse version) {
        if (version == null) {
            return;
        }
        items.add(AdaptiveStrategyVersionItem.builder()
                .role(role)
                .versionId(version.getId())
                .modelVersion(version.getModelVersion())
                .status(version.getStatus())
                .architectureType(version.getArchitectureType())
                .featureVersion(version.getFeatureVersion())
                .meanRankIc(version.getWalkForwardMeanRankIc())
                .rankIcIr(version.getWalkForwardRankIcIr())
                .topDecileNetReturn(version.getWalkForwardTopDecileNetReturn())
                .outputCollapsed(version.getOutputCollapsed())
                .createdAt(version.getCreatedAt())
                .build());
    }

    private List<AdaptiveStrategyEvolutionEvent> buildEvolutionEvents(
            ModelOverviewResponse model, ModelVersionResponse candidate, RealTradingStatusDto trading) {
        List<AdaptiveStrategyEvolutionEvent> events = new ArrayList<>();
        ModelTrainingRunResponse run = model == null ? null : model.getLatestTrainingRun();
        if (run != null) {
            events.add(AdaptiveStrategyEvolutionEvent.builder()
                    .type("TRAINING_RUN")
                    .title("最近一次候选训练")
                    .status(run.getStatus())
                    .detail(run.getProgressMessage() == null ? "训练运行状态已记录" : run.getProgressMessage())
                    .occurredAt(run.getFinishedAt() == null ? run.getCreatedAt() : run.getFinishedAt())
                    .build());
        }
        if (candidate != null) {
            events.add(AdaptiveStrategyEvolutionEvent.builder()
                    .type("CANDIDATE_VERSION")
                    .title("候选版本已形成")
                    .status(candidate.getStatus())
                    .detail("候选版本尚未获得生产激活证据")
                    .occurredAt(candidate.getCreatedAt())
                    .build());
        }
        if (model != null && model.getActiveVersion() != null) {
            events.add(AdaptiveStrategyEvolutionEvent.builder()
                    .type("ACTIVE_VERSION")
                    .title("当前生产版本")
                    .status("ACTIVE")
                    .detail("时间为版本记录创建时间，不代表实际激活时间")
                    .occurredAt(model.getActiveVersion().getCreatedAt())
                    .build());
        }
        if (trading != null && trading.isAutomaticTradingPaused()) {
            events.add(AdaptiveStrategyEvolutionEvent.builder()
                    .type("EMERGENCY_STOP")
                    .title("自动交易紧急停止")
                    .status(trading.getEmergencyStopResult())
                    .detail("操作者：" + value(trading.getEmergencyStopOperator())
                            + "；策略版本：" + value(trading.getEmergencyStopStrategyVersion())
                            + "；原因：" + value(trading.getEmergencyStopReason()))
                    .occurredAt(trading.getEmergencyStoppedAt())
                    .build());
        }
        return events.stream()
                .sorted(Comparator.comparing(AdaptiveStrategyEvolutionEvent::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private AdaptiveStrategyPerformanceResponse toPerformance(PerformanceOverviewDto performance) {
        if (performance == null) {
            return null;
        }
        return AdaptiveStrategyPerformanceResponse.builder()
                .capturedAt(performance.getCapturedAt())
                .cumulativeReturnRate(performance.getCumulativeReturnRate())
                .winRate(performance.getWinRate())
                .averageReturnRate(performance.getAverageReturnRate())
                .worstReturnRate(performance.getWorstReturnRate())
                .closedTradeCount(performance.getClosedTradeCount())
                .drawdownAvailable(false)
                .turnoverAvailable(false)
                .build();
    }

    private String lifecycleStage(ModelOverviewResponse model, ModelVersionResponse candidate) {
        if (model != null && model.getActiveVersion() != null) {
            return "ACTIVE";
        }
        if (candidate != null && "CANDIDATE".equals(candidate.getStatus())) {
            return "CANDIDATE";
        }
        if (model != null && model.isTrainingRunning()) {
            return "CANDIDATE";
        }
        return "CANDIDATE";
    }

    private String lifecycleDescription(String stage) {
        return switch (stage) {
            case "ACTIVE" -> "存在生产模型；候选评估、自动晋级和异常回退由系统规则持续执行";
            default -> "系统处于候选生成或证据积累阶段，尚不能宣称已完成自学习闭环";
        };
    }

    private LocalDateTime resolveLastEvolutionAt(ModelOverviewResponse model) {
        if (model == null || model.getLatestTrainingRun() == null) {
            return null;
        }
        ModelTrainingRunResponse run = model.getLatestTrainingRun();
        return run.getFinishedAt() == null ? run.getCreatedAt() : run.getFinishedAt();
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private String value(String text) {
        return text == null || text.isBlank() ? "未记录" : text;
    }
}
// AI_GENERATE_END------
