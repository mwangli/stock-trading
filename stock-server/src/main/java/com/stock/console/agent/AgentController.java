// AI_GENERATE_START --
package com.stock.console.agent;

import com.stock.console.web.ApiResponse;
import jakarta.servlet.http.HttpSession;
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
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agent;

    public AgentController(AgentService agent) {
        this.agent = agent;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(agent.status());
    }

    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> create(
            @RequestBody(required = false) SessionRequest request,
            HttpSession httpSession) {
        return ApiResponse.ok(agent.createSession(httpSession, request == null ? null : request.title()));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(HttpSession httpSession) {
        return ApiResponse.ok(agent.sessions(httpSession));
    }

    @GetMapping("/sessions/{sessionId}/history")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable String sessionId, HttpSession httpSession) {
        return ApiResponse.ok(agent.history(httpSession, sessionId));
    }

    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@Valid @RequestBody ChatRequest request, HttpSession httpSession) {
        return ApiResponse.ok(agent.chat(httpSession, request.sessionId(), request.question()));
    }

    public record SessionRequest(String title) {
    }

    public record ChatRequest(String sessionId, @NotBlank @Size(max = AgentService.MAX_QUESTION_LENGTH) String question) {
    }
}
// AI_GENERATE_END --
