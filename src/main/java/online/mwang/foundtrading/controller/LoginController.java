package online.mwang.foundtrading.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.param.LoginParam;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

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

    @PostMapping("/login/account")
    public JSONObject login(@RequestBody LoginParam param) {
        log.info("username is {}, password is {}", param.getUsername(), param.getPassword());
        JSONObject res = new JSONObject();
        res.put("currentAuthority", "admin");
        res.put("status", "ok");
        res.put("type", "account");
        return res;
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
}
