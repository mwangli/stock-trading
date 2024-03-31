package online.mwang.stockTrading.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.bean.base.Response;
import online.mwang.stockTrading.bean.param.LoginParam;
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
    private static final Integer TOKEN_EXPIRE_HOURS = 4;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static String generateToken() {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < TOKEN_LENGTH; i++) {
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
        if ("test".equals(param.getUsername())){
            final String token = generateToken();
            JSONObject user = new JSONObject();
            user.put("name", "test");
            user.put("avatar", "https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png");
            user.put("access", "test");
            stringRedisTemplate.opsForValue().set(token, JSON.toJSONString(user), TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
            return Response.success(token);
        }
        if ("guest".equals(param.getUsername())){
            final String token = generateToken();
            JSONObject user = new JSONObject();
            user.put("name", "guest");
            user.put("avatar", "https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png");
            user.put("access", "guest");
            stringRedisTemplate.opsForValue().set(token, JSON.toJSONString(user), TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
            return Response.success(token);
        }
        if  ( USERNAME.equalsIgnoreCase(param.getUsername()) && reverseDate.equals(param.getPassword())) {
            final String token = generateToken();
            JSONObject user = new JSONObject();
            user.put("name", "admin");
            user.put("avatar", "https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png");
            user.put("access", "admin");
            stringRedisTemplate.opsForValue().set(token, JSON.toJSONString(user), TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
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
    public Response<JSONObject> currentUser(HttpServletRequest request) {
        final String token = request.getHeader("token");
        final String user = stringRedisTemplate.opsForValue().get(token);
//        JSONObject user = new JSONObject();
//        user.put("name", "Admin");
//        user.put("avatar", "https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png");
//        user.put("access", "admin");
        return Response.success(JSON.parseObject(user));
    }
}
