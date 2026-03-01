package com.stock.dataCollector.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 证券平台 API 客户端
 * 对接证券平台获取股票数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecuritiesClient {

    private final RestTemplate restTemplate;

    /**
     * 证券平台 API 基础地址
     * 来源：原项目 RequestUtils.REQUEST_URL
     */
    private static final String API_BASE_URL = "https://weixin.citicsinfo.com/reqxml";

    /**
     * 构建请求参数
     */
    private HashMap<String, Object> buildParams(HashMap<String, Object> paramMap) {
        if (paramMap == null) {
            return new HashMap<>();
        }
        paramMap.put("cfrom", "H5");
        paramMap.put("tfrom", "PC");
        paramMap.put("newindex", "1");
        paramMap.put("reqno", System.currentTimeMillis());
        return paramMap;
    }

    /**
     * 发送请求到证券平台 (用于获取数据列表)
     */
    @SuppressWarnings("unchecked")
    private JSONArray requestDataList(HashMap<String, Object> paramMap) {
        try {
            String url = API_BASE_URL.concat("?action=1230");
            Map<String, Object> response = restTemplate.postForObject(url, paramMap, Map.class);
            
            if (response == null) {
                log.warn("证券平台返回为空");
                return new JSONArray();
            }
            
            // 获取 BINDATA -> results
            // 实测证券平台返回的 BINDATA 可能是 JSON 字符串（而不是 Map）
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
        log.info("获取股票 {} 的历史价格数据, 共 {} 天", stockCode, count);
        
        HashMap<String, Object> paramMap = new HashMap<>(10);
        paramMap.put("c.funcno", 20009);
        paramMap.put("c.version", 1);
        paramMap.put("c.stock_code", stockCode);
        paramMap.put("c.type", "day");
        paramMap.put("c.count", String.valueOf(count));
        
        return requestDataList(buildParams(paramMap));
    }

    /**
     * 获取所有股票列表
     * 
     * @return 股票列表数据
     */
    public JSONArray getStockList() {
        log.info("获取股票列表数据");
        
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("c.funcno", 21000);
        paramMap.put("c.version", 1);
        paramMap.put("c.sort", 1);
        paramMap.put("c.order", 1);
        paramMap.put("c.type", "0:2:9:18");
        paramMap.put("c.curPage", 1);
        paramMap.put("c.rowOfPage", 5000);
        paramMap.put("c.field", "1:2:22:23:24:3:8:16:21:31");
        
        return requestDataList(buildParams(paramMap));
    }

    /**
     * 获取股票实时价格
     * 
     * @param stockCode 股票代码
     * @return 实时价格
     */
    public JSONObject getRealtimePrice(String stockCode) {
        log.debug("获取股票 {} 的实时价格", stockCode);
        
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("stockcode", stockCode);
        paramMap.put("action", 33);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("Level", 1);
        paramMap.put("UseBPrice", 1);
        
        try {
            HashMap<String, Object> params = buildParams(paramMap);
            String url = API_BASE_URL;
            Map<String, Object> response = restTemplate.postForObject(url, params, Map.class);
            
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
