package online.mwang.stockTrading.web.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 10:24
 * @description: RequestUtils
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestUtils {

    public static final String REQUEST_URL = "https://weixin.citicsinfo.com/reqxml";
    private final StringRedisTemplate redisTemplate;
    public boolean logs = true;
    @Value("${PROFILE}")
    private String profile;

    @SneakyThrows
    public JSONObject request(String url, HashMap<String, Object> formParam) {
        String result = "";
        try {
            CloseableHttpClient client = HttpClients.createDefault();
            MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
            formParam.forEach((k, v) -> entityBuilder.addTextBody(k, String.valueOf(v)));
            HttpPost post = new HttpPost(url);
            post.setEntity(entityBuilder.build());
            CloseableHttpResponse response = client.execute(post);
            result = EntityUtils.toString(response.getEntity());
            if (logs) log.info(result);
            return JSONObject.parseObject(result);
        } catch (Exception e) {
            log.info("请求失败，返回数据为：{}", result);
        }
        return null;
    }

    @SneakyThrows
    public JSONObject request(HashMap<String, Object> formParam) {
        return request(REQUEST_URL, formParam);
    }


    @SneakyThrows
    public JSONArray request2(HashMap<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL, formParam);
        if ("-204009".equals(res.getString("ERRORNO"))) {
            log.info("TOKEN已经失效，正在重新登录...");
            redisTemplate.opsForValue().getAndDelete("requestToken");
            res = request(REQUEST_URL, formParam);
        }
        return res.getJSONArray("GRID0");
    }

    @SneakyThrows
    public JSONArray request3(HashMap<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL.concat("?action=1230"), formParam);
        final JSONObject data = res.getJSONObject("BINDATA");
        if (data != null && data.getJSONArray("results") != null) {
            return data.getJSONArray("results");
        }
        return new JSONArray();
    }
}
