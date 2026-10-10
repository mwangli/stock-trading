package com.stock.dataCollector.support;

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
 * 证券平台 API 客户端，对接证券平台获取股票数据
 *
 * @author mwangli
 * @since 2026-03-14
 */
@Slf4j
@Component
public class SecuritiesClient {

    private static final String API_BASE_URL = "https://weixin.citicsinfo.com/reqxml";
    private static final String NEWS_API_URL = API_BASE_URL + "?action=1234";
    private final RestTemplate restTemplate;
    private String h5Token = "";

    public SecuritiesClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void setH5Token(String token) {
        this.h5Token = token;
        log.info("H5Token已更新");
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(
            MediaType.parseMediaType("application/json"),
            MediaType.parseMediaType("text/javascript"),
            MediaType.ALL
        ));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1");
        headers.set("Referer", "https://weixin.citicsinfo.com/tztweb/hq/index.html");
        headers.set("sec-ch-ua", "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"145\", \"Chromium\";v=\"145\"");
        headers.set("sec-ch-ua-mobile", "?1");
        headers.set("sec-ch-ua-platform", "\"iOS\"");
        headers.set("sec-fetch-dest", "empty");
        headers.set("sec-fetch-mode", "cors");
        headers.set("sec-fetch-site", "same-origin");
        headers.set("Accept-Language", "zh-CN,zh;q=0.9");
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.set("Cache-Control", "no-cache");
        String cookie = buildCookie();
        if (!cookie.isEmpty()) {
            headers.set("Cookie", cookie);
        }
        return headers;
    }

    private String buildCookie() {
        StringBuilder cookie = new StringBuilder();
        cookie.append("H5Error=0; ");
        cookie.append("has_user_token_cookie=0; ");
        if (h5Token != null && !h5Token.isEmpty()) {
            cookie.append("H5Token=").append(h5Token).append("; ");
        }
        return cookie.toString();
    }

    private MultiValueMap<String, String> buildParams(Map<String, Object> paramMap) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("cfrom", "H5");
        params.add("tfrom", "PC");
        params.add("newindex", "1");
        params.add("reqno", String.valueOf(System.currentTimeMillis()));
        if (paramMap != null) {
            for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                params.add(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private JSONArray requestDataList(Map<String, Object> paramMap) {
        try {
            String url = API_BASE_URL + "?action=1230";
            HttpHeaders headers = buildHeaders();
            MultiValueMap<String, String> params = buildParams(paramMap);
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);
            log.debug("请求URL: {}", url);
            Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);
            if (response == null) {
                log.warn("证券平台返回为空");
                return new JSONArray();
            }
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

    public JSONArray getHistoryPrices(String stockCode, int count) {
        log.info("获取股票 {} 的历史价格数据(平台默认区间)", stockCode);
        Map<String, Object> paramMap = new java.util.HashMap<>(10);
        paramMap.put("c.funcno", 20009);
        paramMap.put("c.version", 1);
        paramMap.put("c.stock_code", stockCode);
        paramMap.put("c.type", "day");
        return requestDataList(paramMap);
    }

    public JSONArray getHistoryPrices(String stockCode, LocalDate startDate, LocalDate endDate) {
        log.info("获取股票 {} 的历史价格数据({} ~ {})", stockCode, startDate, endDate);
        Map<String, Object> paramMap = new java.util.HashMap<>(10);
        paramMap.put("c.funcno", 20009);
        paramMap.put("c.version", 1);
        paramMap.put("c.stock_code", stockCode);
        paramMap.put("c.type", "day");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        if (startDate != null) {
            paramMap.put("c.begin_date", startDate.format(formatter));
        }
        if (endDate != null) {
            paramMap.put("c.end_date", endDate.format(formatter));
        }
        return requestDataList(paramMap);
    }

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

    @SuppressWarnings("unchecked")
    public JSONObject getRealtimePrice(String stockCode) {
        log.debug("获取股票 {} 的实时价格", stockCode);
        try {
            String url = API_BASE_URL;
            HttpHeaders headers = buildHeaders();
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("cfrom", "H5");
            params.add("tfrom", "PC");
            params.add("newindex", "1");
            params.add("reqno", String.valueOf(System.currentTimeMillis()));
            params.add("stockcode", stockCode);
            params.add("action", "33");
            params.add("ReqlinkType", "1");
            params.add("Level", "1");
            params.add("UseBPrice", "1");
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);
            Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);
            if (response == null) return null;
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

    // ==================== 新闻 API ====================

    /** 新闻 menu_id */
    public static final String MENU_ID_NEWS = "20002";
    /** 公告 menu_id */
    public static final String MENU_ID_ANNOUNCEMENT = "20001";

    /**
     * 获取指定股票的新闻/公告列表
     *
     * @param stockCode 股票代码，如 600234
     * @param page      页码，从 1 开始
     * @param pageSize  每页条数，15~100
     * @param menuId    菜单 ID，20002=新闻，20001=公告
     * @return 新闻列表响应，GRID0 为 "externalId|title|publishTime||" 格式的字符串数组
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestNewsList(String stockCode, int page, int pageSize, String menuId) {
        log.debug("获取股票 {} 的列表(menu_id={})，第 {} 页，每页 {} 条", stockCode, menuId, page, pageSize);
        try {
            HttpHeaders headers = buildHeaders();
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("action", "46101");
            params.add("stockCode", stockCode);
            params.add("menu_id", menuId);
            params.add("ReqLinkType", "2");
            params.add("nPage", String.valueOf(page));
            params.add("maxcount", String.valueOf(pageSize));
            params.add("cfrom", "H5");
            params.add("tfrom", "PC");
            params.add("CHANNEL", "");
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);
            Map<String, Object> response = restTemplate.postForObject(NEWS_API_URL, requestEntity, Map.class);
            if (response == null) {
                log.warn("证券平台新闻列表返回为空");
                return java.util.Collections.emptyMap();
            }
            return response;
        } catch (Exception e) {
            log.error("获取股票 {} 新闻列表失败", stockCode, e);
            return java.util.Collections.emptyMap();
        }
    }

    /**
     * 获取新闻/公告详情
     *
     * @param externalId 证券平台新闻 ID
     * @param menuId     菜单 ID，需与列表请求时一致，20002=新闻，20001=公告
     * @return 新闻详情响应，包含 GRID0(正文)、TITLE、TIME、DATES、MEDIA
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestNewsDetail(String externalId, String menuId) {
        log.debug("获取详情，ID: {}, menu_id: {}", externalId, menuId);
        try {
            HttpHeaders headers = buildHeaders();
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("action", "46102");
            params.add("id", externalId);
            params.add("menu_id", menuId);
            params.add("ReqLinkType", "2");
            params.add("cfrom", "H5");
            params.add("tfrom", "PC");
            params.add("CHANNEL", "");
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);
            Map<String, Object> response = restTemplate.postForObject(NEWS_API_URL, requestEntity, Map.class);
            if (response == null) {
                log.warn("证券平台新闻详情返回为空");
                return java.util.Collections.emptyMap();
            }
            return response;
        } catch (Exception e) {
            log.error("获取新闻详情失败，ID: {}", externalId, e);
            return java.util.Collections.emptyMap();
        }
    }
}
