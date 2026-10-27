// AI_GENERATE_START-------
package com.stock.data.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A 股公开交易日历客户端。
 * 使用上证指数历史日 K 确认历史开市日，并使用公开休市公告维护当前和未来日历。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Slf4j
@Component
public class TradingCalendarSourceClient {

    private static final String SOURCE_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get"
            + "?reportName=RPTA_WEB_ZGXSRL&columns=ALL&pageSize=500"
            + "&sortColumns=SDATE&sortTypes=-1";
    private static final String HISTORICAL_INDEX_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
            + "?secid=1.000001&klt=101&fqt=0&lmt=1000"
            + "&beg={beginDate}&end={endDate}"
            + "&fields1=f1,f2,f3,f4,f5,f6&fields2=f51";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter SOURCE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;

    /**
     * 创建公开休市日历客户端。
     *
     * @param restTemplate HTTP 客户端
     */
    public TradingCalendarSourceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 获取历史指数交易日期和当前公开休市安排组成的 A 股日历快照。
     *
     * @param historicalStartDate 历史确认区间开始日期
     * @param historicalEndDate 历史确认区间结束日期
     * @return 历史交易日期、休市区间、已覆盖年份和来源版本
     */
    public CalendarSourceSnapshot fetchAStockCalendar(
            LocalDate historicalStartDate, LocalDate historicalEndDate) {
        if (historicalStartDate == null || historicalEndDate == null
                || historicalStartDate.isAfter(historicalEndDate)) {
            throw new IllegalArgumentException("历史交易日历查询范围无效");
        }
        String responseBody = restTemplate.getForObject(SOURCE_URL, String.class);
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("公开休市日历响应为空");
        }
        JSONObject root = JSONObject.parseObject(responseBody);
        JSONObject result = root.getJSONObject("result");
        JSONArray data = result == null ? null : result.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("公开休市日历未返回有效数据");
        }

        List<ClosureRange> closures = new ArrayList<>();
        Set<Integer> coveredYears = new HashSet<>();
        LocalDate latestPublishedDate = null;
        for (int index = 0; index < data.size(); index++) {
            JSONObject item = data.getJSONObject(index);
            if (item == null || !"A股".equals(item.getString("MKT"))) {
                continue;
            }
            LocalDate startDate = parseSourceDate(item.getString("SDATE"));
            LocalDate endDate = parseSourceDate(item.getString("EDATE"));
            if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
                continue;
            }
            String holidayName = item.getString("HOLIDAY");
            closures.add(new ClosureRange(startDate, endDate,
                    holidayName == null || holidayName.isBlank() ? "节假日" : holidayName.trim()));
            for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
                coveredYears.add(year);
            }
            if (latestPublishedDate == null || endDate.isAfter(latestPublishedDate)) {
                latestPublishedDate = endDate;
            }
        }
        if (closures.isEmpty()) {
            throw new IllegalStateException("公开休市日历未包含 A 股数据");
        }
        Set<LocalDate> historicalTradingDates = fetchHistoricalTradingDates(
                historicalStartDate, historicalEndDate);
        LocalDateTime fetchedAt = LocalDateTime.now(MARKET_ZONE);
        String closureVersion = latestPublishedDate == null
                ? fetchedAt.toLocalDate().toString() : latestPublishedDate.toString();
        String sourceVersion = "eastmoney-index-" + historicalEndDate + "-closure-" + closureVersion;
        log.info("公开交易日历获取成功: historicalRange={}~{}, historicalTradingDays={}, "
                        + "closureRanges={}, coveredYears={}, latestPublishedDate={}",
                historicalStartDate, historicalEndDate, historicalTradingDates.size(),
                closures.size(), coveredYears, latestPublishedDate);
        return new CalendarSourceSnapshot(closures, coveredYears, historicalTradingDates,
                historicalStartDate, historicalEndDate, sourceVersion, fetchedAt);
    }

    private Set<LocalDate> fetchHistoricalTradingDates(LocalDate startDate, LocalDate endDate) {
        String responseBody = restTemplate.getForObject(HISTORICAL_INDEX_URL, String.class,
                startDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                endDate.format(DateTimeFormatter.BASIC_ISO_DATE));
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("上证指数历史日线响应为空");
        }
        JSONObject root = JSONObject.parseObject(responseBody);
        JSONObject data = root.getJSONObject("data");
        JSONArray klines = data == null ? null : data.getJSONArray("klines");
        if (klines == null || klines.isEmpty()) {
            throw new IllegalStateException("上证指数历史日线未返回交易日期");
        }
        if (klines.size() >= 1000) {
            throw new IllegalStateException("上证指数历史日线达到返回上限，无法确认区间完整性");
        }
        Set<LocalDate> tradingDates = new HashSet<>();
        for (int index = 0; index < klines.size(); index++) {
            String value = klines.getString(index);
            LocalDate date = parseSourceDate(value);
            if (date != null && !date.isBefore(startDate) && !date.isAfter(endDate)) {
                tradingDates.add(date);
            }
        }
        if (tradingDates.isEmpty()) {
            throw new IllegalStateException("上证指数历史日线未解析出有效交易日期");
        }
        return Set.copyOf(tradingDates);
    }

    private LocalDate parseSourceDate(String value) {
        if (value == null || value.length() < 10) {
            return null;
        }
        return LocalDate.parse(value.substring(0, 10), SOURCE_DATE_FORMAT);
    }

    /**
     * 公开休市日历快照。
     *
     * @param closures A 股休市区间
     * @param coveredYears 已发布休市安排的年份
     * @param historicalTradingDates 历史区间内上证指数实际交易日期
     * @param historicalStartDate 历史确认区间开始日期
     * @param historicalEndDate 历史确认区间结束日期
     * @param sourceVersion 来源版本
     * @param fetchedAt 抓取时间
     */
    public record CalendarSourceSnapshot(List<ClosureRange> closures, Set<Integer> coveredYears,
                                         Set<LocalDate> historicalTradingDates,
                                         LocalDate historicalStartDate, LocalDate historicalEndDate,
                                         String sourceVersion, LocalDateTime fetchedAt) {

        /**
         * 判断日期是否位于指数日线已确认的历史范围内。
         *
         * @param date 自然日期
         * @return 位于历史确认范围时返回 true
         */
        public boolean coversHistoricalDate(LocalDate date) {
            return date != null
                    && !date.isBefore(historicalStartDate)
                    && !date.isAfter(historicalEndDate);
        }

        /**
         * 判断历史日期是否为上证指数实际交易日。
         *
         * @param date 自然日期
         * @return 存在指数日 K 时返回 true
         */
        public boolean isHistoricalTradingDay(LocalDate date) {
            return historicalTradingDates.contains(date);
        }

        /**
         * 判断指定年份是否已有公开休市安排。
         *
         * @param year 年份
         * @return 是否已覆盖
         */
        public boolean coversYear(int year) {
            return coveredYears.contains(year);
        }

        /**
         * 查询指定日期的节假日名称。
         *
         * @param date 自然日期
         * @return 节假日名称，不休市时为空
         */
        public String holidayName(LocalDate date) {
            return closures.stream()
                    .filter(range -> !date.isBefore(range.startDate()) && !date.isAfter(range.endDate()))
                    .map(ClosureRange::holidayName)
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * 单个休市日期区间。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param holidayName 节假日名称
     */
    public record ClosureRange(LocalDate startDate, LocalDate endDate, String holidayName) {
    }
}
// AI_GENERATE_END-------
