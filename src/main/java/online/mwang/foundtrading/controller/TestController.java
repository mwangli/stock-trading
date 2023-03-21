package online.mwang.foundtrading.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/21 14:37
 * @description: TestController
 */
@RestController
public class TestController {

    @GetMapping("test")
    public String test() {
        return "test:v1.2";
    }
}
