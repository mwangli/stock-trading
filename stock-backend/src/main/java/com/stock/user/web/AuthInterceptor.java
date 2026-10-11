// AI_GENERATE_START
package com.stock.user.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dataCollector.domain.dto.ResponseDTO;
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

    public static final String AUTH_USER_SESSION_KEY = "AUTH_USER";

    private final ObjectMapper objectMapper;

    /** 拦截未登录业务请求并返回统一响应。 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (request.getSession(false) != null
                && request.getSession(false).getAttribute(AUTH_USER_SESSION_KEY) != null) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ResponseDTO.error("请先登录")));
        return false;
    }
}
// AI_GENERATE_END
