package com.stock.dataCollector.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 证券平台 API 客户端
 * 对接证券平台获取股票数据
 */
@Slf4j
@Component
public class SecuritiesClient {

    private final RestTemplate restTemplate;

    /**
     * 证券平台 API 基础地址
     */
    private static final String API_BASE_URL = "https://weixin.citicsinfo.com/reqxml";

    /**
     * 动态Cookie - H5Token (每次启动时可能需要更新)
     */
    private String h5Token = "";

    public SecuritiesClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 设置H5Token (用于动态更新认证信息)
     */
    public void setH5Token(String token) {
        this.h5Token = token;
        log.info("H5Token已更新");
    }

    /**
     * 构建通用请求头
     * 模拟移动端浏览器请求
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        
        // Accept头
        headers.setAccept(List.of(
            MediaType.parseMediaType("application/json"),
            MediaType.parseMediaType("text/javascript"),
            MediaType.ALL
        ));
        
        // 内容类型
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        // 模拟移动端浏览器 User-Agent
        headers.set("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1");
        
        // 来源页面
        headers.set("Referer", "https://weixin.citicsinfo.com/tztweb/hq/index.html");
        
        // CORS相关
        headers.set("sec-ch-ua", "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\"");
        headers.set("sec-ch-ua-mobile", "?1");
        headers.set("sec-ch-ua-platform", "\"iOS\"");
        headers.set("sec-fetch-dest", "empty");
        headers.set("sec-fetch-mode", "cors");
        headers.set("sec-fetch-site", "same-origin");
        
        // 语言
        headers.set("Accept-Language", "zh-CN,zh;q=0.9");
        
        // X-Requested-With (标识AJAX请求)
        headers.set("X-Requested-With", "XMLHttpRequest");
        
        // Cache-Control
        headers.set("Cache-Control", "no-cache");
        
        // Cookie (包含认证信息)
        String cookie = buildCookie();
        if (!cookie.isEmpty()) {
            headers.set("Cookie", cookie);
        }
        
        return headers;
    }

    /**
     * 构建Cookie字符串
     */
    private String buildCookie() {
        StringBuilder cookie = new StringBuilder();
        cookie.append("H5Error=0; ");
        cookie.append("has_user_token_cookie=0; ");
        
        // 动态H5Token
        if (h5Token != null && !h5Token.isEmpty()) {
            cookie.append("H5Token=").append(h5Token).append("; ");
        }
        
        return cookie.toString();
    }

    /**
     * 构建请求参数Map
     */
    private MultiValueMap<String, String> buildParams(Map<String, Object> paramMap) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        
        // 通用参数
        params.add("cfrom", "H5");
        params.add("tfrom", "PC");
        params.add("newindex", "1");
        params.add("reqno", String.valueOf(System.currentTimeMillis()));
        
        // 业务参数
        if (paramMap != null) {
            for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                params.add(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        
        return params;
    }

    /**
     * 发送请求到证券平台 (用于获取数据列表)
     */
    @SuppressWarnings("unchecked")
    private JSONArray requestDataList(Map<String, Object> paramMap) {
        try {
            String url = API_BASE_URL + "?action=1230";
            
            HttpHeaders headers = buildHeaders();
            MultiValueMap<String, String> params = buildParams(paramMap);
            
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);
            
            log.debug("请求URL: {}", url);
            log.debug("请求参数: {}", params);
            
            Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);
            
            if (response == null) {
                log.warn("证券平台返回为空");
                return new JSONArray();
            }
            
            log.debug("响应数据: {}", response.keySet());
            
            // 获取 BINDATA -> results
            Object bindataObj = response.get("BINDATA");
            JSONObject bindataJson = null;
            
            if (bindataObj instanceof String bindataStr) {
                try {
                    bindataJson = JSONObject.parseObject(bindataStr);
                } catch (Exception e) {
                    log.warn("解析证券平台 BINDATA 字符串失败: {}", bindataStr, e);
                }
            } else if (bindataObj instanceof Map) {
                try {
                    bindataJson = new JSONObject((Map<String, Object>) bindataObj);
                } catch (Exception e) {
                    log.warn("解析证券平台 BINDATA Map 失败", e);
                }
            }

            if (bindataJson != null) {
                Object results = bindataJson.get("results");
                if (results instanceof JSONArray resultsArray) {
                    return resultsArray;
                }
                if (results instanceof List) {
                    JSONArray resultsArray = new JSONArray();
                    resultsArray.addAll((List<?>) results);
                    return resultsArray;
                }
            }
            
            return new JSONArray();
        } catch (Exception e) {
            log.error("请求证券平台数据列表失败", e);
            return new JSONArray();
        }
    }

    /**
     * 获取股票历史价格数据
     * 
     * @param stockCode 股票代码
     * @param count     获取天数
     * @return 历史价格数据列表
     */
    public JSONArray getHistoryPrices(String stockCode, int count) {
        // 平台接口不支持按天数指定历史区间，忽略 count 参数，
        // 默认返回最近若干年（约 3 年）的日 K 数据。
        log.info("获取股票 {} 的历史价格数据(平台默认区间)", stockCode);
        
        Map<String, Object> paramMap = new java.util.HashMap<>(10);
        paramMap.put("c.funcno", 20009);
        paramMap.put("c.version", 1);
        paramMap.put("c.stock_code", stockCode);
        paramMap.put("c.type", "day");

        // 为了避免对服务器造成过高压力，简单限流：每次请求之间休眠 0.5 秒
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }

        return requestDataList(paramMap);
    }
    /**
     * 获取指定日期范围的历史价格数据 (增量更新)
     * 
     * @param stockCode 股票代码
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 历史价格数据列表
     */
    public JSONArray getHistoryPrices(String stockCode, LocalDate startDate, LocalDate endDate) {
        log.info("获取股票 {} 的历史价格数据({} ~ {})", stockCode, startDate, endDate);
        
        Map<String, Object> paramMap = new java.util.HashMap<>(10);
        paramMap.put("c.funcno", 20009);
        paramMap.put("c.version", 1);
        paramMap.put("c.stock_code", stockCode);
        paramMap.put("c.type", "day");
        
        // 支持按日期范围拉取
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        if (startDate != null) {
            paramMap.put("c.begin_date", startDate.format(formatter));
        }
        if (endDate != null) {
            paramMap.put("c.end_date", endDate.format(formatter));
        }

        return requestDataList(paramMap);
    }


    /**
     * 获取所有股票列表
     * 
     * @return 股票列表数据
     */
    public JSONArray getStockList() {
        log.info("获取股票列表数据");
        
        Map<String, Object> paramMap = new java.util.HashMap<>();
        paramMap.put("c.funcno", 21000);
        paramMap.put("c.version", 1);
        paramMap.put("c.sort", 1);
        paramMap.put("c.order", 1);
        paramMap.put("c.type", "0:2:9:18");
        paramMap.put("c.curPage", 1);
        paramMap.put("c.rowOfPage", 5000);
        paramMap.put("c.field", "1:2:22:23:24:3:8:16:21:31");
        
        return requestDataList(paramMap);
    }

    /**
     * 获取股票实时价格
     * 
     * @param stockCode 股票代码
     * @return 实时价格
     */
    @SuppressWarnings("unchecked")
    public JSONObject getRealtimePrice(String stockCode) {
        log.debug("获取股票 {} 的实时价格", stockCode);
        
        try {
            String url = API_BASE_URL;
            
            HttpHeaders headers = buildHeaders();
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            
            // 通用参数
            params.add("cfrom", "H5");
            params.add("tfrom", "PC");
            params.add("newindex", "1");
            params.add("reqno", String.valueOf(System.currentTimeMillis()));
            
            // 业务参数
            params.add("stockcode", stockCode);
            params.add("action", "33");
            params.add("ReqlinkType", "1");
            params.add("Level", "1");
            params.add("UseBPrice", "1");
            
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);
            
            Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);
            
            if (response == null) {
                return null;
            }
            
            String errorNo = (String) response.get("ERRORNO");
            if (errorNo != null && !"0".equals(errorNo)) {
                log.error("获取实时价格失败: {}", response.get("MESSAGE"));
                return null;
            }
            
            JSONObject result = new JSONObject();
            result.put("price", response.get("PRICE"));
            return result;
        } catch (Exception e) {
            log.error("获取股票 {} 实时价格失败", stockCode, e);
            return null;
        }
    }
}