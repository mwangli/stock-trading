package online.mwang.foundtrading.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

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
        return "test:v1.2.4";
    }

    @PostMapping("imageUpdate")
    public String imageUpdate(@RequestBody HashMap<String,Object> params) {
        log.info(JSONObject.toJSONString(params));
        return "success";
    }
}
