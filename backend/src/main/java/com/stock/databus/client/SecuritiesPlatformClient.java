package com.stock.databus.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.hutool.http.HttpUtil;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * 证券平台数据客户端（替换 TuShare）
 * 基于已有证券平台接口实现
 */
@Slf4j
@Component
public class SecuritiesPlatformClient {

    @Resource
    private StringRedisTemplate redisTemplate;

    private static final String REQUEST_URL = "https://weixin.citicsinfo.com/reqxml";
    private static final String TOKEN_KEY = "requestToken";
    private static final int LOGIN_RETRY_TIMES = 10;

    /**
     * 获取股票列表数据
     */
    public List<StockInfo> getDataList() {
        List<StockInfo> stockInfos = new ArrayList<>();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("c.funcno", 21000);
        paramMap.put("c.version", 1);
        paramMap.put("c.sort", 1);
        paramMap.put("c.order", 1);
        paramMap.put("c.type", "0:2:9:18");
        paramMap.put("c.curPage", 1);
        paramMap.put("c.rowOfPage", 5000);
        paramMap.put("c.field", "1:2:22:23:24:3:8:16:21:31");
        paramMap.put("c.cfrom", "H5");
        paramMap.put("c.tfrom", "PC");

        JSONArray results = request3(paramMap);
        for (int j = 0; j < results.size(); j++) {
            String s = results.getString(j);
            String[] split = s.split(",");
            String increase = split[0].replaceAll("\\[", "");
            double increasePercent = Double.parseDouble(increase);
            Double price = Double.parseDouble(split[1]);
            String name = split[2].replaceAll("\"", "");
            String market = split[3].replaceAll("\"", "");
            String code = split[4].replaceAll("\"", "");

            StockInfo stockInfo = new StockInfo();
            stockInfo.setName(name);
            stockInfo.setCode(code);
            stockInfo.setMarket(market);
            stockInfo.setIncrease(increasePercent);
            stockInfo.setPrice(price);
            stockInfos.add(stockInfo);
        }
        log.info("共获取到{}条新数据", stockInfos.size());
        return stockInfos;
    }

    /**
     * 获取历史价格曲线数据
     */
    public List<StockPrices> getHistoryPrices(String code) {
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("c.funcno", 20009);
        paramMap.put("c.version", 1);
        paramMap.put("c.stock_code", code);
        paramMap.put("c.type", "day");
        paramMap.put("c.count", "100");
        paramMap.put("c.cfrom", "H5");
        paramMap.put("c.tfrom", "PC");

        JSONArray results = request3(paramMap);
        List<StockPrices> prices = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            StockPrices stockPrices = new StockPrices();
            String s = results.getString(i);
            s = s.replaceAll("\\[", "").replaceAll("]", "");
            String[] split = s.split(",");
            String price1 = split[1];
            String price2 = split[2];
            stockPrices.setDate(split[0]);
            stockPrices.setPrice1(Double.parseDouble(price1) / 100);
            stockPrices.setPrice2(Double.parseDouble(price2) / 100);
            if (split.length > 3) {
                String price3 = split[3];
                String price4 = split[4];
                stockPrices.setPrice3(Double.parseDouble(price3) / 100);
                stockPrices.setPrice4(Double.parseDouble(price4) / 100);
            }
            stockPrices.setCode(code);
            prices.add(stockPrices);
        }
        return prices;
    }

    /**
     * 获取实时价格
     */
    public Double getNowPrice(String code) {
        long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("stockcode", code);
        paramMap.put("Reqno", timeMillis);
        paramMap.put("action", 33);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("Level", 1);
        paramMap.put("UseBPrice", 1);
        JSONObject res = request(paramMap);
        return res.getDouble("PRICE");
    }

    /**
     * 获取 Token
     */
    public String getToken() {
        return redisTemplate.opsForValue().get(TOKEN_KEY);
    }

    /**
     * 设置 Token
     */
    public void setToken(String token) {
        if (token == null) return;
        redisTemplate.opsForValue().set(TOKEN_KEY, token);
    }

    /**
     * 通用请求方法
     */
    private JSONObject request(HashMap<String, Object> formParam) {
        try {
            HashMap<String, Object> params = buildParams(formParam);
            String response = HttpUtil.createPost(REQUEST_URL).form(params).execute().body();
            log.info(response.length() > 1000 ? response.substring(0, 1000) : response);
            JSONObject res = JSONObject.parseObject(response);
            String newToken = res.getString("TOKEN");
            if (newToken != null) {
                setToken(newToken);
            }
            return res;
        } catch (Exception e) {
            log.error("请求数据异常", e);
            return new JSONObject();
        }
    }

    /**
     * 返回 JSONArray 的请求方法
     */
    private JSONArray request2(HashMap<String, Object> formParam) {
        JSONObject res = request(formParam);
        return res.getJSONArray("GRID0");
    }

    /**
     * 返回数据结果的请求方法
     */
    private JSONArray request3(HashMap<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL.concat("?action=1230"), formParam, 0);
        final JSONObject data = res.getJSONObject("BINDATA");
        if (data != null && data.getJSONArray("results") != null) {
            return data.getJSONArray("results");
        }
        return new JSONArray();
    }

    /**
     * 构建请求参数
     */
    private HashMap<String, Object> buildParams(HashMap<String, Object> paramMap) {
        if (paramMap == null) return new HashMap<>();
        paramMap.put("cfrom", "H5");
        paramMap.put("tfrom", "PC");
        paramMap.put("newindex", "1");
        paramMap.put("MobileCode", "13278828091");
        paramMap.put("intacttoserver", "@ClZvbHVtZUluZm8JAAAANTI1QS00Qjc4");
        paramMap.put("reqno", System.currentTimeMillis());
        return paramMap;
    }
}
