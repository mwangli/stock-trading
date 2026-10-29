// AI_GENERATE_START-----------
package com.stock.data.service;

import com.stock.base.cache.CacheNames;
import com.stock.data.dto.KlineDataPayloadDto;
import com.stock.data.dto.KlineDataResponseDto;
import com.stock.data.dto.StockPriceItemDto;
import com.stock.data.entity.StockPrice;
import com.stock.data.repository.PriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 股票日线行情服务。
 *
 * 负责从统一日线事实集合查询历史价格、最新价格和 K 线载荷。
 *
 * @author mwangli
 * @since 2026-10-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    /** K 线接口允许返回的最大记录数。 */
    private static final int MAX_KLINE_LIMIT = 5000;

    private final PriceRepository priceRepository;

    /**
     * 查询统一日线事实库中的股票历史价格。
     *
     * @param stockCode 股票代码
     * @return 历史日线列表
     */
    public List<StockPriceItemDto> getHistoryPriceItems(String stockCode) {
        return priceRepository.findByCodeOrderByDateAsc(stockCode).stream()
                .map(this::toPriceItemDto)
                .toList();
    }

    /**
     * 查询 K 线图表业务数据。
     *
     * @param code 股票代码
     * @param startDate 显式开始日期，可为空
     * @param endDate 显式结束日期，可为空
     * @param timeRange 预设时间范围，可为空
     * @param limit 最大返回记录数
     * @return K 线图表业务数据
     */
    public KlineDataResponseDto getKlineData(String code,
                                             LocalDate startDate,
                                             LocalDate endDate,
                                             String timeRange,
                                             int limit) {
        DateRange range = resolveDateRange(startDate, endDate, timeRange);
        int safeLimit = Math.max(1, Math.min(limit, MAX_KLINE_LIMIT));
        List<StockPrice> prices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(
                code, range.startDate(), range.endDate());
        if (prices.size() > safeLimit) {
            prices = prices.subList(prices.size() - safeLimit, prices.size());
        }
        List<StockPrice> validPrices = prices.stream()
                .filter(price -> price.getDate() != null)
                .filter(price -> price.getOpenPrice() != null)
                .filter(price -> price.getClosePrice() != null)
                .filter(price -> price.getLowPrice() != null)
                .filter(price -> price.getHighPrice() != null)
                .toList();
        KlineDataPayloadDto payload = KlineDataPayloadDto.builder()
                .dates(validPrices.stream().map(price -> price.getDate().toString()).toList())
                .kline(validPrices.stream().map(price -> List.of(
                        price.getOpenPrice(), price.getClosePrice(),
                        price.getLowPrice(), price.getHighPrice())).toList())
                .volumes(validPrices.stream()
                        .map(price -> price.getVolume() == null ? BigDecimal.ZERO : price.getVolume())
                        .toList())
                .build();
        return KlineDataResponseDto.builder().payload(payload).total(validPrices.size()).build();
    }

    /**
     * 查询本地行情库中的最新日线。
     *
     * @param stockCode 股票代码
     * @return 最新日线，不存在时返回 null
     */
    @Cacheable(cacheNames = CacheNames.LATEST_PRICE, key = "#stockCode", unless = "#result == null")
    public StockPrice getLatestPrice(String stockCode) {
        return priceRepository.findTopByCodeOrderByDateDesc(stockCode).orElse(null);
    }

    private StockPriceItemDto toPriceItemDto(StockPrice price) {
        return StockPriceItemDto.builder()
                .code(price.getCode())
                .stockName(price.getStockName())
                .date(price.getDate())
                .openPrice(price.getOpenPrice())
                .highPrice(price.getHighPrice())
                .lowPrice(price.getLowPrice())
                .closePrice(price.getClosePrice())
                .volume(price.getVolume())
                .amount(price.getAmount())
                .updateTime(price.getUpdateTime())
                .build();
    }

    private DateRange resolveDateRange(LocalDate startDate, LocalDate endDate, String timeRange) {
        if (startDate != null || endDate != null) {
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException("开始日期和结束日期必须同时提供");
            }
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("开始日期不能晚于结束日期");
            }
            return new DateRange(startDate, endDate);
        }
        LocalDate resolvedEndDate = LocalDate.now();
        LocalDate resolvedStartDate = switch (timeRange == null ? "" : timeRange) {
            case "last1Week" -> resolvedEndDate.minusWeeks(1);
            case "last3Months" -> resolvedEndDate.minusMonths(3);
            case "last1Year" -> resolvedEndDate.minusYears(1);
            case "last3Years" -> resolvedEndDate.minusYears(3);
            case "last5Years" -> resolvedEndDate.minusYears(5);
            default -> resolvedEndDate.minusMonths(1);
        };
        return new DateRange(resolvedStartDate, resolvedEndDate);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
// AI_GENERATE_END-----------
