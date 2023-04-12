package online.mwang.foundtrading.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.base.Response;
import online.mwang.foundtrading.bean.param.LoginParam;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private static JSONObject user;
    private static final Random RANDOM = new Random();
    private static final String USERNAME = "admin";
    private static final SimpleDateFormat SDF = new SimpleDateFormat("MMdd");

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @PostMapping("/login/account")
    public Response<String> login(@RequestBody LoginParam param) {
        log.info("username is {}, password is {}", param.getUsername(), param.getPassword());
        final String format = SDF.format(new Date());
        final String reverseDate = new StringBuilder(format).reverse().toString();
        if (USERNAME.equalsIgnoreCase(param.getUsername()) && reverseDate.equals(param.getPassword())) {
            final String token = generateToken(18);
            redisTemplate.opsForValue().set(token, "", 30, TimeUnit.SECONDS);
            return Response.success(token);
        } else {
            return Response.fail(1101, "username or pass incorrect!");
        }
    }

    @PostMapping("/login/outLogin")
    public JSONObject outLogin() {
        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("data", new JSONObject());
        return res;
    }

    @SneakyThrows
    @GetMapping("/currentUser")
    public JSONObject currentUser(LoginParam param) {
        log.info("token is {}", param.getToken());
        if (user == null) {
            log.info("load user info ...");
            InputStream is = new ClassPathResource("json/current_user.json").getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            final StringBuilder stringBuilder = new StringBuilder();
            String s;
            while ((s = reader.readLine()) != null) {
                stringBuilder.append(s);
            }
            user = JSONObject.parseObject(stringBuilder.toString());
        }
        JSONObject res = new JSONObject();
        res.put("success", true);
        res.put("data", user);
        return res;
    }

    private static String generateToken(int length) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            final int c = RANDOM.nextInt('z' - '0');
            builder.append((char) ('0' + c));
        }
        return builder.toString();
    }
}
