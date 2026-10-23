// AI_GENERATE_START------------
package com.stock.data.controller;

import com.stock.base.api.ResponseDTO;
import com.stock.data.dto.KlineDataResponseDto;
import com.stock.data.dto.StockDataValidationResponseDto;
import com.stock.data.dto.StockInfoItemDto;
import com.stock.data.dto.StockListPageResponseDto;
import com.stock.data.dto.StockListSyncResult;
import com.stock.data.dto.StockStatisticsResponseDto;
import com.stock.data.service.StockListSyncService;
import com.stock.data.service.StockPriceService;
import com.stock.data.service.StockQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 股票数据管理控制器。
 *
 * 提供股票列表同步、数据统计、完整性校验和 K 线查询接口。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RestController
@RequestMapping("/api/stockData")
@RequiredArgsConstructor
public class StockDataController {

    private final StockListSyncService stockListSyncService;
    private final StockQueryService stockQueryService;
    private final StockPriceService stockPriceService;

    /**
     * 手动触发股票列表全量同步。
     *
     * @return 股票列表同步统计
     */
    @PostMapping("/sync")
    public ResponseDTO<StockListSyncResult> syncStockList() {
        log.info("[StockData] 手动触发股票列表同步");
        try {
            return ResponseDTO.success(stockListSyncService.syncStockList());
        } catch (Exception exception) {
            log.error("[StockData] 股票列表同步失败", exception);
            return ResponseDTO.error("股票列表同步失败: " + exception.getMessage());
        }
    }

    /**
     * 查询股票数据字段完整率和市场分布。
     *
     * @return 股票数据统计
     */
    @GetMapping("/statistics")
    public ResponseDTO<StockStatisticsResponseDto> getStatistics() {
        log.info("[StockData] 获取数据统计");
        return ResponseDTO.success(stockQueryService.getStatistics());
    }

    /**
     * 分页查询股票数据管理列表。
     *
     * @param page 当前页码，从 1 开始
     * @param size 每页记录数
     * @return 股票分页数据
     */
    @GetMapping("/list")
    public ResponseDTO<StockListPageResponseDto> getStockList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("[StockData] 分页查询股票列表 | page={}, size={}", page, size);
        return ResponseDTO.success(stockQueryService.queryStockDataPage(page, size));
    }

    /**
     * 根据股票代码查询基础信息。
     *
     * @param code 股票代码
     * @return 股票信息；不存在时返回失败响应
     */
    @GetMapping("/code/{code}")
    public ResponseDTO<StockInfoItemDto> getStockByCode(@PathVariable String code) {
        log.info("[StockData] 按代码查询股票 | code={}", code);
        return stockQueryService.findByCode(code)
                .map(ResponseDTO::success)
                .orElseGet(() -> ResponseDTO.error("未找到股票: " + code));
    }

    /**
     * 校验股票数据关键字段完整性。
     *
     * @return 股票数据校验结果
     */
    @GetMapping("/validate")
    public ResponseDTO<StockDataValidationResponseDto> validateData() {
        log.info("[StockData] 验证数据完整性");
        return ResponseDTO.success(stockQueryService.validateData());
    }

    /**
     * 查询指定股票的历史 K 线数据。
     *
     * @param code 股票代码
     * @param type K 线类型，当前仅支持 daily
     * @param startDate 开始日期，格式 yyyy-MM-dd，可为空
     * @param endDate 结束日期，格式 yyyy-MM-dd，可为空
     * @param timeRange 预设时间范围，可为空
     * @param limit 最大返回记录数
     * @return K 线业务数据
     */
    @GetMapping("/kline/{code}")
    public ResponseDTO<KlineDataResponseDto> getKlineData(
            @PathVariable String code,
            @RequestParam(defaultValue = "daily") String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String timeRange,
            @RequestParam(defaultValue = "500") int limit) {
        log.info("[StockData] 获取K线 | code={}, type={}, startDate={}, endDate={}, timeRange={}, limit={}",
                code, type, startDate, endDate, timeRange, limit);
        if (!"daily".equals(type)) {
            return ResponseDTO.error("当前仅支持 daily K 线");
        }
        try {
            KlineDataResponseDto result = stockPriceService.getKlineData(
                    code, parseDate(startDate), parseDate(endDate), timeRange, limit);
            return ResponseDTO.success(result);
        } catch (Exception exception) {
            log.error("[StockData] 获取K线失败 | code={}", code, exception);
            return ResponseDTO.error("获取K线失败: " + exception.getMessage());
        }
    }

    private LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
}
// AI_GENERATE_END------------
