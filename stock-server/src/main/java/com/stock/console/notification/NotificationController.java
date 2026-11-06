// AI_GENERATE_START -
package com.stock.console.notification;

import com.stock.console.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<NotificationFeed> notifications(
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "0") long seenId,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(repository.find(afterId, seenId, limit));
    }
}
// AI_GENERATE_END -
