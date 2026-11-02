// AI_GENERATE_START -
package com.stock.console.agent;

import java.util.List;

public record DevelopmentTaskView(
        String taskId,
        String sessionId,
        String requirement,
        DevelopmentTaskStatus status,
        int buildAttempt,
        List<String> changedFiles,
        String commitSha,
        Long workflowRunId,
        String workflowUrl,
        String progress,
        String failureMessage,
        String createdAt,
        String updatedAt) {
}
// AI_GENERATE_END -
