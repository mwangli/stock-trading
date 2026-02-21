package online.mwang.stockTrading.interceptor;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.dto.Response;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 授权拦截器
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        final String user = (String) request.getAttribute("user");
        if (!"admin".equals(user)) {
            return returnJson(response, Response.fail(2011, "对不起，您没有操作权限！"));
        } else {
            return true;
        }
    }

    private boolean returnJson(HttpServletResponse response, Response<?> result) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.print(JSONObject.toJSONString(result));
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return false;
    }
}
