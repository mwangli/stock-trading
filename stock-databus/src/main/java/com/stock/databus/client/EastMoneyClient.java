package com.stock.databus.client;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stock.common.enums.MarketType;
import com.stock.databus.entity.StockInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyClient {

    private final OkHttpClient httpClient;

    private static final String STOCK_LIST_URL = "https://push2.eastmoney.com/api/qt/clist/get";

    public List<StockInfo> fetchStockList() {
        List<StockInfo> stocks = new ArrayList<>();
        try {
            String url = STOCK_LIST_URL + "?pn=1&pz=5000&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fields=f1,f2,f3,f4,f5,f6,f12,f13,f14,f100,f104,f105,f107";
            
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("请求失败: {}", response);
                    return stocks;
                }

                String body = response.body().string();
                JSONObject json = JSONUtil.parseObj(body);
                JSONObject data = json.getJSONObject("data");
                if (data == null) {
                    return stocks;
                }

                JSONArray diff = data.getJSONArray("diff");
                for (int i = 0; i < diff.size(); i++) {
                    try {
                        JSONObject item = diff.getJSONObject(i);
                        String code = item.getStr("f12");
                        String name = item.getStr("f14");
                        
                        if (code == null || name == null || name.contains("ST") || name.contains("*ST")) {
                            continue;
                        }

                        StockInfo stock = new StockInfo();
                        stock.setCode(code);
                        stock.setName(name);
                        stock.setMarket(code.startsWith("6") ? MarketType.SH.getCode() : MarketType.SZ.getCode());
                        stock.setIsSt(name.contains("ST") ? 1 : 0);
                        stock.setIsTradable(1);
                        
                        BigDecimal price = item.getBigDecimal("f2");
                        BigDecimal increase = item.getBigDecimal("f4");
                        stock.setPrice(price);
                        stock.setIncrease(increase);
                        stock.setDeleted("0");
                        
                        stocks.add(stock);
                    } catch (Exception e) {
                        log.warn("解析股票数据异常: {}", e.getMessage());
                    }
                }
            }
            
            log.info("成功获取 {} 只股票", stocks.size());
            
        } catch (Exception e) {
            log.error("获取股票列表失败", e);
        }
        
        return stocks;
    }
}
