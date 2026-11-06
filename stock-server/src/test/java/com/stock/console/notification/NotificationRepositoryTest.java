// AI_GENERATE_START ----
package com.stock.console.notification;

import com.stock.console.config.ConsoleProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRepositoryTest {

    private static final DateTimeFormatter SQLITE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    @TempDir
    Path tempDir;

    @Test
    void readsLatestAndIncrementalNotifications() throws Exception {
        Path database = createDatabase();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            connection.createStatement().execute("""
                    CREATE TABLE notify_msg (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        category TEXT NOT NULL,
                        level TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        source TEXT NOT NULL,
                        event_key TEXT UNIQUE,
                        created_at TEXT NOT NULL,
                        expires_at TEXT NOT NULL
                    )
                    """);
            insert(connection, "SYSTEM", "ERROR", "系统异常", now.minusMinutes(3), now.plusDays(1));
            insert(connection, "TRADE", "SUCCESS", "交易成功", now.minusMinutes(2), now.plusDays(1));
            insert(connection, "TRADE", "ERROR", "交易失败", now.minusMinutes(1), now.plusDays(1));
            insert(connection, "SYSTEM", "ERROR", "已过期", now.minusDays(2), now.minusDays(1));
        }

        NotificationRepository repository = new NotificationRepository(properties());
        NotificationFeed latest = repository.find(null, 1, 2);
        NotificationFeed incremental = repository.find(1L, 1, 1);
        NotificationFeed nextIncremental = repository.find(incremental.cursorId(), 1, 1);

        assertThat(latest.available()).isTrue();
        assertThat(latest.latestId()).isEqualTo(3);
        assertThat(latest.cursorId()).isEqualTo(3);
        assertThat(latest.unreadCount()).isEqualTo(2);
        assertThat(latest.items()).extracting(NotificationItem::id).containsExactly(3L, 2L);
        assertThat(incremental.latestId()).isEqualTo(3);
        assertThat(incremental.cursorId()).isEqualTo(2);
        assertThat(incremental.items()).extracting(NotificationItem::id).containsExactly(2L);
        assertThat(nextIncremental.cursorId()).isEqualTo(3);
        assertThat(nextIncremental.items()).extracting(NotificationItem::id).containsExactly(3L);
    }

    @Test
    void returnsUnavailableWhenTableDoesNotExist() throws Exception {
        createDatabase();

        NotificationFeed feed = new NotificationRepository(properties()).find(null, 0, 50);

        assertThat(feed.available()).isFalse();
        assertThat(feed.items()).isEmpty();
    }

    private Path createDatabase() throws Exception {
        Path state = Files.createDirectories(tempDir.resolve("state"));
        Path database = state.resolve("trading.db");
        try (Connection ignored = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            return database;
        }
    }

    private void insert(
            Connection connection,
            String category,
            String level,
            String title,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO notify_msg(
                    category, level, title, content, source, event_key, created_at, expires_at
                ) VALUES(?, ?, ?, ?, ?, NULL, ?, ?)
                """)) {
            statement.setString(1, category);
            statement.setString(2, level);
            statement.setString(3, title);
            statement.setString(4, title + "内容");
            statement.setString(5, "test");
            statement.setString(6, createdAt.format(SQLITE_TIMESTAMP));
            statement.setString(7, expiresAt.format(SQLITE_TIMESTAMP));
            statement.executeUpdate();
        }
    }

    private ConsoleProperties properties() {
        return new ConsoleProperties(
                tempDir,
                tempDir.resolve("task.log"),
                tempDir.toString(),
                new ConsoleProperties.Auth("admin", "hash"),
                null,
                null);
    }
}
// AI_GENERATE_END ----
