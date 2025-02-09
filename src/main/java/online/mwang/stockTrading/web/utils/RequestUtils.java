package online.mwang.stockTrading.web.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.impl.ZXStockServiceImpl;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/5/22 10:24
 * @description: RequestUtils
 */
@Slf4j
@Component
public class RequestUtils {

    private static final String REQUEST_URL = "https://weixin.citicsinfo.com/reqxml";

    private static final int RETRY_TIMES = 10;
    public boolean logs = true;
    @Resource
    ApplicationContext applicationContext;

    @SneakyThrows
    public JSONObject request(String url, HashMap<String, Object> formParam, int times) {
        try {
            if (times > RETRY_TIMES) {
                log.info("请求错误次数过多,请检查程序代码!");
                return new JSONObject();
            }
            ZXStockServiceImpl stockService = applicationContext.getBean(ZXStockServiceImpl.class);
            CloseableHttpClient client = HttpClients.createDefault();
            MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
            formParam.forEach((k, v) -> entityBuilder.addTextBody(k, String.valueOf(v)));
            HttpPost post = new HttpPost(url);
            post.setEntity(entityBuilder.build());
            CloseableHttpResponse response = client.execute(post);
            String result = EntityUtils.toString(response.getEntity());
            if (logs) log.info(result);
            final JSONObject res = JSONObject.parseObject(result);
            String newToken = res.getString("TOKEN");
            if (newToken != null) stockService.setToken(newToken);
            String code = res.getString("ERRORNO");
            if ("-204007".equals(code) || "-204009".equals(code)) {
                log.info("检测到无效token，尝试重新登录...");
                stockService.clearToken();
                String token = stockService.getToken();
                if (token != null ) {
                    formParam.put("token", token);
//                    return request(url, formParam, ++times);
                }
            }
            return res;
        } catch (JSONException e) {
            log.info("请求数据异常，正在重新请求数据。");
            return request(url, formParam, ++times);
        }
    }

    @SneakyThrows
    public JSONObject request(HashMap<String, Object> formParam) {
        return request(REQUEST_URL, formParam, 0);
    }


    @SneakyThrows
    public JSONArray request2(HashMap<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL, formParam, 0);
        return res.getJSONArray("GRID0");
    }

    @SneakyThrows
    public JSONArray request3(HashMap<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL.concat("?action=1230"), formParam, 0);
        final JSONObject data = res.getJSONObject("BINDATA");
        if (data != null && data.getJSONArray("results") != null) {
            return data.getJSONArray("results");
        }
        return new JSONArray();
    }
}

