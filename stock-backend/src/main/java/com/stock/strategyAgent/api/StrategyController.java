// AI_GENERATE_START ---------
package com.stock.strategyAgent.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.strategyAgent.config.StrategyConfigService;
import com.stock.strategyAgent.config.StrategyModeManager;
import com.stock.strategyAgent.config.StrategyStateManager;
import com.stock.strategyAgent.domain.vo.SelectionResult;
import com.stock.strategyAgent.domain.dto.StrategyConfigDto;
import com.stock.strategyAgent.domain.entity.StrategyMode;
import com.stock.strategyAgent.domain.dto.StrategyStateDto;
import com.stock.strategyAgent.domain.dto.StockRankingDto;
import com.stock.strategyAgent.engine.StockSelector;
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
     * 设置规则自动切换开关。
     *
     * @param enabled 是否启用市场规则自动切换
     * @return 更新后的策略状态
     */
    @PostMapping("/autoSwitch")
    public ResponseDTO<StrategyStateDto> setAutoSwitch(@RequestParam boolean enabled) {
        log.info("[Strategy] 设置规则自动切换 | enabled={}", enabled);
        stateManager.setAutoSwitchEnabled(enabled);
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
    public ResponseDTO<StrategyConfigDto> getConfig() {
        log.info("[Strategy] 获取策略配置");
        return ResponseDTO.success(configService.getCurrentConfigDto());
    }

    /**
     * 更新策略配置
     */
    @PutMapping("/config")
    public ResponseDTO<StrategyConfigDto> updateConfig(@RequestBody StrategyConfigDto config) {
        log.info("[Strategy] 更新策略配置");
        StrategyConfigDto saved = configService.updateConfig(config);
        stateManager.updateMode(saved.getMode(), "页面保存策略配置");
        return ResponseDTO.success(saved);
    }

    /**
     * 重置策略配置
     */
    @PostMapping("/config/reset")
    public ResponseDTO<StrategyConfigDto> resetConfig() {
        log.info("[Strategy] 重置策略配置");
        StrategyConfigDto reset = configService.resetToDefaultDto();
        stateManager.updateMode(reset.getMode(), "页面恢复默认策略配置");
        return ResponseDTO.success(reset);
    }

}
// AI_GENERATE_END ---------
