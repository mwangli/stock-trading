package online.mwang.foundtrading.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 10:24
 * @description: RequestUtils
 */
@Slf4j
@Component
public class RequestUtils {

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();


    @SneakyThrows
    public JSONArray request(String formParam) {
        HttpRequest postRequest = HttpRequest.newBuilder()
               .POST(HttpRequest.BodyPublishers.ofString(formParam))
                .uri(URI.create("https://weixin.citicsinfo.com/reqxml?action=1230"))
                .build();
        HttpResponse<String> response = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
//        log.info(responseBody);
        final JSONObject res = JSONObject.parseObject(responseBody);
        final JSONObject data = res.getJSONObject("BINDATA");
        return data.getJSONArray("results");
    }

    @SneakyThrows
    public JSONArray request2(String formParam) {
        HttpRequest postRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(formParam))
                .uri(URI.create("https://weixin.citicsinfo.com/reqxml"))
                .build();
        HttpResponse<String> response = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        log.info(responseBody);
        final JSONObject res = JSONObject.parseObject(responseBody);
        return res.getJSONArray("GRID0");
    }

    @SneakyThrows
    public JSONObject request3(String formParam) {
        HttpRequest postRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(formParam))
                .uri(URI.create("https://weixin.citicsinfo.com/reqxml"))
                .build();
        HttpResponse<String> response = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        log.info(responseBody);
        return JSONObject.parseObject(responseBody);
    }
}
