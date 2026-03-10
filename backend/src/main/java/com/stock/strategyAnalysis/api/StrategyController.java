package com.stock.strategyAnalysis.api;

import com.stock.common.dto.ResponseDTO;
import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.config.StrategyModeManager;
import com.stock.strategyAnalysis.config.StrategyStateManager;
import com.stock.strategyAnalysis.domain.vo.SelectionResult;
import com.stock.strategyAnalysis.domain.dto.AnalysisStrategyItemDto;
import com.stock.strategyAnalysis.domain.dto.AnalysisStrategyToggleRequestDto;
import com.stock.strategyAnalysis.domain.dto.StrategyGenericRequestDto;
import com.stock.strategyAnalysis.domain.dto.StrategyOperationResponseDto;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import com.stock.strategyAnalysis.domain.entity.StrategyMode;
import com.stock.strategyAnalysis.domain.dto.StrategyStateDto;
import com.stock.strategyAnalysis.domain.dto.StockRankingDto;
import com.stock.strategyAnalysis.engine.IntradaySellService;
import com.stock.strategyAnalysis.engine.StockSelector;
import com.stock.strategyAnalysis.service.StrategyAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 策略 API 控制器
 * <p>
 * 提供选股、策略配置、策略模式切换、分析页策略列表等接口。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final StockSelector stockSelector;
    private final IntradaySellService intradaySellService;
    private final StrategyConfigService configService;
    private final StrategyModeManager modeManager;
    private final StrategyStateManager stateManager;
    private final StrategyAnalysisService strategyAnalysisService;

    /**
     * 获取分析页策略列表（选股 + 交易），含启用状态与占位指标
     */
    @GetMapping("/analysis/list")
    public ResponseEntity<ResponseDTO<List<AnalysisStrategyItemDto>>> getAnalysisList() {
        log.info("[Strategy] 获取分析页策略列表");
        return ResponseEntity.ok(ResponseDTO.success(strategyAnalysisService.getAnalysisList()));
    }

    /**
     * 设置分析页某策略的启用状态
     * <p>
     * 路径参数 id 在末尾，符合规范。兼容旧路径 /analysis/{id}/active。
     * </p>
     *
     * @param id   策略 id，与前端一致：doubleFactor, tplus1, stopLoss, rsi, volume, bollinger
     * @param body 请求体，包含 active 字段
     */
    @PutMapping("/analysis/active/{id}")
    public ResponseEntity<ResponseDTO<StrategyOperationResponseDto>> setAnalysisStrategyActive(
            @PathVariable String id,
            @RequestBody AnalysisStrategyToggleRequestDto body) {
        log.info("[Strategy] 设置分析策略启用状态 | id={}, active={}", id, body != null ? body.isActive() : null);
        if (body == null) {
            return ResponseEntity.badRequest().body(
                    ResponseDTO.error("请求体不能为空"));
        }
        strategyAnalysisService.setStrategyActive(id, body.isActive());
        return ResponseEntity.ok(ResponseDTO.success(StrategyOperationResponseDto.builder()
                .success(true)
                .message("已更新")
                .build()));
    }

    /**
     * 执行选股
     *
     * @param n 选取数量
     */
    @PostMapping("/select")
    public ResponseEntity<ResponseDTO<SelectionResult>> executeSelection(@RequestParam(defaultValue = "10") int n) {
        log.info("[Strategy] 执行选股 | n={}", n);
        SelectionResult result = stockSelector.selectTopN(n);
        return ResponseEntity.ok(ResponseDTO.success(result));
    }

    /**
     * 获取选股结果（排行榜）
     */
    @GetMapping("/rankings")
    public ResponseEntity<ResponseDTO<List<StockRankingDto>>> getRankings() {
        log.info("[Strategy] 获取选股排行榜");
        return ResponseEntity.ok(ResponseDTO.success(stockSelector.getAllRankings()));
    }

    /**
     * 获取策略状态
     */
    @GetMapping("/state")
    public ResponseEntity<ResponseDTO<StrategyStateDto>> getState() {
        log.info("[Strategy] 获取策略状态");
        return ResponseEntity.ok(ResponseDTO.success(stateManager.getCurrentState()));
    }

    /**
     * 切换策略模式
     */
    @PostMapping("/mode")
    public ResponseEntity<ResponseDTO<String>> switchMode(@RequestParam StrategyMode mode) {
        log.info("[Strategy] 切换策略模式 | mode={}", mode);
        modeManager.switchMode(mode);
        return ResponseEntity.ok(ResponseDTO.success("策略模式已切换为: " + mode.getName()));
    }

    /**
     * 获取策略配置
     */
    @GetMapping("/config")
    public ResponseEntity<ResponseDTO<StrategyConfig>> getConfig() {
        log.info("[Strategy] 获取策略配置");
        return ResponseEntity.ok(ResponseDTO.success(configService.getCurrentConfig()));
    }

    /**
     * 更新策略配置
     */
    @PutMapping("/config")
    public ResponseEntity<ResponseDTO<String>> updateConfig(@RequestBody StrategyConfig config) {
        log.info("[Strategy] 更新策略配置");
        configService.updateConfig(config);
        return ResponseEntity.ok(ResponseDTO.success("策略配置已更新"));
    }

    /**
     * 重置策略配置
     */
    @PostMapping("/config/reset")
    public ResponseEntity<ResponseDTO<String>> resetConfig() {
        log.info("[Strategy] 重置策略配置");
        configService.resetToDefault();
        return ResponseEntity.ok(ResponseDTO.success("策略配置已重置为默认值"));
    }

    /**
     * 创建策略 (Mock)
     */
    @PostMapping("/create")
    public ResponseEntity<ResponseDTO<StrategyOperationResponseDto>> createStrategy(@RequestBody(required = false) StrategyGenericRequestDto params) {
        log.info("[Strategy] 创建策略 | id={}, name={}", params != null ? params.getId() : null, params != null ? params.getName() : null);
        return ResponseEntity.ok(ResponseDTO.success(StrategyOperationResponseDto.builder()
                .success(true)
                .message("创建成功")
                .build()));
    }

    /**
     * 更新策略 (Mock)
     */
    @PutMapping("/update")
    public ResponseEntity<ResponseDTO<StrategyOperationResponseDto>> updateStrategy(@RequestBody(required = false) StrategyGenericRequestDto params) {
        log.info("[Strategy] 更新策略 | id={}, name={}", params != null ? params.getId() : null, params != null ? params.getName() : null);
        return ResponseEntity.ok(ResponseDTO.success(StrategyOperationResponseDto.builder()
                .success(true)
                .message("更新成功")
                .build()));
    }

    /**
     * 选择策略 (Mock)
     */
    @PostMapping("/choose")
    public ResponseEntity<ResponseDTO<StrategyOperationResponseDto>> chooseStrategy(@RequestBody(required = false) StrategyGenericRequestDto params) {
        log.info("[Strategy] 选择策略 | id={}, name={}", params != null ? params.getId() : null, params != null ? params.getName() : null);
        return ResponseEntity.ok(ResponseDTO.success(StrategyOperationResponseDto.builder()
                .success(true)
                .message("选择成功")
                .build()));
    }

    /**
     * 删除策略 (Mock)
     */
    @DeleteMapping("/delete")
    public ResponseEntity<ResponseDTO<StrategyOperationResponseDto>> deleteStrategy(@RequestParam(required = false) String id) {
        log.info("[Strategy] 删除策略 | id={}", id);
        return ResponseEntity.ok(ResponseDTO.success(StrategyOperationResponseDto.builder()
                .success(true)
                .message("删除成功")
                .build()));
    }
}
