package online.mwang.foundtrading.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.param.LoginParam;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/31 15:01
 * @description: LoginController
 */
@Slf4j
@RestController
public class LoginController {

    private static final Random RANDOM = new Random();
    private static final String USERNAME = "admin";
    private static final String SDF = "MMdd";
    private static final Integer TOKEN_LENGTH = 32;
    private static final Integer TOKEN_EXPIRE_MINUTES = 30;
    private static final Integer TOKEN_EXPIRE_HOURS = 4;
    private static JSONObject user;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static String generateToken(int length) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            final int c = RANDOM.nextInt('z' - '0');
            builder.append((char) ('0' + c));
        }
        return builder.toString();
    }

    @PostMapping("/login/account")
    public Response<String> login(@RequestBody LoginParam param) {
        log.info("username is {}, password is {}", param.getUsername(), param.getPassword());
        final String monthDate = new SimpleDateFormat(SDF).format(new Date());
        final String reverseDate = new StringBuilder(monthDate).reverse().toString();
        if (USERNAME.equalsIgnoreCase(param.getUsername()) && reverseDate.equals(param.getPassword())) {
            final String token = generateToken(TOKEN_LENGTH);
            stringRedisTemplate.opsForValue().set(token, "", TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
            return Response.success(token);
        } else {
            return Response.fail(1101, "用户名或密码错误!");
        }
    }

    @PostMapping("/login/outLogin")
    public Response<Void> outLogin(HttpServletRequest request) {
        final String token = request.getHeader("token");
        stringRedisTemplate.opsForValue().getAndDelete(token);
        return Response.success();
    }

    @SneakyThrows
    @GetMapping("/currentUser")
    public Response<JSONObject> currentUser(LoginParam param) {
        JSONObject user = new JSONObject();
        user.put("name", "Admin");
        user.put("avatar", "https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png");
        return Response.success(user);
    }
}
