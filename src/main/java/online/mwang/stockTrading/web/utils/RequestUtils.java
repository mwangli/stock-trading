package online.mwang.stockTrading.web.utils;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.impl.ZXStockServiceImpl;
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
//            CloseableHttpClient client = HttpClients.createDefault();
//            MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
//            formParam.forEach((k, v) -> entityBuilder.addTextBody(k, String.valueOf(v)));
//            HttpPost post = new HttpPost(url);
//            post.setEntity(entityBuilder.build());
//            CloseableHttpResponse response = client.execute(post);
//            String result = EntityUtils.toString(response.getEntity());
            // 发送 POST 请求
            String response = HttpUtil.createPost(url).form(formParam).execute().body();
            if (logs) log.info(response);
            JSONObject res = JSONObject.parseObject(response);
            String newToken = res.getString("TOKEN");
            if (newToken != null) stockService.setToken(newToken);
            String code = res.getString("ERRORNO");
            if ("-204007".equals(code) || "-204009".equals(code)) {
                log.info("检测到无效token，尝试重新登录...");
                stockService.clearToken();
                String token = stockService.getToken();
            }
            return res;
        } catch (JSONException e) {
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


    public  void test111() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("Token", "Y63ZNw35@17AC-4901690N2zeuo");
        params.put("MobileCode", "13278828091");
        params.put("Reqno", System.currentTimeMillis());
        params.put("ReqlinkType", 1);
        params.put("newindex", 1);
        params.put("action", 115);
        params.put("StartPos", 0);
        params.put("MaxCount", 100);
        params.put("BeginDate", "20241119");
        params.put("EndDate", "20250216");
        params.put("intacttoserver", "@ClZvbHVtZUluZm8JAAAAMTdBQy00OTAx");
        params.put("cfrom", "H5");
        params.put("tfrom", "PC");
        JSONObject response = request(params);
        log.info("{}", response);
    }
}

