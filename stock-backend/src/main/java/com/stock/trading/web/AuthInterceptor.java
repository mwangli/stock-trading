// AI_GENERATE_START-------
package com.stock.trading.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.base.api.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 业务接口登录态拦截器，基于服务端 HttpSession 校验当前用户。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    /** 服务端会话中的当前用户主键属性名。 */
    public static final String AUTH_USER_ID_SESSION_KEY = "AUTH_USER_ID";

    /** 服务端会话中的当前用户名属性名。 */
    public static final String AUTH_USERNAME_SESSION_KEY = "AUTH_USERNAME";

    /** 服务端会话中的当前用户显示名称属性名。 */
    public static final String AUTH_DISPLAY_NAME_SESSION_KEY = "AUTH_DISPLAY_NAME";

    private final ObjectMapper objectMapper;

    /**
     * 校验当前请求是否存在有效登录会话。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 请求处理器
     * @return true 表示允许继续处理，false 表示已返回未登录响应
     * @throws IOException 写入未登录响应失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (request.getSession(false) != null
                && request.getSession(false).getAttribute(AUTH_USER_ID_SESSION_KEY) instanceof Long
                && request.getSession(false).getAttribute(AUTH_USERNAME_SESSION_KEY) instanceof String) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ResponseDTO.error("请先登录")));
        return false;
    }
}
// AI_GENERATE_END-------
