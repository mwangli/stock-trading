// AI_GENERATE_START ----
package com.stock.dataCollector.api;

import com.stock.dataCollector.domain.dto.AdjustmentFactorImportRequestDto;
import com.stock.dataCollector.domain.dto.HistoricalUniverseImportRequestDto;
import com.stock.dataCollector.domain.dto.MarketDataImportResultDto;
import com.stock.dataCollector.domain.dto.MinuteBarImportRequestDto;
import com.stock.dataCollector.domain.dto.ModelReferenceDailyBarImportRequestDto;
import com.stock.dataCollector.domain.dto.ModelStockDailyBarImportRequestDto;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.dataCollector.domain.dto.TradeCalendarImportRequestDto;
import com.stock.dataCollector.domain.dto.StockDailyTradabilityImportRequestDto;
import com.stock.dataCollector.service.MarketDataFoundationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产级回测市场数据基础导入接口。
 * 外部数据源协议由后续适配器负责，本接口只接收已明确来源和版本的结构化事实。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@RestController
@RequestMapping("/api/market-data-foundation")
@RequiredArgsConstructor
public class MarketDataFoundationController {

    private final MarketDataFoundationService marketDataFoundationService;

    /**
     * 导入历史股票池有效期版本。
     *
     * @param request 历史股票池批次
     * @return 导入统计
     */
    @PostMapping("/historical-universe/import")
    public ResponseDTO<MarketDataImportResultDto> importHistoricalUniverse(
            @Valid @RequestBody HistoricalUniverseImportRequestDto request) {
        log.info("[MarketDataFoundation] 导入历史股票池: source={}, version={}, count={}",
                request.getSource(), request.getSourceVersion(), request.getItems().size());
        return ResponseDTO.success(marketDataFoundationService.importHistoricalUniverse(request));
    }

    /**
     * 导入证券市场交易日历。
     *
     * @param request 交易日历批次
     * @return 导入统计
     */
    @PostMapping("/trade-calendar/import")
    public ResponseDTO<MarketDataImportResultDto> importTradeCalendar(
            @Valid @RequestBody TradeCalendarImportRequestDto request) {
        log.info("[MarketDataFoundation] 导入交易日历: source={}, version={}, count={}",
                request.getSource(), request.getSourceVersion(), request.getItems().size());
        return ResponseDTO.success(marketDataFoundationService.importTradeCalendar(request));
    }

    /**
     * 导入股票复权因子。
     *
     * @param request 复权因子批次
     * @return 导入统计
     */
    @PostMapping("/adjustment-factors/import")
    public ResponseDTO<MarketDataImportResultDto> importAdjustmentFactors(
            @Valid @RequestBody AdjustmentFactorImportRequestDto request) {
        log.info("[MarketDataFoundation] 导入复权因子: source={}, version={}, count={}",
                request.getSource(), request.getSourceVersion(), request.getItems().size());
        return ResponseDTO.success(marketDataFoundationService.importAdjustmentFactors(request));
    }

    /**
     * 导入股票 5 分钟 K 线。
     *
     * @param request 5 分钟行情批次
     * @return 导入统计
     */
    @PostMapping("/minute-bars/import")
    public ResponseDTO<MarketDataImportResultDto> importMinuteBars(
            @Valid @RequestBody MinuteBarImportRequestDto request) {
        log.info("[MarketDataFoundation] 导入 5 分钟 K 线: source={}, version={}, count={}, volumeUnit={}",
                request.getSource(), request.getSourceVersion(), request.getItems().size(),
                request.getVolumeUnit());
        return ResponseDTO.success(marketDataFoundationService.importMinuteBars(request));
    }

    /**
     * 导入模型特征专用的版本化股票日线。
     *
     * @param request 股票日线批次
     * @return 导入统计
     */
    @PostMapping("/model-stock-daily-bars/import")
    public ResponseDTO<MarketDataImportResultDto> importModelStockDailyBars(
            @Valid @RequestBody ModelStockDailyBarImportRequestDto request) {
        log.info("[MarketDataFoundation] 导入模型股票日线: source={}, version={}, count={}, volumeUnit={}",
                request.getSource(), request.getSourceVersion(), request.getItems().size(),
                request.getVolumeUnit());
        return ResponseDTO.success(marketDataFoundationService.importModelStockDailyBars(request));
    }

    /**
     * 导入模型特征使用的市场或行业参考日线。
     *
     * @param request 参考序列日线批次
     * @return 导入统计
     */
    @PostMapping("/model-reference-daily-bars/import")
    public ResponseDTO<MarketDataImportResultDto> importModelReferenceDailyBars(
            @Valid @RequestBody ModelReferenceDailyBarImportRequestDto request) {
        log.info("[MarketDataFoundation] 导入模型参考日线: type={}, source={}, version={}, count={}",
                request.getSeriesType(), request.getSource(), request.getSourceVersion(),
                request.getItems().size());
        return ResponseDTO.success(marketDataFoundationService.importModelReferenceDailyBars(request));
    }

    /**
     * 导入股票单日开盘可成交性事实。
     *
     * @param request 可成交性事实批次
     * @return 导入统计
     */
    @PostMapping("/stock-daily-tradability/import")
    public ResponseDTO<MarketDataImportResultDto> importStockDailyTradability(
            @Valid @RequestBody StockDailyTradabilityImportRequestDto request) {
        log.info("[MarketDataFoundation] 导入股票开盘可成交性: source={}, version={}, count={}",
                request.getSource(), request.getSourceVersion(), request.getItems().size());
        return ResponseDTO.success(marketDataFoundationService.importStockDailyTradability(request));
    }
}
// AI_GENERATE_END ----
