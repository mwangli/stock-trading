package com.stock.strategyAnalysis.api;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.config.StrategyModeManager;
import com.stock.strategyAnalysis.config.StrategyStateManager;
import com.stock.strategyAnalysis.domain.vo.SelectionResult;
import com.stock.strategyAnalysis.domain.dto.AnalysisStrategyItemDto;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import com.stock.strategyAnalysis.domain.entity.StrategyMode;
import com.stock.strategyAnalysis.domain.dto.StrategyStateDto;
import com.stock.strategyAnalysis.engine.IntradaySellService;
import com.stock.strategyAnalysis.engine.StockSelector;
import com.stock.strategyAnalysis.service.StrategyAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 策略API控制器
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
    public ResponseEntity<List<AnalysisStrategyItemDto>> getAnalysisList() {
        return ResponseEntity.ok(strategyAnalysisService.getAnalysisList());
    }

    /**
     * 设置分析页某策略的启用状态
     *
     * @param id 策略 id，与前端一致：doubleFactor, tplus1, stopLoss, rsi, volume, bollinger
     */
    @PutMapping("/analysis/{id}/active")
    public ResponseEntity<String> setAnalysisStrategyActive(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body) {
        Boolean active = body != null ? body.get("active") : null;
        if (active == null) {
            return ResponseEntity.badRequest().body("缺少 active 字段");
        }
        strategyAnalysisService.setStrategyActive(id, active);
        return ResponseEntity.ok("已更新");
    }

    /**
     * 执行选股
     */
    @PostMapping("/select")
    public ResponseEntity<SelectionResult> executeSelection(@RequestParam(defaultValue = "10") int n) {
        log.info("API调用: 执行选股, n={}", n);
        SelectionResult result = stockSelector.selectTopN(n);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取选股结果
     */
    @GetMapping("/rankings")
    public ResponseEntity<List<?>> getRankings() {
        return ResponseEntity.ok(stockSelector.getAllRankings());
    }

    /**
     * 获取策略状态
     */
    @GetMapping("/state")
    public ResponseEntity<StrategyStateDto> getState() {
        return ResponseEntity.ok(stateManager.getCurrentState());
    }

    /**
     * 切换策略模式
     */
    @PostMapping("/mode")
    public ResponseEntity<String> switchMode(@RequestParam StrategyMode mode) {
        log.info("API调用: 切换策略模式 {}", mode);
        modeManager.switchMode(mode);
        return ResponseEntity.ok("策略模式已切换为: " + mode.getName());
    }

    /**
     * 获取策略配置
     */
    @GetMapping("/config")
    public ResponseEntity<StrategyConfig> getConfig() {
        return ResponseEntity.ok(configService.getCurrentConfig());
    }

    /**
     * 更新策略配置
     */
    @PutMapping("/config")
    public ResponseEntity<String> updateConfig(@RequestBody StrategyConfig config) {
        log.info("API调用: 更新策略配置");
        configService.updateConfig(config);
        return ResponseEntity.ok("策略配置已更新");
    }

    /**
     * 重置策略配置
     */
    @PostMapping("/config/reset")
    public ResponseEntity<String> resetConfig() {
        configService.resetToDefault();
        return ResponseEntity.ok("策略配置已重置为默认值");
    }

    /**
     * 创建策略 (Mock)
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createStrategy(@RequestBody Map<String, Object> params) {
        log.info("API调用: 创建策略 {}", params);
        return ResponseEntity.ok(Map.of("success", true, "message", "创建成功"));
    }

    /**
     * 更新策略 (Mock)
     */
    @PutMapping("/update")
    public ResponseEntity<Map<String, Object>> updateStrategy(@RequestBody Map<String, Object> params) {
        log.info("API调用: 更新策略 {}", params);
        return ResponseEntity.ok(Map.of("success", true, "message", "更新成功"));
    }

    /**
     * 选择策略 (Mock)
     */
    @PostMapping("/choose")
    public ResponseEntity<Map<String, Object>> chooseStrategy(@RequestBody Map<String, Object> params) {
        log.info("API调用: 选择策略 {}", params);
        return ResponseEntity.ok(Map.of("success", true, "message", "选择成功"));
    }

    /**
     * 删除策略 (Mock)
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteStrategy(@RequestParam(required = false) String id) {
        log.info("API调用: 删除策略 {}", id);
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }
}
