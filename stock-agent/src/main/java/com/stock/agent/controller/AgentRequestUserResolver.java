// AI_GENERATE_START-----
package com.stock.agent.controller;

import com.stock.trading.web.AuthInterceptor;
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

    /**
     * 校验当前用户是否为系统管理员，并返回稳定操作人名称。
     *
     * @param request HTTP 请求
     * @return 管理员用户名
     * @throws IllegalStateException 当前用户未登录或不是管理员时抛出
     */
    public String requireAdminOperator(HttpServletRequest request) {
        String username = requireUsername(request);
        if (!"admin".equalsIgnoreCase(username)) {
            throw new IllegalStateException("当前用户无权修改 Agent 交易决策治理配置");
        }
        return username;
    }

    private Long requireUserId(HttpServletRequest request) {
        Object userId = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute(AuthInterceptor.AUTH_USER_ID_SESSION_KEY);
        if (userId instanceof Long value) {
            return value;
        }
        throw new IllegalStateException("当前登录用户不存在");
    }

    private String requireUsername(HttpServletRequest request) {
        Object username = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute(AuthInterceptor.AUTH_USERNAME_SESSION_KEY);
        if (username instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new IllegalStateException("当前登录用户不存在");
    }
}
// AI_GENERATE_END-----
