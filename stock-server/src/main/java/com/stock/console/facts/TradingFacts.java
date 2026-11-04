// AI_GENERATE_START ----------------
package com.stock.console.facts;

import com.stock.console.config.ConsoleProperties;
import com.stock.console.market.MarketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TradingFacts {

    private static final Logger LOGGER = LoggerFactory.getLogger(TradingFacts.class);
    private static final String POSITIONS_SQL = """
            WITH realized_buys AS (
                SELECT buy_date AS trade_date, buy_order_id AS broker_order_id,
                       sum(quantity) AS matched_quantity
                FROM trading.realized_trade
                GROUP BY buy_date, buy_order_id
            ), open_buys AS (
                SELECT orders.instrument, orders.stock_name, orders.market,
                       orders.average_fill_price,
                       orders.filled_quantity - coalesce(realized_buys.matched_quantity, 0) AS quantity,
                       orders.updated_at
                FROM trading.broker_order orders
                LEFT JOIN realized_buys USING(trade_date, broker_order_id)
                WHERE orders.direction = 'BUY'
                  AND orders.filled_quantity > coalesce(realized_buys.matched_quantity, 0)
            )
            SELECT instrument, max(stock_name) AS stock_name,
                   sum(quantity) AS quantity, sum(quantity) AS available_quantity,
                   0 AS frozen_quantity,
                   sum(quantity * average_fill_price) / nullif(sum(quantity), 0) AS average_cost,
                   sum(quantity * average_fill_price) / nullif(sum(quantity), 0) AS current_price,
                   max(market) AS market, max(updated_at) AS captured_at,
                   sum(quantity * average_fill_price) AS market_value
            FROM open_buys
            GROUP BY instrument
            ORDER BY market_value DESC, instrument
            """;
    private static final String PERFORMANCE_SQL = """
            SELECT sell_date AS business_date, model_version_id,
                   cost_basis AS cash, sell_amount AS market_value,
                   sell_amount AS equity, NULL AS external_cash_flow,
                   sell_amount AS turnover, buy_fee + sell_fee AS estimated_fees,
                   gross_profit AS gross_pnl, net_profit AS net_pnl,
                   gross_return, net_return, updated_at, instrument AS scenario
            FROM trading.realized_trade
            ORDER BY sell_time DESC
            """;
    private static final String PERFORMANCE_RANKING_SQL = """
            SELECT trade.trade_key, trade.instrument,
                   coalesce(buy_order.stock_name, sell_order.stock_name) AS stock_name,
                   trade.cost_basis + trade.sell_amount AS transaction_amount,
                   trade.cost_basis AS buy_amount, trade.sell_amount,
                   trade.quantity AS trade_quantity, trade.net_profit,
                   trade.net_return AS return_rate, trade.buy_date, trade.sell_date,
                   trade.buy_time, trade.sell_time
            FROM trading.realized_trade trade
            LEFT JOIN trading.broker_order buy_order
              ON buy_order.trade_date = trade.buy_date
             AND buy_order.broker_order_id = trade.buy_order_id
            LEFT JOIN trading.broker_order sell_order
              ON sell_order.trade_date = trade.sell_date
             AND sell_order.broker_order_id = trade.sell_order_id
            ORDER BY trade.net_return DESC, trade.net_profit DESC, trade.sell_time DESC
            """;
    private static final String MODELS_SQL = """
            SELECT model.id, model.model_name, model.feature_set, model.status,
                   model.artifact_hash, model.artifact_path, model.metrics_json,
                   model.created_at, coalesce(prediction_stats.prediction_count, 0) AS prediction_count,
                   coalesce(trade_stats.realized_trade_count, 0) AS realized_trade_count,
                   coalesce(trade_stats.gross_profit, 0) AS gross_profit,
                   coalesce(trade_stats.net_profit, 0) AS net_profit,
                   trade_stats.net_return, trade_stats.last_sell_time
            FROM trading.model_version model
            LEFT JOIN (
                SELECT model_version_id, count(*) AS prediction_count
                FROM trading.prediction GROUP BY model_version_id
            ) prediction_stats ON prediction_stats.model_version_id = model.id
            LEFT JOIN (
                SELECT model_version_id, count(*) AS realized_trade_count,
                       sum(gross_profit) AS gross_profit, sum(net_profit) AS net_profit,
                       sum(net_profit) / nullif(sum(cost_basis) + sum(buy_fee), 0) AS net_return,
                       max(sell_time) AS last_sell_time
                FROM trading.realized_trade
                WHERE model_version_id IS NOT NULL
                GROUP BY model_version_id
            ) trade_stats ON trade_stats.model_version_id = model.id
            ORDER BY model.created_at DESC
            """;
    private static final String PREDICTIONS_SQL = """
            SELECT prediction.id, prediction.prediction_date, prediction.instrument,
                   prediction.rank, prediction.score, prediction.label,
                   model.id AS model_version_id, model.model_name,
                   model.feature_set, model.status AS model_status,
                   prediction.created_at
            FROM trading.prediction prediction
            JOIN trading.model_version model ON model.id = prediction.model_version_id
            ORDER BY prediction.prediction_date DESC, prediction.rank ASC
            """;
    private static final String EVALUATIONS_SQL = """
            SELECT evaluation.business_key, evaluation.business_date,
                   evaluation.window_observations AS window_days, evaluation.observations,
                   evaluation.positive_period_ratio AS gross_positive_ratio,
                   evaluation.total_gross_return,
                   evaluation.total_net_return, evaluation.max_drawdown,
                   evaluation.decision, evaluation.reason, evaluation.created_at,
                   model.model_name, model.feature_set
            FROM trading.model_evaluation evaluation
            LEFT JOIN trading.model_version model ON model.id = evaluation.model_version_id
            ORDER BY evaluation.business_date DESC
            """;
    private static final String TRAINING_REQUESTS_SQL = """
            SELECT request.evaluation_key AS business_key,
                   'MODEL_EVALUATION' AS trigger_type, request.status,
                   request.requested_at, coalesce(request.completed_at, request.requested_at) AS updated_at,
                   model.model_name, model.feature_set
            FROM trading.training_request request
            LEFT JOIN trading.model_version model ON model.id = request.requested_model_version_id
            ORDER BY request.requested_at DESC
            """;
    private final ConsoleProperties properties;
    private final MarketRepository market;

    public TradingFacts(ConsoleProperties properties, MarketRepository market) {
        this.properties = properties;
        this.market = market;
    }

    public Map<String, Object> status() {
        String source = databaseSource();
        if (!databaseAvailable()) {
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
        List<Map<String, Object>> positions = positions(500);
        return List.of(summarizeAccount(positions));
    }

    public List<Map<String, Object>> positions(int limit) {
        return query(POSITIONS_SQL, limit);
    }

    public List<Map<String, Object>> orders(int limit) {
        return query("""
                SELECT orders.broker_order_id, orders.instrument, orders.stock_name, orders.direction AS side,
                       order_price AS price, order_quantity AS quantity,
                       average_fill_price, filled_quantity, status, raw_status,
                       market, order_time, orders.updated_at, 'BROKER' AS source,
                       coalesce(fee.total_fee, 0) AS total_fee
                FROM trading.broker_order orders
                LEFT JOIN trading.broker_order_fee fee USING(trade_date, broker_order_id)
                UNION ALL
                SELECT client_order_id AS broker_order_id, instrument, NULL AS stock_name,
                       side, price, quantity, NULL AS average_fill_price,
                       NULL AS filled_quantity, status, error_message AS raw_status,
                       NULL AS market, created_at AS order_time, updated_at, 'LOCAL' AS source,
                       0 AS total_fee
                FROM trading.order_attempt attempt
                WHERE attempt.broker_order_id IS NULL
                   OR NOT EXISTS (
                       SELECT 1 FROM trading.broker_order orders
                       WHERE orders.broker_order_id = attempt.broker_order_id
                   )
                ORDER BY updated_at DESC
                """, limit);
    }

    public List<Map<String, Object>> fills(int limit) {
        return query("""
                SELECT trade_date || ':' || broker_order_id AS fill_key,
                       broker_order_id, instrument, stock_name,
                       direction AS side, average_fill_price AS price,
                       filled_quantity AS quantity,
                       average_fill_price * filled_quantity AS amount,
                       order_time AS fill_time, created_at
                FROM trading.broker_order
                ORDER BY order_time DESC
                """, limit);
    }

    public List<Map<String, Object>> trades(int limit) {
        return withHoldingDays(query("""
                SELECT trade.trade_key, trade.model_version_id, trade.instrument,
                       buy_order.stock_name, trade.buy_order_id, trade.sell_order_id,
                       trade.buy_date, trade.sell_date, trade.quantity,
                       trade.buy_price, trade.sell_price, trade.buy_fee, trade.sell_fee,
                       trade.cost_basis, trade.sell_amount, trade.gross_profit,
                       trade.net_profit, trade.gross_return, trade.net_return,
                       trade.buy_time, trade.sell_time, trade.created_at, trade.updated_at
                FROM trading.realized_trade trade
                LEFT JOIN trading.broker_order buy_order
                  ON buy_order.trade_date = trade.buy_date
                 AND buy_order.broker_order_id = trade.buy_order_id
                ORDER BY trade.sell_time DESC, trade.trade_key
                """, limit));
    }

    public List<Map<String, Object>> performance(int limit) {
        return query(PERFORMANCE_SQL, limit);
    }

    public Map<String, Object> agentPerformanceSummary(
            LocalDate startDate,
            LocalDate endDate,
            String groupBy) {
        String grouping = switch (groupBy) {
            case "day" -> "CAST(sell_date AS VARCHAR)";
            case "month" -> "strftime(CAST(sell_date AS DATE), '%Y-%m')";
            case "instrument" -> "instrument";
            case "model" -> "CAST(model_version_id AS VARCHAR)";
            case "total" -> "'total'";
            default -> throw new IllegalArgumentException("不支持的收益分组: " + groupBy);
        };
        String sql = """
                SELECT %s AS period,
                       count(*) AS trade_count,
                       sum(CASE WHEN net_profit > 0 THEN 1 ELSE 0 END) AS winning_trades,
                       sum(cost_basis) AS cost_basis,
                       sum(buy_fee + sell_fee) AS total_fees,
                       sum(gross_profit) AS gross_profit,
                       sum(net_profit) AS net_profit,
                       sum(gross_profit) / nullif(sum(cost_basis), 0) AS gross_return,
                       sum(net_profit) / nullif(sum(cost_basis) + sum(buy_fee), 0) AS net_return
                FROM trading.realized_trade
                WHERE CAST(sell_date AS DATE) BETWEEN CAST(? AS DATE) AND CAST(? AS DATE)
                GROUP BY %s
                ORDER BY period
                """.formatted(grouping, grouping);
        List<Map<String, Object>> rows = query(sql, 500, List.of(startDate, endDate));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startDate", startDate.toString());
        result.put("endDate", endDate.toString());
        result.put("groupBy", groupBy);
        result.put("rows", rows);
        result.put("returnBasis", "仅统计 realized_trade 已完成买卖闭环；gross 未扣费用，net 扣已记录费用。");
        return result;
    }

    public List<Map<String, Object>> agentRealizedTrades(
            LocalDate startDate,
            LocalDate endDate,
            String instrument,
            int limit) {
        String normalizedInstrument = instrument == null ? "" : instrument.trim().toLowerCase();
        return query("""
                SELECT trade_key, model_version_id, instrument, buy_order_id, sell_order_id,
                       buy_date, sell_date, quantity, buy_price, sell_price,
                       buy_fee, sell_fee, cost_basis, sell_amount,
                       gross_profit, net_profit, gross_return, net_return,
                       buy_time, sell_time
                FROM trading.realized_trade
                WHERE CAST(sell_date AS DATE) BETWEEN CAST(? AS DATE) AND CAST(? AS DATE)
                  AND (? = '' OR lower(instrument) = ?)
                ORDER BY sell_time DESC
                """, limit, List.of(startDate, endDate, normalizedInstrument, normalizedInstrument));
    }

    public List<Map<String, Object>> agentPredictionRanking(LocalDate predictionDate, int limit) {
        String sql = predictionDate == null ? """
                SELECT prediction_date, instrument, rank, score, label, model_version_id, created_at
                FROM trading.prediction
                WHERE prediction_date = (SELECT max(prediction_date) FROM trading.prediction)
                ORDER BY rank, instrument
                """ : """
                SELECT prediction_date, instrument, rank, score, label, model_version_id, created_at
                FROM trading.prediction
                WHERE CAST(prediction_date AS DATE) = CAST(? AS DATE)
                ORDER BY rank, instrument
                """;
        return predictionDate == null
                ? query(sql, limit)
                : query(sql, limit, List.of(predictionDate));
    }

    public List<Map<String, Object>> agentTaskFailures(int limit) {
        return query("""
                SELECT business_key, task_type, business_date, status,
                       started_at, finished_at, error_message
                FROM trading.task_run
                WHERE upper(status) IN ('FAILED', 'ERROR')
                ORDER BY id DESC
                """, limit);
    }

    public List<Map<String, Object>> agentTaskStatus(LocalDate businessDate, String taskType, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT business_key, task_type, business_date, status,
                       started_at, finished_at, error_message
                FROM trading.task_run
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (businessDate != null) {
            sql.append(" AND CAST(business_date AS DATE) = CAST(? AS DATE)");
            parameters.add(businessDate);
        }
        if (taskType != null && !taskType.isBlank()) {
            sql.append(" AND lower(task_type) LIKE ?");
            parameters.add("%" + taskType.trim().toLowerCase() + "%");
        }
        sql.append(" ORDER BY id DESC");
        return parameters.isEmpty()
                ? query(sql.toString(), limit)
                : query(sql.toString(), limit, parameters);
    }

    public List<Map<String, Object>> models(int limit) {
        return query(MODELS_SQL, limit);
    }

    public Map<String, Object> dashboardOverview() {
        if (!databaseAvailable()) {
            return emptyDashboard("trading.db 不存在");
        }
        try (Connection connection = openReadOnly()) {
            List<Map<String, Object>> positions = query(connection, POSITIONS_SQL, 50);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tradingSource", availableStatus());
            result.put("account", summarizeAccount(positions));
            result.put("positions", positions);
            result.put("performance", query(connection, PERFORMANCE_SQL, 90));
            result.put("ranking", withHoldingDays(query(connection, PERFORMANCE_RANKING_SQL, 500)));
            return result;
        } catch (SQLException exception) {
            LOGGER.warn("收益总览查询失败: {}", safeMessage(exception));
            return emptyDashboard(safeMessage(exception));
        }
    }

    public List<Map<String, Object>> predictions(int limit) {
        return query(PREDICTIONS_SQL, limit);
    }

    public List<Map<String, Object>> evaluations(int limit) {
        return query(EVALUATIONS_SQL, limit);
    }

    public List<Map<String, Object>> trainingRequests(int limit) {
        return query(TRAINING_REQUESTS_SQL, limit);
    }

    public Map<String, Object> tradingStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!databaseAvailable()) {
            result.put("source", status());
            return result;
        }
        try (Connection connection = openReadOnly()) {
            List<Map<String, Object>> counts = query(connection, """
                    SELECT (SELECT count(*) FROM trading.broker_order) AS order_count,
                           (SELECT count(*) FROM trading.order_attempt) AS attempt_count,
                           (SELECT count(*) FROM trading.realized_trade) AS realized_trade_count

                    """, 1);
            result.put("source", availableStatus());
            if (!counts.isEmpty()) {
                result.putAll(counts.get(0));
            }
        } catch (SQLException exception) {
            result.put("source", Map.of(
                    "available", false,
                    "reason", safeMessage(exception),
                    "path", databaseSource()));
        }
        return result;
    }

    public Map<String, Object> modelOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!databaseAvailable()) {
            result.put("versions", List.of());
            result.put("predictions", List.of());
            result.put("evaluations", List.of());
            result.put("trainingRequests", List.of());
            return result;
        }
        try (Connection connection = openReadOnly()) {
            result.put("versions", query(connection, MODELS_SQL, 20));
            result.put("predictions", query(connection, PREDICTIONS_SQL, 20));
            result.put("evaluations", query(connection, EVALUATIONS_SQL, 20));
            result.put("trainingRequests", query(connection, TRAINING_REQUESTS_SQL, 20));
        } catch (SQLException exception) {
            LOGGER.warn("模型信息查询失败: {}", safeMessage(exception));
            result.put("versions", List.of());
            result.put("predictions", List.of());
            result.put("evaluations", List.of());
            result.put("trainingRequests", List.of());
        }
        return result;
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
        Map<String, Object> dashboard = dashboardOverview();
        return Map.of(
                "source", dashboard.get("tradingSource"),
                "account", dashboard.get("account"),
                "positions", dashboard.get("positions"),
                "orders", orders(10),
                "performance", dashboard.get("performance"),
                "models", models(5),
                "predictions", predictions(10),
                "evaluations", evaluations(5),
                "tasks", taskRuns(10));
    }

    private List<Map<String, Object>> query(String sql, int limit) {
        if (!databaseAvailable()) {
            return List.of();
        }
        try (Connection connection = openReadOnly()) {
            return query(connection, sql, limit);
        } catch (SQLException exception) {
            LOGGER.warn("只读交易数据查询失败: {}", safeMessage(exception));
            return List.of();
        }
    }

    private List<Map<String, Object>> query(String sql, int limit, List<Object> parameters) {
        if (!databaseAvailable()) {
            return List.of();
        }
        try (Connection connection = openReadOnly();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(Math.max(1, Math.min(limit, 500)));
            for (int index = 0; index < parameters.size(); index++) {
                Object value = parameters.get(index);
                if (value instanceof LocalDate date) {
                    statement.setString(index + 1, date.toString());
                } else {
                    statement.setObject(index + 1, value);
                }
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return rows(resultSet);
            }
        } catch (SQLException exception) {
            LOGGER.warn("只读交易数据参数查询失败: {}", safeMessage(exception));
            return List.of();
        }
    }

    private List<Map<String, Object>> query(Connection connection, String sql, int limit) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setMaxRows(Math.max(1, Math.min(limit, 500)));
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                return rows(resultSet);
            }
        }
    }

    private List<Map<String, Object>> rows(ResultSet resultSet) throws SQLException {
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

    private Map<String, Object> summarizeAccount(List<Map<String, Object>> positions) {
        double totalPosition = positions.stream()
                .map(row -> row.get("market_value"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToDouble(Number::doubleValue)
                .sum();
        String capturedAt = positions.stream()
                .map(row -> row.get("captured_at"))
                .filter(value -> value != null)
                .map(String::valueOf)
                .max(String::compareTo)
                .orElse("");
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("total_assets", totalPosition);
        account.put("available_cash", 0);
        account.put("total_position", totalPosition);
        account.put("frozen_amount", 0);
        account.put("captured_at", capturedAt);
        account.put("source", "OPEN_FILLED_BUY_COST");
        return account;
    }

    private List<Map<String, Object>> withHoldingDays(List<Map<String, Object>> rows) {
        LocalDate startDate = rows.stream()
                .map(row -> tradeDate(row, "buy_date", "buy_time"))
                .filter(value -> value != null)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate endDate = rows.stream()
                .map(row -> tradeDate(row, "sell_date", "sell_time"))
                .filter(value -> value != null)
                .max(LocalDate::compareTo)
                .orElse(null);
        List<LocalDate> tradingDates = market.tradingDates(startDate, endDate);
        for (Map<String, Object> row : rows) {
            LocalDate buyDate = tradeDate(row, "buy_date", "buy_time");
            LocalDate sellDate = tradeDate(row, "sell_date", "sell_time");
            if (buyDate == null || sellDate == null || sellDate.isBefore(buyDate) || tradingDates.isEmpty()) {
                row.put("holding_days", null);
                continue;
            }
            int holdingDays = (int) tradingDates.stream()
                    .filter(date -> date.isAfter(buyDate) && !date.isAfter(sellDate))
                    .count();
            row.put("holding_days", holdingDays);
        }
        return rows;
    }

    private LocalDate tradeDate(Map<String, Object> row, String dateKey, String timeKey) {
        Object value = row.get(dateKey);
        if (value == null) {
            value = row.get(timeKey);
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.length() >= 10 ? LocalDate.parse(text.substring(0, 10)) : null;
    }

    private Map<String, Object> availableStatus() {
        return Map.of(
                "available", true,
                "engine", "DuckDB",
                "mode", "READ_ONLY",
                "path", databaseSource());
    }

    private Map<String, Object> emptyDashboard(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tradingSource", Map.of(
                "available", false,
                "reason", reason,
                "path", databaseSource()));
        result.put("account", summarizeAccount(List.of()));
        result.put("positions", List.of());
        result.put("performance", List.of());
        result.put("ranking", List.of());
        return result;
    }

    private Connection openReadOnly() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:duckdb:");
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
        return properties.tradingDb().toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private boolean databaseAvailable() {
        return Files.isRegularFile(properties.tradingDb());
    }

    private String safeMessage(SQLException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "SQLite 数据源无法读取" : message;
    }
}
// AI_GENERATE_END ----------------
