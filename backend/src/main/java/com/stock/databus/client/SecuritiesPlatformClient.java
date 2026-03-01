package com.stock.databus.client;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 证券平台数据客户端
 * 
 * 接口分类：
 * 1. 不需要 Token 的接口（公开数据）：
 *    - 股票列表查询 (getDataList)
 *    - 历史 K 线数据 (getHistoryPrices)
 *    - 实时行情数据 (getRealTimeQuotes)
 * 
 * 2. 需要 Token 的接口（交易相关）：
 *    - 账户信息
 *    - 持仓信息
 *    - 委托下单
 *    - 订单查询
 * 
 * 当前实现仅包含不需要 Token 的数据采集接口
 */
@Slf4j
@Component
public class SecuritiesPlatformClient {

    private static final String REQUEST_URL = "https://weixin.citicsinfo.com/reqxml";

    /**
     * 获取股票列表数据（不需要 Token）
     */
    public List<StockInfo> getDataList() {
        log.info("开始获取股票列表数据");
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

        try {
            JSONArray results = request3(paramMap);
            for (int j = 0; j < results.size(); j++) {
                String s = results.getString(j);
                String[] split = s.split(",");
                
                String increase = split[0].replaceAll("\[", "");
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
            log.info("成功获取{}只股票数据", stockInfos.size());
        } catch (Exception e) {
            log.error("获取股票列表失败", e);
        }
        return stockInfos;
    }

    /**
     * 获取历史价格曲线数据（不需要 Token）
     */
    public List<StockPrices> getHistoryPrices(String code) {
        log.info("开始获取股票{}的历史价格数据", code);
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("c.funcno", 20009);
        paramMap.put("c.version", 1);
        paramMap.put("c.stock_code", code);
        paramMap.put("c.type", "day");
        paramMap.put("c.count", "100");
        paramMap.put("c.cfrom", "H5");
        paramMap.put("c.tfrom", "PC");

        try {
            JSONArray results = request3(paramMap);
            List<StockPrices> prices = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                StockPrices stockPrices = new StockPrices();
                String s = results.getString(i);
                s = s.replaceAll("\[", "").replaceAll("]", "");
                String[] split = s.split(",");
                
                stockPrices.setDate(split[0]);
                stockPrices.setCode(code);
                
                if (split.length >= 2) {
                    stockPrices.setPrice1(Double.parseDouble(split[1]) / 100);
                    stockPrices.setPrice2(Double.parseDouble(split[2]) / 100);
                }
                if (split.length >= 5) {
                    stockPrices.setPrice3(Double.parseDouble(split[3]) / 100);
                    stockPrices.setPrice4(Double.parseDouble(split[4]) / 100);
                }
                prices.add(stockPrices);
            }
            log.info("成功获取{}条历史价格数据", prices.size());
            return prices;
        } catch (Exception e) {
            log.error("获取历史价格失败：{}", code, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取实时价格（不需要 Token）
     */
    public Double getNowPrice(String code) {
        log.debug("开始获取股票{}的实时价格", code);
        long timeMillis = System.currentTimeMillis();
        HashMap<String, Object> paramMap = new HashMap<>();
        paramMap.put("stockcode", code);
        paramMap.put("Reqno", timeMillis);
        paramMap.put("action", 33);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("Level", 1);
        paramMap.put("UseBPrice", 1);
        
        try {
            JSONObject res = request(paramMap);
            Double price = res.getDouble("PRICE");
            log.debug("股票{}实时价格：{}", code, price);
            return price;
        } catch (Exception e) {
            log.error("获取实时价格失败：{}", code, e);
            return null;
        }
    }

    private JSONObject request(HashMap<String, Object> formParam) {
        return request(REQUEST_URL, formParam);
    }

    private JSONObject request(String url, HashMap<String, Object> formParam) {
        try {
            HashMap<String, Object> params = buildParams(formParam);
            String response = HttpUtil.createPost(url).form(params).timeout(10000).execute().body();
            
            if (log.isDebugEnabled() && response != null) {
                log.debug(response.length() > 500 ? response.substring(0, 500) : response);
            }
            
            if (response == null || response.isEmpty()) {
                log.warn("请求返回为空");
                return new JSONObject();
            }
            
            JSONObject res = JSONObject.parseObject(response);
            return res != null ? res : new JSONObject();
        } catch (Exception e) {
            log.error("请求数据异常：{}", url, e);
            return new JSONObject();
        }
    }

    private JSONArray request2(HashMap<String, Object> formParam) {
        JSONObject res = request(formParam);
        return res != null ? res.getJSONArray("GRID0") : new JSONArray();
    }

    private JSONArray request3(HashMap<String, Object> formParam) {
        JSONObject res = request(REQUEST_URL.concat("?action=1230"), formParam);
        if (res != null) {
            JSONObject data = res.getJSONObject("BINDATA");
            if (data != null && data.getJSONArray("results") != null) {
                return data.getJSONArray("results");
            }
        }
        return new JSONArray();
    }

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
