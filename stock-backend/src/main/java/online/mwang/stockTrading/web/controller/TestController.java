package online.mwang.stockTrading.web.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/21 14:37
 * @description: TestController
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    @SneakyThrows
    @GetMapping("/alive")
    public boolean alive() {
        // K8S存活探针
        return true;
    }

    @SneakyThrows
    @PostMapping("/imageUpdate")
    public String imageUpdate(@RequestBody HashMap<String, Object> params) {
        log.info(JSONObject.toJSONString(params));
        return "success";
    }
}
