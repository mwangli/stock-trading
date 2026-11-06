// AI_GENERATE_START ---
package com.stock.console.notification;

import com.stock.console.config.ConsoleProperties;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Repository
public class NotificationRepository {

    private static final DateTimeFormatter SQLITE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");
    private final ConsoleProperties properties;

    public NotificationRepository(ConsoleProperties properties) {
        this.properties = properties;
    }

    public NotificationFeed find(Long afterId, long seenId, int limit) {
        if (!Files.isRegularFile(properties.tradingDb())) {
            return NotificationFeed.unavailable();
        }
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        long normalizedSeenId = Math.max(0, seenId);
        String now = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS)
                .format(SQLITE_TIMESTAMP);
        try (Connection connection = openReadOnly()) {
            if (!tableAvailable(connection)) {
                return NotificationFeed.unavailable();
            }
            long latestId = scalarLong(
                    connection,
                    "SELECT coalesce(max(id), 0) FROM trading.notify_msg WHERE expires_at > ?",
                    now);
            long unreadCount = scalarLong(
                    connection,
                    "SELECT count(*) FROM trading.notify_msg WHERE id > ? AND expires_at > ?",
                    normalizedSeenId,
                    now);
            List<NotificationItem> items = items(connection, afterId, normalizedLimit, now);
            long cursorId = items.stream()
                    .mapToLong(NotificationItem::id)
                    .max()
                    .orElse(afterId == null ? latestId : Math.max(0, afterId));
            return new NotificationFeed(true, items, latestId, cursorId, unreadCount);
        } catch (SQLException exception) {
            return NotificationFeed.unavailable();
        }
    }

    private List<NotificationItem> items(
            Connection connection, Long afterId, int limit, String now) throws SQLException {
        boolean incremental = afterId != null;
        String sql = incremental
                ? """
                    SELECT id, category, level, title, content, source, created_at
                    FROM trading.notify_msg
                    WHERE id > ? AND expires_at > ?
                    ORDER BY id ASC LIMIT ?
                    """
                : """
                    SELECT id, category, level, title, content, source, created_at
                    FROM trading.notify_msg
                    WHERE expires_at > ?
                    ORDER BY id DESC LIMIT ?
                    """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            if (incremental) {
                statement.setLong(parameter++, Math.max(0, afterId));
            }
            statement.setString(parameter++, now);
            statement.setInt(parameter, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<NotificationItem> items = new ArrayList<>();
                while (resultSet.next()) {
                    items.add(new NotificationItem(
                            resultSet.getLong("id"),
                            resultSet.getString("category"),
                            resultSet.getString("level"),
                            resultSet.getString("title"),
                            resultSet.getString("content"),
                            resultSet.getString("source"),
                            resultSet.getString("created_at")));
                }
                return items;
            }
        }
    }

    private boolean tableAvailable(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT 1 FROM trading.notify_msg LIMIT 1");
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    private long scalarLong(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        }
    }

    private Connection openReadOnly() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:duckdb:");
        String path = properties.tradingDb().toAbsolutePath().normalize().toString()
                .replace('\\', '/')
                .replace("'", "''");
        try (Statement statement = connection.createStatement()) {
            statement.execute("ATTACH '" + path + "' AS trading (TYPE sqlite, READ_ONLY)");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }
}
// AI_GENERATE_END ---
