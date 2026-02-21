package online.mwang.stockTrading.interceptor;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.dto.Response;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 登录拦截器
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        final String token = request.getHeader("token");
        if (token == null) {
            return returnJson(response, Response.fail(1011, "无token,请使用token访问。"));
        }
        final String redisToken = stringRedisTemplate.opsForValue().get(token);
        if (redisToken != null) {
            final String user = JSONObject.parseObject(redisToken).getString("name");
            request.setAttribute("user", user);
            return true;
        } else {
            return returnJson(response, Response.fail(1001, "登录失效,请重新登录。"));
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
