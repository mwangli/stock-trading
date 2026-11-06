// AI_GENERATE_START --
package com.stock.console.notification;

import java.util.List;

public record NotificationFeed(
        boolean available,
        List<NotificationItem> items,
        long latestId,
        long cursorId,
        long unreadCount) {

    public static NotificationFeed unavailable() {
        return new NotificationFeed(false, List.of(), 0, 0, 0);
    }
}
// AI_GENERATE_END --
