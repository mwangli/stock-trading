// AI_GENERATE_START --
package com.stock.agent.shared.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.shared.api.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Agent 对外接口共享登录态拦截器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
@RequiredArgsConstructor
public class AgentAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    /**
     * 校验 Redis 共享会话中的用户身份。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 请求处理器
     * @return true 表示已登录
     * @throws IOException 写入未登录响应失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (request.getSession(false) != null
                && request.getSession(false).getAttribute(AgentSessionAttributes.AUTH_USER_ID) instanceof Long
                && request.getSession(false).getAttribute(AgentSessionAttributes.AUTH_USERNAME) instanceof String) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ResponseDTO.error("请先登录")));
        return false;
    }
}
// AI_GENERATE_END -
