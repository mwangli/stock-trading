// AI_GENERATE_START ---
package com.stock.console.market;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Pattern;

@Repository
public class MarketRepository {

    private static final Pattern CODE = Pattern.compile("(?i)(sh|sz)[.]\\d{6}");
    private final ConsoleProperties properties;
    private final ObjectMapper objectMapper;

    public MarketRepository(ConsoleProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> status() {
        Path contract = properties.marketDir().resolve("console/contract.json");
        Path instruments = properties.marketDir().resolve("console/instruments.parquet");
        if (!Files.isRegularFile(contract)) {
            return Map.of("available", false, "reason", "行情契约文件不存在", "contract", contract.toString());
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(
                    Files.readString(contract), new TypeReference<>() {
                    });
            int version = Integer.parseInt(String.valueOf(metadata.getOrDefault("contractVersion", 0)));
            boolean directoryReadable = Files.isReadable(instruments) && Files.isRegularFile(instruments);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", version == properties.expectedContractVersion() && directoryReadable);
            result.put("version", version);
            result.put("expectedVersion", properties.expectedContractVersion());
            result.put("asOfDate", metadata.get("asOfDate"));
            result.put("instrumentsReadable", directoryReadable);
            result.put("contract", contract.toString());
            result.put("marketDir", properties.marketDir().toString());
            if (version != properties.expectedContractVersion()) {
                result.put("reason", "行情契约版本不兼容");
            } else if (!directoryReadable) {
                result.put("reason", "股票目录文件不可读");
            }
            return result;
        } catch (Exception exception) {
            return Map.of("available", false, "reason", "行情契约无法读取", "contract", contract.toString());
        }
    }

    public List<Map<String, Object>> instruments(String keyword, int limit) {
        Path source = properties.marketDir().resolve("console/instruments.parquet");
        if (!Files.isRegularFile(source)) {
            return List.of();
        }
        String sql = "SELECT as_of_date,code,name,market,trade_status FROM read_parquet(?) "
                + "WHERE (? = '' OR code ILIKE ? OR name ILIKE ?) ORDER BY code LIMIT ?";
        String value = keyword == null ? "" : keyword.trim();
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source.toString());
            statement.setString(2, value);
            statement.setString(3, "%" + value + "%");
            statement.setString(4, "%" + value + "%");
            statement.setInt(5, Math.max(1, Math.min(limit, 500)));
            return rows(statement);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public List<Map<String, Object>> dailyBars(String code, int limit) {
        String normalized = requireCode(code);
        Path history = properties.marketDir().resolve("daily_history/code=" + normalized + "/bars.parquet");
        if (!Files.isRegularFile(history)) {
            return List.of();
        }
        String sql = "SELECT trade_date,open,high,low,close,preclose,volume,amount,tradestatus,is_st "
                + "FROM read_parquet(?) ORDER BY trade_date DESC LIMIT ?";
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, history.toString());
            statement.setInt(2, Math.max(1, Math.min(limit, 1000)));
            return rows(statement);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public List<Map<String, Object>> minuteBars(String code, int limit) {
        String normalized = requireCode(code);
        Path directory = properties.marketDir().resolve("minute_5m/code=" + normalized);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        String source = directory.resolve("trade_date=*/bars.parquet").toString();
        String sql = "SELECT trade_date,bar_start,bar_end,open,high,low,close,volume,amount "
                + "FROM read_parquet(?) ORDER BY bar_end DESC LIMIT ?";
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source);
            statement.setInt(2, Math.max(1, Math.min(limit, 2000)));
            return rows(statement);
        } catch (Exception exception) {
            return List.of();
        }
    }

    public Map<String, Object> statistics() {
        Map<String, Object> contract = status();
        Object asOfDate = contract.get("asOfDate");
        if (!Boolean.TRUE.equals(contract.get("available")) || asOfDate == null) {
            return Map.of("available", false, "reason", contract.getOrDefault("reason", "行情数据不可用"));
        }
        Path source = properties.marketDir().resolve(
                "daily/trade_date=" + asOfDate + "/bars.parquet");
        if (!Files.isRegularFile(source)) {
            return Map.of("available", false, "reason", "最新日线分区不存在", "asOfDate", asOfDate);
        }
        String sql = "SELECT count(*) AS total, "
                + "sum(CASE WHEN close > preclose THEN 1 ELSE 0 END) AS advances, "
                + "sum(CASE WHEN close < preclose THEN 1 ELSE 0 END) AS declines, "
                + "sum(CASE WHEN close = preclose THEN 1 ELSE 0 END) AS unchanged "
                + "FROM read_parquet(?)";
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, source.toString());
            List<Map<String, Object>> values = rows(statement);
            Map<String, Object> result = new LinkedHashMap<>(values.isEmpty() ? Map.of() : values.get(0));
            result.put("available", true);
            result.put("asOfDate", asOfDate);
            return result;
        } catch (Exception exception) {
            return Map.of("available", false, "reason", "市场统计读取失败", "asOfDate", asOfDate);
        }
    }

    private String requireCode(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase();
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("股票代码格式错误");
        }
        return normalized;
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
}
// AI_GENERATE_END ---
