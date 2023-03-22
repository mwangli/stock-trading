package online.mwang.foundtrading.controller;

import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.bean.param.LoginParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/21 14:37
 * @description: TestController
 */
@Slf4j
@RestController
public class TestController {

    @GetMapping("test")
    public String test() {
        return "test:v1.2";
    }

    @PostMapping("login/account")
    public String login(@RequestBody LoginParam param) {
        log.info("username is {}, password is {}", param.getUsername(), param.getPassword());
        return "test:v1.2";
    }
}
