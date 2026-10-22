// AI_GENERATE_START -
package com.stock.strategyAgent.api;

import com.stock.tradingExecutor.domain.dto.AuthUserDto;
import com.stock.tradingExecutor.web.AuthInterceptor;
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
        Object user = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute(AuthInterceptor.AUTH_USER_SESSION_KEY);
        if (user instanceof AuthUserDto authUser && authUser.getId() != null) {
            return "user:" + authUser.getId();
        }
        throw new IllegalStateException("当前登录用户不存在");
    }
}
// AI_GENERATE_END -
