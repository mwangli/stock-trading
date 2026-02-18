package online.mwang.stockTrading.core.utils;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * HTTP 请求工具类
 */
@Slf4j
public class RequestUtils {

    private static final String BASE_URL = "https://stock.trade.10jqka.com.cn";

    public static JSONObject request(HashMap<String, Object> paramMap) {
        try {
            String result = HttpUtil.post(BASE_URL, paramMap);
            return JSONObject.parseObject(result);
        } catch (Exception e) {
            log.error("HTTP请求失败: {}", e.getMessage());
            return new JSONObject();
        }
    }

    public static String get(String url) {
        try {
            return HttpUtil.get(url);
        } catch (Exception e) {
            log.error("HTTP GET请求失败: {}", e.getMessage());
            return "";
        }
    }
}
