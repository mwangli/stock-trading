// AI_GENERATE_START ---------------
package com.stock.console.market;

import com.stock.console.config.ConsoleProperties;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
public class MarketRepository {

    private static final Pattern CODE = Pattern.compile("(?i)(sh|sz)[.]\\d{6}");
    private static final String BASIC_RELATIVE = "raw/stock/stocks.parquet";
    private static final String LEGACY_BASIC_RELATIVE = "basic/stocks.parquet";
    private static final String DAILY_PATTERN = "raw/daily/code=*/bars.parquet";
    private static final String LEGACY_DAILY_PATTERN = "daily/code=*/bars.parquet";
    private static final String BASIC_HISTORY_PATTERN = "derived/basic/code=*/indicators.parquet";
    private static final String LEGACY_DAILY_BASIC_PATTERN = "daily_basic/trade_date=*/indicators.parquet";
    private static final String TRADING_CALENDAR_PATTERN = DAILY_PATTERN;
    private final ConsoleProperties properties;

    public MarketRepository(ConsoleProperties properties) {
        this.properties = properties;
    }

    /**
     * 返回当前基础信息和最新日线分区的可用状态。
     *
     * @return 行情数据源状态
     */
    public Map<String, Object> status() {
        String instruments = basicSource();
        if (instruments == null) {
            return Map.of(
                    "available", false,
                    "reason", "股票目录文件不存在",
                    "path", instruments);
        }
        String sql = "SELECT count(*) AS instruments, max(snapshot_date) AS as_of_date FROM read_parquet(?)";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instruments);
            List<Map<String, Object>> values = rows(statement);
            Map<String, Object> summary = values.isEmpty() ? Map.of() : values.get(0);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", true);
            result.put("engine", "DuckDB");
            result.put("mode", "READ_ONLY");
            result.put("path", instruments);
            result.put("instruments", summary.getOrDefault("instruments", 0));
            result.put("asOfDate", summary.get("as_of_date"));
            result.put("latestDailyDate", latestDailyDate().orElse(null));
            return result;
        } catch (Exception exception) {
            return Map.of(
                    "available", false,
                    "reason", safeMessage(exception),
                    "path", instruments);
        }
    }

    /**
     * 查询股票基础信息、最新行情和日级估值指标。
     *
     * @param keyword 股票代码或名称关键字
     * @param limit 最大返回数量
     * @return 股票详情列表
     */
    public List<Map<String, Object>> instruments(String keyword, int limit) {
        String instruments = basicSource();
        Optional<String> latest = latestDailyDate();
        if (instruments == null || latest.isEmpty()) {
            return List.of();
        }
        String daily = dailySource();
        String dailyBasic = basicHistorySource();
        if (daily == null || dailyBasic == null) {
            return List.of();
        }
        String sql = """
                SELECT instruments.snapshot_date AS as_of_date,
                       instruments.ipo_date AS listed_date,
                       instruments.code,
                       instruments.code_name AS name,
                       CASE WHEN lower(instruments.code) LIKE 'sh.%' THEN 'SH' ELSE 'SZ' END AS market,
                       instruments.market_category,
                       instruments.trade_status,
                       instruments.security_type,
                       instruments.status,
                       bars.trade_date AS update_time,
                       bars.close AS price,
                       bars.close - bars.preclose AS change_amount,
                       CASE WHEN bars.preclose IS NULL OR bars.preclose = 0 THEN NULL
                            ELSE (bars.close - bars.preclose) / bars.preclose * 100 END AS change_percent,
                       bars.volume,
                       bars.amount,
                       indicators.turnover_rate,
                       indicators.volume_ratio,
                       NULL AS total_market_value,
                       indicators.circulating_market_value
                FROM read_parquet(?) AS instruments
                INNER JOIN read_parquet(?, hive_partitioning = true) AS bars
                    ON bars.code = instruments.code AND CAST(bars.trade_date AS DATE) = CAST(? AS DATE)
                LEFT JOIN read_parquet(?, hive_partitioning = true) AS indicators
                    ON indicators.code = instruments.code AND CAST(indicators.trade_date AS DATE) = CAST(? AS DATE)
                WHERE instruments.security_type = '1'
                  AND coalesce(instruments.status, '') IN ('', '1')
                  AND instruments.trade_status = '1'
                  AND (? = '' OR instruments.code ILIKE ? OR instruments.code_name ILIKE ?)
                ORDER BY instruments.code
                LIMIT ?
                """;
        String value = keyword == null ? "" : keyword.trim();
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instruments);
            statement.setString(2, daily);
            statement.setString(3, latest.get());
            statement.setString(4, dailyBasic);
            statement.setString(5, latest.get());
            statement.setString(6, value);
            statement.setString(7, "%" + value + "%");
            statement.setString(8, "%" + value + "%");
            statement.setInt(9, Math.max(1, Math.min(limit, 500)));
            return rows(statement);
        } catch (Exception exception) {
            return List.of();
        }
    }

    /**
     * 分页查询当前可交易股票，并在接口结果中补充成交量和成交额的万亿单位展示值。
     *
     * @param keyword 股票代码或名称关键字
     * @param page 页码，从一开始
     * @param pageSize 每页数量
     * @return 包含原始数值和格式化展示值的股票分页数据
     */
    public Map<String, Object> activeInstruments(String keyword, int page, int pageSize) {
        String instruments = basicSource();
        Optional<String> latest = latestDailyDate();
        if (instruments == null || latest.isEmpty()) {
            return emptyInstrumentPage(page, pageSize, null);
        }
        String daily = dailySource();
        String dailyBasic = basicHistorySource();
        if (daily == null || dailyBasic == null) {
            return emptyInstrumentPage(page, pageSize, latest.get());
        }

        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = Math.max(1, Math.min(pageSize, 100));
        int offset = (normalizedPage - 1) * normalizedPageSize;
        String value = keyword == null ? "" : keyword.trim();
        String filter = """
                FROM read_parquet(?) AS instruments
                INNER JOIN read_parquet(?, hive_partitioning = true) AS bars
                    ON bars.code = instruments.code AND CAST(bars.trade_date AS DATE) = CAST(? AS DATE)
                LEFT JOIN read_parquet(?, hive_partitioning = true) AS indicators
                    ON indicators.code = instruments.code AND CAST(indicators.trade_date AS DATE) = CAST(? AS DATE)
                WHERE instruments.security_type = '1'
                  AND coalesce(instruments.status, '') IN ('', '1')
                  AND instruments.trade_status = '1'
                  AND (? = '' OR instruments.code ILIKE ? OR instruments.code_name ILIKE ?)
                """;
        String countSql = "SELECT count(*) AS total " + filter;
        String dataSql = """
                SELECT instruments.snapshot_date AS as_of_date,
                       instruments.ipo_date AS listed_date,
                       instruments.code,
                       instruments.code_name AS name,
                       CASE WHEN lower(instruments.code) LIKE 'sh.%' THEN 'SH' ELSE 'SZ' END AS market,
                       instruments.market_category,
                       bars.trade_date AS update_time, bars.close AS price,
                       bars.close - bars.preclose AS change_amount,
                       CASE WHEN bars.preclose IS NULL OR bars.preclose = 0 THEN NULL
                            ELSE (bars.close - bars.preclose) / bars.preclose * 100 END AS change_percent,
                       bars.volume,
                       bars.amount,
                       CASE WHEN bars.volume IS NULL THEN '-'
                            WHEN abs(bars.volume) >= 100000000 THEN printf('%.2f亿', bars.volume / 100000000.0)
                            WHEN abs(bars.volume) >= 10000 THEN printf('%.2f万', bars.volume / 10000.0)
                            ELSE printf('%.0f', bars.volume) END AS volume_display,
                       CASE WHEN bars.amount IS NULL THEN '-'
                            WHEN abs(bars.amount) >= 100000000 THEN printf('¥%.2f亿', bars.amount / 100000000.0)
                            WHEN abs(bars.amount) >= 10000 THEN printf('¥%.2f万', bars.amount / 10000.0)
                            ELSE printf('¥%.2f', bars.amount) END AS amount_display,
                       indicators.turnover_rate,
                       indicators.volume_ratio,
                       NULL AS total_market_value,
                       indicators.circulating_market_value
                """ + filter + " ORDER BY instruments.code LIMIT ? OFFSET ?";

        try (Connection connection = open();
             PreparedStatement countStatement = connection.prepareStatement(countSql);
             PreparedStatement dataStatement = connection.prepareStatement(dataSql)) {
            bindInstrumentFilter(countStatement, instruments, daily, latest.get(), dailyBasic, latest.get(), value);
            List<Map<String, Object>> countRows = rows(countStatement);
            long total = countRows.isEmpty()
                    ? 0
                    : ((Number) countRows.get(0).getOrDefault("total", 0)).longValue();

            bindInstrumentFilter(dataStatement, instruments, daily, latest.get(), dailyBasic, latest.get(), value);
            dataStatement.setInt(9, normalizedPageSize);
            dataStatement.setInt(10, offset);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("items", rows(dataStatement));
            result.put("total", total);
            result.put("page", normalizedPage);
            result.put("pageSize", normalizedPageSize);
            result.put("asOfDate", latest.get());
            return result;
        } catch (Exception exception) {
            return emptyInstrumentPage(normalizedPage, normalizedPageSize, latest.get());
        }
    }

    /**
     * 读取指定股票的日线历史。
     *
     * @param code 股票代码
     * @param limit 最大返回数量
     * @return 按日期倒序的日线
     */
    public List<Map<String, Object>> dailyBars(String code, int limit) {
        String normalizedCode = requireCode(code);
        String relative = "raw/daily/code=" + normalizedCode + "/bars.parquet";
        return readBars(relative, "trade_date", limit, 1000);
    }

    /**
     * 读取指定股票的五分钟行情。
     *
     * @param code 股票代码
     * @param limit 最大返回数量
     * @return 按时间倒序的五分钟行情
     */
    public List<Map<String, Object>> minuteBars(String code, int limit) {
        String normalizedCode = requireCode(code);
        String relative = "raw/minute5/code=" + normalizedCode + "/bars.parquet";
        return readBars(relative, "bar_end", limit, 2000);
    }

    /**
     * 从当前日线历史中查询交易日历。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 升序交易日列表
     */
    public List<LocalDate> tradingDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
                || dailySource() == null) {
            return List.of();
        }
        String sql = """
                SELECT DISTINCT CAST(trade_date AS DATE) AS trade_date
                FROM read_parquet(?, hive_partitioning = true)
                WHERE CAST(trade_date AS DATE) BETWEEN CAST(? AS DATE) AND CAST(? AS DATE)
                ORDER BY trade_date
                """;
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, marketSource(TRADING_CALENDAR_PATTERN));
            statement.setString(2, startDate.toString());
            statement.setString(3, endDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LocalDate> dates = new ArrayList<>();
                while (resultSet.next()) {
                    dates.add(LocalDate.parse(resultSet.getString("trade_date")));
                }
                return dates;
            }
        } catch (Exception exception) {
            return List.of();
        }
    }

    public Map<String, Object> statistics() {
        Optional<String> latest = latestDailyDate();
        if (latest.isEmpty()) {
            return Map.of("available", false, "reason", "全市场日线分区尚未生成");
        }
        String source = dailySource();
        String sql = """
                SELECT count(*) AS total,
                       sum(CASE WHEN close > preclose THEN 1 ELSE 0 END) AS advances,
                       sum(CASE WHEN close < preclose THEN 1 ELSE 0 END) AS declines,
                       sum(CASE WHEN close = preclose THEN 1 ELSE 0 END) AS unchanged,
                       coalesce(sum(amount), 0) AS total_amount,
                       coalesce(sum(volume), 0) AS total_volume
                FROM read_parquet(?, hive_partitioning = true, union_by_name = true)
                WHERE CAST(trade_date AS DATE) = CAST(? AS DATE)
                """;
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source);
            statement.setString(2, latest.get());
            List<Map<String, Object>> values = rows(statement);
            Map<String, Object> result = new LinkedHashMap<>(values.isEmpty() ? Map.of() : values.get(0));
            result.put("available", true);
            result.put("asOfDate", latest.get());
            return result;
        } catch (Exception exception) {
            return Map.of(
                    "available", false,
                    "reason", "市场统计读取失败",
                    "asOfDate", latest.get());
        }
    }

    /**
     * 读取最新交易日的涨跌幅排名。
     *
     * @param limit 最大返回数量
     * @return 最新日线排名
     */
    public List<Map<String, Object>> latestDailyRanking(int limit) {
        String instruments = basicSource();
        if (instruments == null) {
            return List.of();
        }

        Optional<String> latest = latestDailyDate();
        String dailySource = dailySource();
        if (latest.isEmpty() || dailySource == null) return List.of();

        String sql = """
                WITH bars AS MATERIALIZED (
                    SELECT trade_date, code, close, preclose
                    FROM read_parquet(?, hive_partitioning = true)
                    WHERE CAST(trade_date AS DATE) = CAST(? AS DATE)
                      AND preclose IS NOT NULL AND preclose <> 0
                ),
                latest_date AS (
                    SELECT max(trade_date) AS trade_date
                    FROM bars
                )
                SELECT bars.trade_date AS period,
                       bars.code AS "stockCode",
                       instruments.code_name AS "stockName",
                       bars.close AS close,
                       bars.preclose AS preclose,
                       (bars.close - bars.preclose) / bars.preclose AS "returnRate"
                FROM bars
                INNER JOIN latest_date ON bars.trade_date = latest_date.trade_date
                LEFT JOIN read_parquet(?) AS instruments ON instruments.code = bars.code
                ORDER BY "returnRate" DESC, bars.code
                LIMIT ?
                """;
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            statement.setString(parameter++, dailySource);
            statement.setString(parameter++, latest.get());
            statement.setString(parameter++, instruments);
            statement.setInt(parameter, Math.max(1, Math.min(limit, 100)));
            return rows(statement);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readBars(
            String relativePath, String orderColumn, int limit, int maximum) {
        if (!marketAvailable(relativePath)) {
            return List.of();
        }
        String source = marketSource(relativePath);
        String sql = "SELECT * FROM read_parquet(?) ORDER BY " + orderColumn + " DESC LIMIT ?";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source);
            statement.setInt(2, Math.max(1, Math.min(limit, maximum)));
            return rows(statement);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Optional<String> latestDailyDate() {
        String source = dailySource();
        if (source == null) {
            return Optional.empty();
        }
        String sql = "SELECT max(CAST(trade_date AS DATE)) AS latest_date FROM read_parquet(?, hive_partitioning = true)";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source);
            List<Map<String, Object>> values = rows(statement);
            Object latest = values.isEmpty() ? null : values.get(0).get("latest_date");
            return latest == null ? Optional.empty() : Optional.of(latest.toString());
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private String requireCode(String code) {
        String normalizedCode = code == null ? "" : code.trim().toLowerCase();
        if (!CODE.matcher(normalizedCode).matches()) {
            throw new IllegalArgumentException("股票代码格式错误");
        }
        return normalizedCode;
    }

    private Connection open() throws Exception {
        return DriverManager.getConnection("jdbc:duckdb:");
    }

    private String marketSource(String relativePath) {
        return properties.marketDir().resolve(relativePath)
                .toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    /** 返回当前股票基础信息快照，兼容尚未迁移的旧单文件路径。 */
    private String basicSource() {
        if (marketAvailable(BASIC_RELATIVE)) return marketSource(BASIC_RELATIVE);
        if (marketAvailable(LEGACY_BASIC_RELATIVE)) return marketSource(LEGACY_BASIC_RELATIVE);
        return null;
    }

    /** 返回按股票分区的当前日线数据通配路径。 */
    private String dailySource() {
        if (marketDirectoryAvailable("raw/daily")) return marketSource(DAILY_PATTERN);
        if (marketDirectoryAvailable("daily")) return marketSource(LEGACY_DAILY_PATTERN);
        return null;
    }

    /** 返回按股票分区的当前每日指标数据通配路径。 */
    private String basicHistorySource() {
        if (marketDirectoryAvailable("derived/basic")) return marketSource(BASIC_HISTORY_PATTERN);
        if (marketDirectoryAvailable("daily_basic")) return marketSource(LEGACY_DAILY_BASIC_PATTERN);
        return null;
    }

    /** 判断路径是否为包含 parquet 分区的目录。 */
    private boolean marketDirectoryAvailable(String relativePath) {
        return Files.isDirectory(properties.marketDir().resolve(relativePath));
    }

    private boolean marketAvailable(String relativePath) {
        return Files.isRegularFile(properties.marketDir().resolve(relativePath));
    }

    private List<Map<String, Object>> rows(PreparedStatement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery()) {
            List<Map<String, Object>> result = new ArrayList<>();
            int columns = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 1; index <= columns; index++) {
                    row.put(resultSet.getMetaData().getColumnLabel(index), resultSet.getObject(index));
                }
                result.add(row);
            }
            return result;
        }
    }

    /**
     * 绑定基础信息、日线、每日指标和股票关键字查询参数。
     *
     * @param statement 查询语句
     * @param instruments 基础信息文件
     * @param daily 日线横截面文件
     * @param dailyDate 日线查询日期
     * @param dailyBasic 每日指标文件
     * @param basicDate 每日指标查询日期
     * @param keyword 股票关键字
     * @throws Exception 参数绑定失败
     */
    private void bindInstrumentFilter(
            PreparedStatement statement, String instruments, String daily,
            String dailyDate, String dailyBasic, String basicDate,
            String keyword) throws Exception {
        statement.setString(1, instruments);
        statement.setString(2, daily);
        statement.setString(3, dailyDate);
        statement.setString(4, dailyBasic);
        statement.setString(5, basicDate);
        statement.setString(6, keyword);
        statement.setString(7, "%" + keyword + "%");
        statement.setString(8, "%" + keyword + "%");
    }

    private Map<String, Object> emptyInstrumentPage(int page, int pageSize, String asOfDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", List.of());
        result.put("total", 0);
        result.put("page", Math.max(1, page));
        result.put("pageSize", Math.max(1, Math.min(pageSize, 100)));
        result.put("asOfDate", asOfDate);
        return result;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "行情数据源无法读取" : message;
    }
}
// AI_GENERATE_END ---------------
