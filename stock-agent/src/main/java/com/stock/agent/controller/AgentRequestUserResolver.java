// AI_GENERATE_START----
package com.stock.agent.controller;

import com.stock.trading.dto.AuthUserDto;
import com.stock.trading.web.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
        AuthUserDto authUser = requireUser(request);
        if (authUser.getId() != null) {
            return "user:" + authUser.getId();
        }
        throw new IllegalStateException("当前登录用户不存在");
    }

    /**
     * 校验当前用户是否为系统管理员，并返回稳定操作人名称。
     *
     * @param request HTTP 请求
     * @return 管理员用户名
     * @throws IllegalStateException 当前用户未登录或不是管理员时抛出
     */
    public String requireAdminOperator(HttpServletRequest request) {
        AuthUserDto authUser = requireUser(request);
        if (!"admin".equalsIgnoreCase(authUser.getUsername())) {
            throw new IllegalStateException("当前用户无权修改 Agent 交易决策治理配置");
        }
        return authUser.getUsername();
    }

    private AuthUserDto requireUser(HttpServletRequest request) {
        Object user = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute(AuthInterceptor.AUTH_USER_SESSION_KEY);
        if (user instanceof AuthUserDto authUser
                && authUser.getId() != null
                && StringUtils.hasText(authUser.getUsername())) {
            return authUser;
        }
        throw new IllegalStateException("当前登录用户不存在");
    }
}
// AI_GENERATE_END----
