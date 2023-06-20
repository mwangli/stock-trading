package online.mwang.foundtrading.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.foundtrading.job.DailyJob;
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
    private static final int RETRY_REQUEST_TIMES = 3;

    @Resource
    ApplicationContext applicationContext;

    private Boolean logs = false;

    public void setLogs(Boolean logs) {
        this.logs = logs;
    }

    @SneakyThrows
    public JSONObject request(String url, HashMap<String, Object> formParam) {
        int times = 0;
        while (times++ < RETRY_REQUEST_TIMES) {
            CloseableHttpClient client = HttpClients.createDefault();
            MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
            formParam.forEach((k, v) -> entityBuilder.addTextBody(k, String.valueOf(v)));
            HttpPost post = new HttpPost(url);
            post.setEntity(entityBuilder.build());
            CloseableHttpResponse response = client.execute(post);
            String result = EntityUtils.toString(response.getEntity());
            if (logs) log.info(result);
            final JSONObject res = JSONObject.parseObject(result);
            String code = res.getString("ERRORNO");
            if ("-204007".equals(code)) {
                log.info("检测到无效token，尝试重新登录...");
                String token = applicationContext.getBean(DailyJob.class).getToken();
                log.info("登录后再次发起请求。");
                formParam.put("token", token);
                continue;
            }
            return res;
        }
        log.info("尝试{}次请求失败，请检查程序代码", RETRY_REQUEST_TIMES);
        return new JSONObject();
    }

    @SneakyThrows
    public JSONObject request(HashMap<String, Object> formParam) {
        return request(REQUEST_URL, formParam);
    }


    @SneakyThrows
    public JSONArray request2(HashMap<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL, formParam);
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
