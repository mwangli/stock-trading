// AI_GENERATE_START -
package com.stock.console.agent;

import com.stock.console.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/development")
public class DevelopmentTaskController {

    private final DevelopmentTaskService tasks;

    public DevelopmentTaskController(DevelopmentTaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(tasks.status());
    }

    @PostMapping("/tasks")
    public ApiResponse<DevelopmentTaskView> create(@Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.ok(tasks.start(request.sessionId(), request.requirement()));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<DevelopmentTaskView> task(@PathVariable String taskId) {
        return ApiResponse.ok(tasks.task(taskId));
    }

    @GetMapping("/tasks/recent")
    public ApiResponse<List<DevelopmentTaskView>> recent() {
        return ApiResponse.ok(tasks.recent());
    }

    public record CreateTaskRequest(
            String sessionId,
            @NotBlank @Size(max = DevelopmentTaskService.MAX_REQUIREMENT_LENGTH) String requirement) {
    }
}
// AI_GENERATE_END -
