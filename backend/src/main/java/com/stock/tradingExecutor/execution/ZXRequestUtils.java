package com.stock.tradingExecutor.execution;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 中信证券API请求工具类
 * 封装HTTP请求和Token管理
 */
@Slf4j
@Component
public class ZXRequestUtils {

    private static final String REQUEST_URL = "https://weixin.citicsinfo.com/reqxml";
    private static final int RETRY_TIMES = 10;

    /**
     * 使用本地内存缓存请求 Token，替代 Redis 存储
     */
    private volatile String token;
    private volatile long tokenExpireAtMs;

    /**
     * 构建通用请求参数
     */
    public Map<String, Object> buildParams(Map<String, Object> paramMap) {
        if (paramMap == null) {
            paramMap = new HashMap<>();
        }
        paramMap.put("cfrom", "H5");
        paramMap.put("tfrom", "PC");
        paramMap.put("newindex", "1");
        paramMap.put("MobileCode", "13278828091");
        paramMap.put("intacttoserver", "@ClZvbHVtZUluZm8JAAAANTI1QS00Qjc4");
        paramMap.put("reqno", System.currentTimeMillis());
        return paramMap;
    }

    /**
     * 获取Token
     */
    public String getToken() {
        long now = System.currentTimeMillis();
        if (token != null && now < tokenExpireAtMs) {
            return token;
        }
        return null;
    }

    /**
     * 设置Token
     */
    public void setToken(String token) {
        if (token != null) {
            this.token = token;
            // 默认缓存30分钟
            this.tokenExpireAtMs = System.currentTimeMillis() + 30L * 60L * 1000L;
        }
    }

    /**
     * 发送HTTP请求，返回JSONObject
     */
    public JSONObject request(Map<String, Object> formParam) {
        return request(REQUEST_URL, formParam, 0);
    }

    /**
     * 发送HTTP请求，返回JSONObject
     */
    public JSONObject request(String url, Map<String, Object> formParam, int times) {
        try {
            if (times > RETRY_TIMES) {
                log.error("[ZXBroker] 请求错误次数过多,请检查程序代码!");
                return new JSONObject();
            }

            // 发送 POST 请求
            String response = HttpUtil.createPost(url).form(formParam).execute().body();

            // 日志太长截取前1000个字符
            if (log.isDebugEnabled()) {
                log.debug("[ZXBroker] 响应: {}",
                    response.length() > 1000 ? response.substring(0, 1000) : response);
            }

            JSONObject res = JSONObject.parseObject(response);
            String newToken = res.getString("TOKEN");
            if (newToken != null) {
                setToken(newToken);
            }
            return res;
        } catch (JSONException e) {
            log.error("[ZXBroker] 请求数据异常: {}", e.getMessage());
            return new JSONObject();
        } catch (Exception e) {
            log.error("[ZXBroker] 请求异常: {}", e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * 发送HTTP请求，返回JSONArray (GRID0字段)
     */
    public JSONArray requestArray(Map<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL, formParam, 0);
        return res.getJSONArray("GRID0");
    }

    /**
     * 发送HTTP请求，返回JSONArray (BINDATA.results字段)
     */
    public JSONArray requestBindata(Map<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL + "?action=1230", formParam, 0);
        JSONObject data = res.getJSONObject("BINDATA");
        if (data != null && data.getJSONArray("results") != null) {
            return data.getJSONArray("results");
        }
        return new JSONArray();
    }

    /**
     * 构建带Token的参数
     */
    public Map<String, Object> buildParamsWithToken(Map<String, Object> paramMap) {
        Map<String, Object> params = buildParams(paramMap);
        params.put("token", getToken());
        params.put("reqno", System.currentTimeMillis());
        return params;
    }
}
