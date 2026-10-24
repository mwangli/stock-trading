// AI_GENERATE_START --
package com.stock.agent.shared.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.shared.api.ResponseDTO;
import com.stock.agent.shared.config.InternalApiProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Agent 内部接口令牌拦截器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
@RequiredArgsConstructor
public class InternalTokenInterceptor implements HandlerInterceptor {

    /** 内部接口令牌请求头。 */
    public static final String TOKEN_HEADER = "X-Internal-Token";

    private final InternalApiProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 校验内部服务调用令牌。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 请求处理器
     * @return true 表示允许继续处理
     * @throws IOException 写入错误响应失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String configured = properties.getToken();
        if (configured == null || configured.isBlank()) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "内部接口令牌未配置");
            return false;
        }
        String supplied = request.getHeader(TOKEN_HEADER);
        if (supplied == null || !MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "内部接口令牌无效");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ResponseDTO.error(message)));
    }
}
// AI_GENERATE_END -
