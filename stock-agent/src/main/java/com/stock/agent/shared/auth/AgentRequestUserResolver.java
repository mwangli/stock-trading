// AI_GENERATE_START---------
package com.stock.agent.shared.auth;

import com.stock.agent.shared.auth.AgentSessionAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Agent 接口认证用户解析器。
 * 统一从服务端 HttpSession 获取当前用户，禁止采信请求体中的用户范围。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Component
public class AgentRequestUserResolver {

    /**
     * 返回当前认证用户的稳定 Agent 数据范围。
     *
     * @param request HTTP 请求
     * @return 由用户主键生成的 Agent 用户范围
     * @throws IllegalStateException 当前请求不存在有效登录用户时抛出
     */
    public String requireUserScope(HttpServletRequest request) {
        return "user:" + requireUserId(request);
    }

    private Long requireUserId(HttpServletRequest request) {
        Object userId = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute(AgentSessionAttributes.AUTH_USER_ID);
        if (userId instanceof Long value) {
            return value;
        }
        throw new IllegalStateException("当前登录用户不存在");
    }
}
// AI_GENERATE_END--------
