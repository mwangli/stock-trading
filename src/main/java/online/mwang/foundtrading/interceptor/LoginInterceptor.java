package online.mwang.foundtrading.interceptor;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.base.Response;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/4/12 11:13
 * @description: LoginIntecerptor
 */
@Slf4j
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        final String token = request.getHeader("token");
        log.info("token is {}", token);
        final String redisToken = stringRedisTemplate.opsForValue().get(token);
        if (redisToken != null) {
            return true;
        } else {
            returnJson(response, Response.fail(1001, "登录失效,请重新登录。"));
            return false;
        }
    }

    private void returnJson(HttpServletResponse response, Response<?> result) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.print(JSONObject.toJSONString(result));
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
