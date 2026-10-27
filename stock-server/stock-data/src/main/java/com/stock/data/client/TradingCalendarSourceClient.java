// AI_GENERATE_START-----
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
 * A 股公开休市日历客户端。
 * 从公开数据中心读取 A 股休市区间，用于构建可持续滚动更新的交易日历。
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
     * 获取当前公开的 A 股休市区间快照。
     *
     * @return 休市区间、已覆盖年份和来源版本
     */
    public CalendarSourceSnapshot fetchAStockClosures() {
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
        LocalDateTime fetchedAt = LocalDateTime.now(MARKET_ZONE);
        String sourceVersion = "eastmoney-" + (latestPublishedDate == null
                ? fetchedAt.toLocalDate() : latestPublishedDate);
        log.info("公开休市日历获取成功: closureRanges={}, coveredYears={}, latestPublishedDate={}",
                closures.size(), coveredYears, latestPublishedDate);
        return new CalendarSourceSnapshot(closures, coveredYears, sourceVersion, fetchedAt);
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
     * @param sourceVersion 来源版本
     * @param fetchedAt 抓取时间
     */
    public record CalendarSourceSnapshot(List<ClosureRange> closures, Set<Integer> coveredYears,
                                         String sourceVersion, LocalDateTime fetchedAt) {

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
// AI_GENERATE_END-----
