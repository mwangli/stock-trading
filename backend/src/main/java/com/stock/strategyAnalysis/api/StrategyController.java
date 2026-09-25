// AI_GENERATE_START --
package com.stock.strategyAnalysis.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.config.StrategyModeManager;
import com.stock.strategyAnalysis.config.StrategyStateManager;
import com.stock.strategyAnalysis.domain.vo.SelectionResult;
import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import com.stock.strategyAnalysis.domain.entity.StrategyMode;
import com.stock.strategyAnalysis.domain.dto.StrategyStateDto;
import com.stock.strategyAnalysis.domain.dto.StockRankingDto;
import com.stock.strategyAnalysis.engine.StockSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final StrategyConfigService configService;
    private final StrategyModeManager modeManager;
    private final StrategyStateManager stateManager;

    /**
     * 执行选股
     *
     * @param n 选取数量
     */
    @PostMapping("/select")
    public ResponseDTO<SelectionResult> executeSelection(@RequestParam(defaultValue = "10") int n) {
        log.info("[Strategy] 执行选股 | n={}", n);
        SelectionResult result = stockSelector.selectTopN(n);
        return ResponseDTO.success(result);
    }

    /**
     * 获取选股结果（排行榜）
     */
    @GetMapping("/rankings")
    public ResponseDTO<List<StockRankingDto>> getRankings() {
        log.info("[Strategy] 获取选股排行榜");
        return ResponseDTO.success(stockSelector.getAllRankings());
    }

    /**
     * 获取策略状态
     */
    @GetMapping("/state")
    public ResponseDTO<StrategyStateDto> getState() {
        log.info("[Strategy] 获取策略状态");
        return ResponseDTO.success(stateManager.getCurrentState());
    }

    /**
     * 切换策略模式
     */
    @PostMapping("/mode")
    public ResponseDTO<String> switchMode(@RequestParam StrategyMode mode) {
        log.info("[Strategy] 切换策略模式 | mode={}", mode);
        modeManager.switchMode(mode);
        return ResponseDTO.success("策略模式已切换为: " + mode.getName());
    }

    /**
     * 获取策略配置
     */
    @GetMapping("/config")
    public ResponseDTO<StrategyConfig> getConfig() {
        log.info("[Strategy] 获取策略配置");
        return ResponseDTO.success(configService.getCurrentConfig());
    }

    /**
     * 更新策略配置
     */
    @PutMapping("/config")
    public ResponseDTO<String> updateConfig(@RequestBody StrategyConfig config) {
        log.info("[Strategy] 更新策略配置");
        configService.updateConfig(config);
        return ResponseDTO.success("策略配置已更新");
    }

    /**
     * 重置策略配置
     */
    @PostMapping("/config/reset")
    public ResponseDTO<String> resetConfig() {
        log.info("[Strategy] 重置策略配置");
        configService.resetToDefault();
        return ResponseDTO.success("策略配置已重置为默认值");
    }

}
// AI_GENERATE_END --
