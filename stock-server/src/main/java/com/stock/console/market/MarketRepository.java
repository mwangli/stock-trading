// AI_GENERATE_START ---------
package com.stock.console.market;

import com.stock.console.config.ConsoleProperties;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
public class MarketRepository {

    private static final Pattern CODE = Pattern.compile("(?i)(sh|sz)[.]\\d{6}");
    private final ConsoleProperties properties;

    public MarketRepository(ConsoleProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> status() {
        String instruments = marketSource("instruments/stocks.parquet");
        if (!marketAvailable("instruments/stocks.parquet")) {
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

    public List<Map<String, Object>> instruments(String keyword, int limit) {
        String instruments = marketSource("instruments/stocks.parquet");
        Optional<String> latest = latestDailyDate();
        if (!marketAvailable("instruments/stocks.parquet") || latest.isEmpty()) {
            return List.of();
        }
        String dailyRelative = "daily/trade_date=" + latest.get() + "/bars.parquet";
        if (!marketAvailable(dailyRelative)) {
            return List.of();
        }
        String daily = marketSource(dailyRelative);
        String sql = """
                SELECT instruments.snapshot_date AS as_of_date,
                       instruments.code,
                       instruments.code_name AS name,
                       CASE WHEN lower(instruments.code) LIKE 'sh.%' THEN 'SH' ELSE 'SZ' END AS market,
                       instruments.trade_status,
                       instruments.security_type,
                       instruments.status,
                       bars.trade_date AS update_time,
                       bars.close AS price,
                       bars.close - bars.preclose AS change_amount,
                       CASE WHEN bars.preclose IS NULL OR bars.preclose = 0 THEN NULL
                            ELSE (bars.close - bars.preclose) / bars.preclose * 100 END AS change_percent,
                       bars.volume,
                       bars.amount
                FROM read_parquet(?) AS instruments
                INNER JOIN read_parquet(?) AS bars ON bars.code = instruments.code
                WHERE instruments.security_type = '1'
                  AND (? = '' OR instruments.code ILIKE ? OR instruments.code_name ILIKE ?)
                ORDER BY instruments.code
                LIMIT ?
                """;
        String value = keyword == null ? "" : keyword.trim();
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, instruments);
            statement.setString(2, daily);
            statement.setString(3, value);
            statement.setString(4, "%" + value + "%");
            statement.setString(5, "%" + value + "%");
            statement.setInt(6, Math.max(1, Math.min(limit, 500)));
            return rows(statement);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public List<Map<String, Object>> dailyBars(String code, int limit) {
        String normalizedCode = requireCode(code);
        String relative = "daily_history/code=" + normalizedCode + "/bars.parquet";
        return readBars(relative, "trade_date", limit, 1000);
    }

    public List<Map<String, Object>> minuteBars(String code, int limit) {
        String normalizedCode = requireCode(code);
        String relative = "minute_5m/code=" + normalizedCode + "/bars.parquet";
        return readBars(relative, "bar_end", limit, 2000);
    }

    public Map<String, Object> statistics() {
        Optional<String> latest = latestDailyDate();
        if (latest.isEmpty()) {
            return Map.of("available", false, "reason", "全市场日线分区尚未生成");
        }
        String source = marketSource("daily/trade_date=" + latest.get() + "/bars.parquet");
        String sql = """
                SELECT count(*) AS total,
                       sum(CASE WHEN close > preclose THEN 1 ELSE 0 END) AS advances,
                       sum(CASE WHEN close < preclose THEN 1 ELSE 0 END) AS declines,
                       sum(CASE WHEN close = preclose THEN 1 ELSE 0 END) AS unchanged,
                       coalesce(sum(amount), 0) AS total_amount,
                       coalesce(sum(volume), 0) AS total_volume
                FROM read_parquet(?)
                """;
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source);
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

    public List<Map<String, Object>> latestDailyRanking(int limit) {
        String instruments = marketSource("instruments/stocks.parquet");
        if (!marketAvailable("instruments/stocks.parquet")) {
            return List.of();
        }

        String dailySource = null;
        String dailyReader;
        Optional<String> latest = latestDailyDate();
        String latestRelative = latest
                .map(value -> "daily/trade_date=" + value + "/bars.parquet")
                .orElse("");
        if (!latestRelative.isEmpty() && marketAvailable(latestRelative)) {
            dailySource = marketSource(latestRelative);
            dailyReader = "read_parquet(?)";
        } else {
            Path directory = properties.marketDir().resolve("daily_history");
            if (!Files.isDirectory(directory)) {
                return List.of();
            }
            dailySource = directory.resolve("code=*/bars.parquet")
                    .toAbsolutePath().normalize().toString().replace('\\', '/');
            dailyReader = "read_parquet(?, hive_partitioning = true, union_by_name = true)";
        }

        String sql = """
                WITH bars AS MATERIALIZED (
                    SELECT trade_date, code, close, preclose
                    FROM %s
                    WHERE preclose IS NOT NULL AND preclose <> 0
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
                """.formatted(dailyReader);
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            statement.setString(parameter++, dailySource);
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
        Path directory = properties.marketDir().resolve("daily");
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("trade_date="))
                    .map(name -> name.substring("trade_date=".length()))
                    .max(String::compareTo);
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

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "行情数据源无法读取" : message;
    }
}
// AI_GENERATE_END ---------
