package com.stock.strategyAnalysis.service;

import com.stock.strategyAnalysis.config.StrategyStateManager;
import com.stock.strategyAnalysis.dto.AnalysisStrategyItemDto;
import com.stock.strategyAnalysis.dto.StrategyStateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 策略分析页业务服务
 * 为「分析量化模型」页面提供策略列表与启用状态更新，与前端 id/nameKey 约定一致
 *
 * @author AI Assistant
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyAnalysisService {

    /** 选股策略 id */
    public static final String ID_SELECTION_DOUBLE_FACTOR = "doubleFactor";
    /** 交易策略 id：T+1（无对应开关，常开） */
    public static final String ID_TRADING_TPLUS1 = "tplus1";
    /** 交易策略 id：移动止损，对应后端 indicator */
    public static final String ID_TRADING_STOP_LOSS = "stopLoss";
    public static final String INDICATOR_TRAILING_STOP = "trailingStop";
    public static final String ID_TRADING_RSI = "rsi";
    public static final String INDICATOR_RSI = "rsi";
    public static final String ID_TRADING_VOLUME = "volume";
    public static final String INDICATOR_VOLUME = "volume";
    public static final String ID_TRADING_BOLLINGER = "bollinger";
    public static final String INDICATOR_BOLLINGER = "bollinger";

    private static final Map<String, String> TRADING_ID_TO_INDICATOR = Map.of(
            ID_TRADING_STOP_LOSS, INDICATOR_TRAILING_STOP,
            ID_TRADING_RSI, INDICATOR_RSI,
            ID_TRADING_VOLUME, INDICATOR_VOLUME,
            ID_TRADING_BOLLINGER, INDICATOR_BOLLINGER
    );

    private final StrategyStateManager stateManager;

    /**
     * 获取分析页策略列表（选股 + 交易），含启用状态与占位指标
     *
     * @return 策略项列表，与前端表格一一对应
     */
    public List<AnalysisStrategyItemDto> getAnalysisList() {
        StrategyStateDto state = stateManager.getCurrentState();
        boolean selectionEnabled = state.isEnabled();
        List<String> disabled = state.getDisabledIndicators() != null ? state.getDisabledIndicators() : new ArrayList<>();

        List<AnalysisStrategyItemDto> list = new ArrayList<>();

        // 选股：双因子
        list.add(AnalysisStrategyItemDto.builder()
                .id(ID_SELECTION_DOUBLE_FACTOR)
                .type("selection")
                .nameKey("strategyAnalysis.selection.doubleFactor")
                .active(selectionEnabled)
                .winRate(68.5)
                .pnl(12450)
                .totalTrades(142)
                .build());

        // 交易：T+1（常开）
        list.add(AnalysisStrategyItemDto.builder()
                .id(ID_TRADING_TPLUS1)
                .type("trading")
                .nameKey("strategyAnalysis.trading.tplus1")
                .active(true)
                .winRate(72.1)
                .pnl(8920)
                .totalTrades(315)
                .build());

        // 交易：移动止损
        list.add(AnalysisStrategyItemDto.builder()
                .id(ID_TRADING_STOP_LOSS)
                .type("trading")
                .nameKey("strategyAnalysis.trading.stopLoss")
                .active(!disabled.contains(INDICATOR_TRAILING_STOP))
                .winRate(55.4)
                .pnl(-1200)
                .totalTrades(89)
                .build());

        // 交易：RSI
        list.add(AnalysisStrategyItemDto.builder()
                .id(ID_TRADING_RSI)
                .type("trading")
                .nameKey("strategyAnalysis.trading.rsi")
                .active(!disabled.contains(INDICATOR_RSI))
                .winRate(48.2)
                .pnl(560)
                .totalTrades(45)
                .build());

        // 交易：成交量
        list.add(AnalysisStrategyItemDto.builder()
                .id(ID_TRADING_VOLUME)
                .type("trading")
                .nameKey("strategyAnalysis.trading.volume")
                .active(!disabled.contains(INDICATOR_VOLUME))
                .winRate(61.8)
                .pnl(3400)
                .totalTrades(112)
                .build());

        // 交易：布林带
        list.add(AnalysisStrategyItemDto.builder()
                .id(ID_TRADING_BOLLINGER)
                .type("trading")
                .nameKey("strategyAnalysis.trading.bollinger")
                .active(!disabled.contains(INDICATOR_BOLLINGER))
                .winRate(51.0)
                .pnl(-450)
                .totalTrades(23)
                .build());

        return list;
    }

    /**
     * 设置某策略的启用状态
     *
     * @param id    策略 id，与前端一致
     * @param active 是否启用
     */
    public void setStrategyActive(String id, boolean active) {
        if (ID_SELECTION_DOUBLE_FACTOR.equals(id)) {
            stateManager.setEnabled(active);
            return;
        }
        if (ID_TRADING_TPLUS1.equals(id)) {
            // T+1 无对应开关，忽略或仅记录
            log.debug("T+1 策略无开关，忽略 setActive({})", active);
            return;
        }
        String indicator = TRADING_ID_TO_INDICATOR.get(id);
        if (indicator != null) {
            if (active) {
                stateManager.enableIndicator(indicator);
            } else {
                stateManager.disableIndicator(indicator);
            }
        } else {
            log.warn("未知策略 id: {}", id);
        }
    }
}
