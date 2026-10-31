// AI_GENERATE_START ---
package com.stock.console.facts;

import com.stock.console.config.ConsoleProperties;
import com.stock.console.data.RemoteDataSource;
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
    private final RemoteDataSource remoteData;

    public TradingFacts(ConsoleProperties properties, RemoteDataSource remoteData) {
        this.properties = properties;
        this.remoteData = remoteData;
    }

    public Map<String, Object> status() {
        String source = databaseSource();
        if (!databaseAvailable(source)) {
            return Map.of(
                    "available", false,
                    "reason", "trading.db 不存在",
                    "path", source);
        }
        try (Connection connection = openReadOnly(); Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT count(*) FROM trading.task_run").close();
            return Map.of(
                    "available", true,
                    "engine", "DuckDB",
                    "mode", "READ_ONLY",
                    "path", source);
        } catch (SQLException exception) {
            return Map.of(
                    "available", false,
                    "reason", safeMessage(exception),
                    "path", source);
        }
    }

    public List<Map<String, Object>> account() {
        return query("""
                SELECT id, business_date, available_cash, total_assets, total_position,
                       frozen_amount, captured_at
                FROM trading.broker_account_snapshot
                ORDER BY captured_at DESC
                """, 1);
    }

    public List<Map<String, Object>> positions(int limit) {
        return query("""
                SELECT instrument, stock_name, quantity, available_quantity, frozen_quantity,
                       average_cost, current_price, market, captured_at,
                       quantity * current_price AS market_value
                FROM trading.broker_position_snapshot
                WHERE business_key = (
                    SELECT business_key FROM trading.broker_position_snapshot
                    ORDER BY captured_at DESC LIMIT 1
                )
                ORDER BY market_value DESC, instrument
                """, limit);
    }

    public List<Map<String, Object>> orders(int limit) {
        return query("""
                SELECT broker_order_id, instrument, stock_name, direction AS side,
                       order_price AS price, order_quantity AS quantity,
                       average_fill_price, filled_quantity, status, raw_status,
                       market, order_time, updated_at, 'BROKER' AS source
                FROM trading.broker_order_fact
                UNION ALL
                SELECT client_order_id AS broker_order_id, instrument, NULL AS stock_name,
                       side, price, quantity, NULL AS average_fill_price,
                       NULL AS filled_quantity, status, error_message AS raw_status,
                       NULL AS market, created_at AS order_time, updated_at, 'LOCAL' AS source
                FROM trading.broker_order_intent intent
                WHERE intent.broker_order_id IS NULL
                   OR NOT EXISTS (
                       SELECT 1 FROM trading.broker_order_fact fact
                       WHERE fact.broker_order_id = intent.broker_order_id
                   )
                ORDER BY updated_at DESC
                """, limit);
    }

    public List<Map<String, Object>> fills(int limit) {
        return query("""
                SELECT fill_key, broker_order_id, instrument, stock_name,
                       direction AS side, fill_price AS price, fill_quantity AS quantity,
                       fill_amount AS amount, fill_time, created_at
                FROM trading.broker_fill_fact
                ORDER BY coalesce(fill_time, created_at) DESC
                """, limit);
    }

    public List<Map<String, Object>> performance(int limit) {
        List<Map<String, Object>> live = query("""
                SELECT business_date, model_version_id, cash, market_value, equity,
                       external_cash_flow, turnover, estimated_fees, gross_pnl, net_pnl,
                       gross_return, net_return, updated_at, 'LIVE' AS scenario
                FROM trading.live_performance_snapshot
                ORDER BY business_date DESC
                """, limit);
        if (!live.isEmpty()) {
            return live;
        }
        return query("""
                SELECT business_date, NULL AS model_version_id, cash, market_value, equity,
                       NULL AS external_cash_flow, NULL AS turnover, NULL AS estimated_fees,
                       NULL AS gross_pnl, NULL AS net_pnl, NULL AS gross_return,
                       NULL AS net_return, created_at AS updated_at, scenario
                FROM trading.performance_snapshot
                ORDER BY business_date DESC
                """, limit);
    }

    public List<Map<String, Object>> models(int limit) {
        return query("""
                SELECT id, model_name, feature_set, status, artifact_hash,
                       artifact_path, metrics_json, created_at
                FROM trading.model_version
                ORDER BY created_at DESC
                """, limit);
    }

    public List<Map<String, Object>> predictions(int limit) {
        return query("""
                SELECT prediction.id, prediction.prediction_date, prediction.instrument,
                       prediction.rank, prediction.score, prediction.label,
                       model.id AS model_version_id, model.model_name,
                       model.feature_set, model.status AS model_status,
                       prediction.created_at
                FROM trading.prediction prediction
                JOIN trading.model_version model ON model.id = prediction.model_version_id
                ORDER BY prediction.prediction_date DESC, prediction.rank ASC
                """, limit);
    }

    public List<Map<String, Object>> evaluations(int limit) {
        return query("""
                SELECT evaluation.business_key, evaluation.business_date,
                       evaluation.window_days, evaluation.observations,
                       evaluation.gross_positive_ratio, evaluation.total_gross_return,
                       evaluation.total_net_return, evaluation.max_drawdown,
                       evaluation.decision, evaluation.reason, evaluation.created_at,
                       model.model_name, model.feature_set
                FROM trading.model_evaluation evaluation
                LEFT JOIN trading.model_version model ON model.id = evaluation.model_version_id
                ORDER BY evaluation.business_date DESC
                """, limit);
    }

    public List<Map<String, Object>> trainingRequests(int limit) {
        return query("""
                SELECT request.business_key, request.trigger_type, request.status,
                       request.requested_at, request.updated_at,
                       model.model_name, model.feature_set
                FROM trading.training_request request
                LEFT JOIN trading.model_version model ON model.id = request.model_version_id
                ORDER BY request.requested_at DESC
                """, limit);
    }

    public List<Map<String, Object>> taskRuns(int limit) {
        return query("""
                SELECT id, business_key, task_type, business_date, status,
                       started_at, finished_at, error_message
                FROM trading.task_run
                ORDER BY id DESC
                """, limit);
    }

    public Map<String, Object> agentSnapshot() {
        return Map.of(
                "source", status(),
                "account", account(),
                "positions", positions(10),
                "orders", orders(10),
                "performance", performance(20),
                "models", models(5),
                "predictions", predictions(10),
                "evaluations", evaluations(5),
                "tasks", taskRuns(10));
    }

    private List<Map<String, Object>> query(String sql, int limit) {
        String source = databaseSource();
        if (!databaseAvailable(source)) {
            return List.of();
        }
        try (Connection connection = openReadOnly(); Statement statement = connection.createStatement()) {
            statement.setMaxRows(Math.max(1, Math.min(limit, 500)));
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
        Connection connection = DriverManager.getConnection("jdbc:duckdb:");
        remoteData.configure(connection);
        String path = databaseSource().replace("'", "''");
        try (Statement statement = connection.createStatement()) {
            statement.execute("ATTACH '" + path + "' AS trading (TYPE sqlite, READ_ONLY)");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    private String databaseSource() {
        if (remoteData.enabled()) {
            return remoteData.tradingDatabase();
        }
        return properties.tradingDb().toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private boolean databaseAvailable(String source) {
        return remoteData.enabled()
                ? remoteData.exists(source)
                : Files.isRegularFile(properties.tradingDb());
    }

    private String safeMessage(SQLException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "SQLite 数据源无法读取" : message;
    }
}
// AI_GENERATE_END ---
