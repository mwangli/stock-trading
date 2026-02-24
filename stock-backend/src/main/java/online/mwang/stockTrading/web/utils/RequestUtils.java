package online.mwang.stockTrading.web.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Slf4j
@Component
public class RequestUtils {

    private static final String REQUEST_URL = "https://weixin.citicsinfo.com/reqxml";

    private static final int RETRY_TIMES = 10;

    @SneakyThrows
    public JSONObject request(String url, HashMap<String, Object> formParam, int times) {
        try {
            if (times > RETRY_TIMES) {
                log.info("请求错误次数过多,请检查程序代码!");
                return new JSONObject();
            }
            cn.hutool.http.HttpResponse response = cn.hutool.http.HttpUtil.createPost(url)
                .form(formParam)
                .execute();
            String body = response.body();
            if (body != null && body.length() > 1000) {
                log.info(body.substring(0, 1000));
            } else {
                log.info(body);
            }
            return JSONObject.parseObject(body);
        } catch (Exception e) {
            log.info("message:{}", e.getMessage());
            log.info("请求数据异常，请检查程序代码。");
            return new JSONObject();
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
