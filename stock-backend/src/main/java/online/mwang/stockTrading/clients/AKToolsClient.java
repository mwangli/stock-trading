package online.mwang.stockTrading.clients;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.dto.KLineDTO;
import online.mwang.stockTrading.dto.QuoteDTO;
import online.mwang.stockTrading.dto.StockInfoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AKTools API客户端
 * 【已弃用】数据采集已迁移到Python层直接执行
 * 
 * 原功能：Java通过HTTP调用AKTools API获取数据
 * 新架构：Python服务直接调用AKShare并写入数据库
 * 
 * 保留此类仅用于兼容，新代码请直接从数据库查询
 * 
 * @deprecated 使用Python DataCollectionService替代，Java层直接从MySQL/MongoDB查询
 * @see online.mwang.stockTrading.services.StockDataQueryService
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated(since = "2.0", forRemoval = true)
public class AKToolsClient {

    private final RestTemplate restTemplate;

    @Value("${stock.data.aktools.base-url:https://api.aktools.com}")
    private String baseUrl;

    @Value("${stock.data.aktools.timeout:30000}")
    private int timeout;

    @Value("${stock.data.aktools.retry.max-attempts:3}")
    private int maxRetryAttempts;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 获取A股全市场股票列表
     * @return 股票信息列表
     */
    public List<StockInfoDTO> getStockList() {
        String url = baseUrl + "/api/public/stock_info_a_code_name";
        log.info("Fetching stock list from: {}", url);

        try {
            String response = executeWithRetry(() -> {
                ResponseEntity<String> entity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    String.class
                );
                return entity.getBody();
            });

            return parseStockList(response);
        } catch (Exception e) {
            log.error("Failed to fetch stock list from AKTools", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取个股历史K线数据
     * @param stockCode 股票代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return K线数据列表
     */
    public List<KLineDTO> getKLine(String stockCode, LocalDate startDate, LocalDate endDate) {
        String url = String.format("%s/api/public/stock_zh_a_hist?symbol=%s&period=daily&start_date=%s&end_date=%s",
                baseUrl, stockCode, startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));
        log.info("Fetching K-line data for {} from {} to {}", stockCode, startDate, endDate);

        try {
            String response = executeWithRetry(() -> {
                ResponseEntity<String> entity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    String.class
                );
                return entity.getBody();
            });

            return parseKLineData(response);
        } catch (Exception e) {
            log.error("Failed to fetch K-line data for stock: {}", stockCode, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取实时行情
     * @param stockCode 股票代码
     * @return 行情数据
     */
    public QuoteDTO getQuote(String stockCode) {
        String url = String.format("%s/api/public/stock_zh_a_spot_em", baseUrl);
        log.debug("Fetching real-time quote for: {}", stockCode);

        try {
            String response = executeWithRetry(() -> {
                ResponseEntity<String> entity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    String.class
                );
                return entity.getBody();
            });

            return parseQuoteData(response, stockCode);
        } catch (Exception e) {
            log.error("Failed to fetch quote for stock: {}", stockCode, e);
            return null;
        }
    }

    /**
     * 批量获取实时行情
     * @param stockCodes 股票代码列表
     * @return 行情数据列表
     */
    public List<QuoteDTO> getQuotes(List<String> stockCodes) {
        // AKTools提供全市场行情接口，我们获取后过滤
        String url = String.format("%s/api/public/stock_zh_a_spot_em", baseUrl);
        log.info("Fetching real-time quotes for {} stocks", stockCodes.size());

        try {
            String response = executeWithRetry(() -> {
                ResponseEntity<String> entity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    String.class
                );
                return entity.getBody();
            });

            return parseQuotesData(response, stockCodes);
        } catch (Exception e) {
            log.error("Failed to fetch quotes", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析股票列表数据
     */
    private List<StockInfoDTO> parseStockList(String response) {
        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        List<StockInfoDTO> stockList = new ArrayList<>();
        try {
            JSONArray array = JSON.parseArray(response);
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                StockInfoDTO dto = new StockInfoDTO();
                dto.setStockCode(obj.getString("code"));
                dto.setStockName(obj.getString("name"));
                
                // 根据代码前缀判断市场
                String code = dto.getStockCode();
                if (code != null && code.startsWith("6")) {
                    dto.setMarket("SH");
                } else {
                    dto.setMarket("SZ");
                }
                
                // 判断是否为ST股票
                String name = dto.getStockName();
                dto.setIsSt(name != null && (name.contains("ST") || name.contains("*ST")));
                
                // 默认可交易（需要结合其他接口确认停牌状态）
                dto.setIsTradable(!dto.getIsSt());
                
                stockList.add(dto);
            }
        } catch (Exception e) {
            log.error("Failed to parse stock list", e);
        }

        log.info("Successfully parsed {} stocks", stockList.size());
        return stockList;
    }

    /**
     * 解析K线数据
     */
    private List<KLineDTO> parseKLineData(String response) {
        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        List<KLineDTO> klineList = new ArrayList<>();
        try {
            JSONArray array = JSON.parseArray(response);
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                KLineDTO dto = new KLineDTO();
                dto.setTradeDate(LocalDate.parse(obj.getString("日期")));
                dto.setOpen(obj.getBigDecimal("开盘"));
                dto.setHigh(obj.getBigDecimal("最高"));
                dto.setLow(obj.getBigDecimal("最低"));
                dto.setClose(obj.getBigDecimal("收盘"));
                dto.setVolume(obj.getLong("成交量"));
                dto.setAmount(obj.getBigDecimal("成交额"));
                dto.setChangePct(obj.getBigDecimal("涨跌幅"));
                dto.setTurnoverRate(obj.getBigDecimal("换手率"));
                klineList.add(dto);
            }
        } catch (Exception e) {
            log.error("Failed to parse K-line data", e);
        }

        return klineList;
    }

    /**
     * 解析单只股票行情
     */
    private QuoteDTO parseQuoteData(String response, String stockCode) {
        List<QuoteDTO> quotes = parseQuotesData(response, Collections.singletonList(stockCode));
        return quotes.isEmpty() ? null : quotes.get(0);
    }

    /**
     * 解析批量行情数据
     */
    private List<QuoteDTO> parseQuotesData(String response, List<String> stockCodes) {
        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        List<QuoteDTO> quoteList = new ArrayList<>();
        try {
            JSONArray array = JSON.parseArray(response);
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String code = obj.getString("代码");
                
                // 只处理请求的股票代码
                if (stockCodes != null && !stockCodes.contains(code)) {
                    continue;
                }

                QuoteDTO dto = new QuoteDTO();
                dto.setStockCode(code);
                dto.setStockName(obj.getString("名称"));
                dto.setCurrentPrice(obj.getBigDecimal("最新价"));
                dto.setChange(obj.getBigDecimal("涨跌额"));
                dto.setChangePercent(obj.getBigDecimal("涨跌幅"));
                dto.setOpen(obj.getBigDecimal("今开"));
                dto.setHigh(obj.getBigDecimal("最高"));
                dto.setLow(obj.getBigDecimal("最低"));
                dto.setPreviousClose(obj.getBigDecimal("昨收"));
                dto.setVolume(obj.getLong("成交量"));
                dto.setAmount(obj.getBigDecimal("成交额"));
                dto.setIsRealTime(true);
                quoteList.add(dto);
            }
        } catch (Exception e) {
            log.error("Failed to parse quote data", e);
        }

        return quoteList;
    }

    /**
     * 带重试机制的执行
     */
    private String executeWithRetry(RetryableOperation operation) throws Exception {
        Exception lastException = null;
        for (int i = 0; i < maxRetryAttempts; i++) {
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                log.warn("API call failed (attempt {}/{}), retrying...", i + 1, maxRetryAttempts);
                if (i < maxRetryAttempts - 1) {
                    Thread.sleep(1000L * (i + 1)); // 指数退避
                }
            }
        }
        throw lastException;
    }

    @FunctionalInterface
    private interface RetryableOperation {
        String execute() throws Exception;
    }
}
