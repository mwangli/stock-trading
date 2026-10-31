// AI_GENERATE_START -
package com.stock.console.facts;

import com.stock.console.config.ConsoleProperties;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TradingFacts {

    private final ConsoleProperties properties;

    public TradingFacts(ConsoleProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> contractStatus() {
        if (!Files.isRegularFile(properties.tradingDb())) {
            return Map.of("available", false, "reason", "trading.db 不存在");
        }
        try {
            List<Map<String, Object>> rows = querySql(
                    "SELECT contract_value FROM console_contract_metadata "
                            + "WHERE contract_key='console_contract_version'", 1);
            int version = rows.isEmpty() ? 0 : Integer.parseInt(String.valueOf(rows.get(0).get("contract_value")));
            return Map.of(
                    "available", version == properties.expectedContractVersion(),
                    "version", version,
                    "expectedVersion", properties.expectedContractVersion(),
                    "path", properties.tradingDb().toString());
        } catch (Exception exception) {
            return Map.of("available", false, "reason", exception.getMessage());
        }
    }

    public List<Map<String, Object>> account() {
        return queryView("console_v_account_latest", "captured_at DESC", 1);
    }

    public List<Map<String, Object>> positions(int limit) {
        return queryView("console_v_positions_latest", "market_value DESC", limit,
                "instrument, stock_name, quantity, available_quantity, frozen_quantity, "
                        + "average_cost, current_price, market, captured_at, "
                        + "quantity * current_price AS market_value");
    }

    public List<Map<String, Object>> orders(int limit) {
        return queryView("console_v_orders", "updated_at DESC", limit);
    }

    public List<Map<String, Object>> fills(int limit) {
        return queryView("console_v_fills", "COALESCE(fill_time, created_at) DESC", limit);
    }

    public List<Map<String, Object>> performance(int limit) {
        return queryView("console_v_performance", "business_date DESC", limit);
    }

    public List<Map<String, Object>> models(int limit) {
        return queryView("console_v_model_versions", "created_at DESC", limit);
    }

    public List<Map<String, Object>> predictions(int limit) {
        return queryView("console_v_predictions", "prediction_date DESC, rank ASC", limit);
    }

    public List<Map<String, Object>> evaluations(int limit) {
        return queryView("console_v_model_evaluations", "business_date DESC", limit);
    }

    public List<Map<String, Object>> trainingRequests(int limit) {
        return queryView("console_v_training_requests", "requested_at DESC", limit);
    }

    public List<Map<String, Object>> taskRuns(int limit) {
        return queryView("console_v_task_runs", "id DESC", limit);
    }

    public Map<String, Object> agentSnapshot() {
        return Map.of(
                "account", account(),
                "positions", positions(10),
                "orders", orders(10),
                "performance", performance(20),
                "models", models(5),
                "predictions", predictions(10),
                "evaluations", evaluations(5),
                "tasks", taskRuns(10));
    }

    private List<Map<String, Object>> queryView(String view, String orderBy, int limit) {
        return queryView(view, orderBy, limit, "*");
    }

    private List<Map<String, Object>> queryView(
            String view, String orderBy, int limit, String columns) {
        return querySql("SELECT " + columns + " FROM " + view + " ORDER BY " + orderBy,
                Math.max(1, Math.min(limit, 500)));
    }

    private List<Map<String, Object>> querySql(String sql, int limit) {
        if (!Files.isRegularFile(properties.tradingDb())) {
            return List.of();
        }
        try (Connection connection = openReadOnly(); Statement statement = connection.createStatement()) {
            statement.setMaxRows(limit);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException exception) {
            return List.of();
        }
    }

    private Connection openReadOnly() throws SQLException {
        String path = properties.tradingDb().toAbsolutePath().normalize().toString().replace('\\', '/');
        Connection connection = DriverManager.getConnection("jdbc:sqlite:file:" + path + "?mode=ro");
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only=ON");
            statement.execute("PRAGMA busy_timeout=30000");
        }
        return connection;
    }
}
// AI_GENERATE_END -
