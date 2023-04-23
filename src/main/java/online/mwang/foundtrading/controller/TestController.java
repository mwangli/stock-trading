package online.mwang.foundtrading.controller;

import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.utils.CommandUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
        return "test:v2.9.2";
    }

    @SneakyThrows
    @PostMapping("imageUpdate")
    public String imageUpdate(@RequestBody HashMap<String, Object> params) {
        log.info(JSONObject.toJSONString(params));
        CommandUtils.run("kubectl get pod");
        return "success";
    }


    @SneakyThrows
    public static void main(String[] args) {

        final CookieManager cookieManager = new CookieManager();
        cookieManager.getCookieStore().add(URI.create("weixin.citicsinfo.com"),
                new HttpCookie("H5Token", "NajcI750M9zeg2w7O0TaQe4bMcj8Q1zdNdj6Ee51M2D7Aa15O1T6g2waMcz2Mc22"));

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
//                .cookieHandler(cookieManager)
                .build();

//        HttpRequest getRequest = HttpRequest.newBuilder()
//                .GET()
//                .uri(URI.create("http://www.flydean.com"))
//                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36")
//                .build();


        HttpRequest postRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString("c.funcno=20001&c.version=1&c.stock_code=000001&c.market=SH&c.start=240&c.cfrom=H5&c.tfrom=PC&c.CHANNEL="))
                .uri(URI.create("https://weixin.citicsinfo.com/reqxml?action=1230"))
                .build();


        HttpResponse<String> response = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        log.info(responseBody);
    }

}
