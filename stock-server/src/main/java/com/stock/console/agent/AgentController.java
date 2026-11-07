// AI_GENERATE_START ---
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

    /**
     * 创建智能对话接口控制器。
     *
     * @param agent 智能对话业务服务
     */
    public AgentController(AgentService agent) {
        this.agent = agent;
    }

    /**
     * 查询智能对话当前运行状态。
     *
     * @return 智能对话配置和运行状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(agent.status());
    }

    /**
     * 创建当前用户的临时智能对话会话。
     *
     * @param request 会话标题请求
     * @param httpSession 当前 HTTP 会话
     * @return 新建会话信息
     */
    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> create(
            @RequestBody(required = false) SessionRequest request,
            HttpSession httpSession) {
        return ApiResponse.ok(agent.createSession(httpSession, request == null ? null : request.title()));
    }

    /**
     * 查询当前用户可见的临时智能对话会话。
     *
     * @param httpSession 当前 HTTP 会话
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions(HttpSession httpSession) {
        return ApiResponse.ok(agent.sessions(httpSession));
    }

    /**
     * 查询指定智能对话会话的历史消息。
     *
     * @param sessionId 会话 ID
     * @param httpSession 当前 HTTP 会话
     * @return 历史消息列表
     */
    @GetMapping("/sessions/{sessionId}/history")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable String sessionId, HttpSession httpSession) {
        return ApiResponse.ok(agent.history(httpSession, sessionId));
    }

    /**
     * 根据会话来源处理问题，未指定来源时保持电脑端完整回答。
     *
     * @param request 会话、问题和来源参数
     * @param httpSession 当前 HTTP 会话
     * @return 智能对话回答及证据引用
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@Valid @RequestBody ChatRequest request, HttpSession httpSession) {
        return ApiResponse.ok(agent.chat(httpSession, request.sessionId(), request.question(), request.resource()));
    }

    /**
     * 表示创建会话时提交的标题。
     *
     * @param title 会话标题
     * @return 会话标题请求记录
     */
    public record SessionRequest(String title) {
    }

    /**
     * 表示智能对话请求及其会话来源。
     *
     * @param sessionId 会话 ID
     * @param question 用户问题
     * @param resource 会话来源
     * @param max 用户问题允许的最大字符数
     * @return 智能对话请求记录
     */
    public record ChatRequest(
            String sessionId,
            @NotBlank @Size(max = AgentService.MAX_QUESTION_LENGTH) String question,
            String resource) {
    }
}
// AI_GENERATE_END ---
