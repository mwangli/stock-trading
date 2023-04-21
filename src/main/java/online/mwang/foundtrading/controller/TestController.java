package online.mwang.foundtrading.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
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

    @SneakyThrows
    @GetMapping("test")
    public String test() {
        return "test:v2.5";
    }

    @SneakyThrows
    @PostMapping("imageUpdate")
    public String imageUpdate(@RequestBody HashMap<String, Object> params) {
        log.info(JSONObject.toJSONString(params));
        final Process process = Runtime.getRuntime().exec("kubectl get pod -A");
        log.info(JSONObject.toJSONString(process));
        return "success";
    }


    @SneakyThrows
    public static void main(String[] args) {
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpGet httpGet = new HttpGet("http://test:8080/test");
        while (true) {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            // 从响应模型中获取响应实体
            final HttpEntity entity = response.getEntity();
            System.out.println("响应内容为:" + EntityUtils.toString(entity));
            Thread.sleep(1000);
        }
    }

}
