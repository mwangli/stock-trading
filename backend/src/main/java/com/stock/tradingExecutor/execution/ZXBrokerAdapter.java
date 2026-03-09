package com.stock.tradingExecutor.execution;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.tradingExecutor.domain.entity.OrderStatus;
import com.stock.tradingExecutor.domain.entity.Position;
import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.OrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 中信证券适配器
 * 对接中信证券交易平台API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZXBrokerAdapter implements BrokerAdapter {

    private final ZXRequestUtils requestUtils;
    private final ZXBrokerConfig config;

    // API Action常量
    private static final int ACTION_GET_ACCOUNT = 116;
    private static final int ACTION_GET_PRICE = 33;
    private static final int ACTION_SUBMIT_ORDER = 110;
    private static final int ACTION_QUERY_TODAY_ORDERS = 113;
    private static final int ACTION_QUERY_FILLED_ORDERS = 114;
    private static final int ACTION_QUERY_HISTORY_ORDERS = 115;
    private static final int ACTION_CANCEL_ORDER = 111;

    @Override
    public String getName() {
        return "ZXBroker(中信证券)";
    }

    @Override
    public AccountStatus getAccountInfo() {
        log.info("[ZXBroker] 获取账户资金信息");

        String token = requestUtils.getToken();
        if (token == null) {
            log.error("[ZXBroker] Token无效，请先设置Token");
            return null;
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", ACTION_GET_ACCOUNT);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("token", token);
        paramMap.put("reqno", System.currentTimeMillis());

        JSONArray jsonArray = requestUtils.requestArray(requestUtils.buildParams(paramMap));
        if (jsonArray == null || jsonArray.size() < 2) {
            log.error("[ZXBroker] 获取账户资金失败");
            return null;
        }

        // 解析账户信息
        String data = jsonArray.getString(1);
        String[] split = data.split("\\|");

        if (split.length < 6) {
            log.error("[ZXBroker] 账户数据格式错误: {}", data);
            return null;
        }

        AccountStatus status = new AccountStatus();
        try {
            status.setAvailableCash(new BigDecimal(split[3]));
            status.setTotalAssets(new BigDecimal(split[4]));
            status.setFrozenAmount(new BigDecimal(split[7]));
            status.setTotalPosition(new BigDecimal(split[5]));
        } catch (NumberFormatException e) {
            log.error("[ZXBroker] 解析账户数据失败: {}", e.getMessage());
            return null;
        }

        log.info("[ZXBroker] 账户信息: 总资产={}, 可用={}", status.getTotalAssets(), status.getAvailableCash());
        return status;
    }

    @Override
    public BigDecimal getRealtimePrice(String stockCode) {
        log.info("[ZXBroker] 获取股票实时价格: {}", stockCode);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("stockcode", stockCode);
        paramMap.put("Reqno", System.currentTimeMillis());
        paramMap.put("action", ACTION_GET_PRICE);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("Level", 1);
        paramMap.put("UseBPrice", 1);

        JSONObject res = requestUtils.request(requestUtils.buildParams(paramMap));
        Double price = res.getDouble("PRICE");

        if (price == null) {
            log.error("[ZXBroker] 获取股票价格失败: {}", stockCode);
            return null;
        }

        log.info("[ZXBroker] 股票 {} 当前价格: {}", stockCode, price);
        return BigDecimal.valueOf(price);
    }

    @Override
    public OrderResult submitOrder(String direction, String stockCode, BigDecimal price, Integer quantity) {
        log.info("[ZXBroker] 提交委托: {} {} {} {}", direction, stockCode, price, quantity);

        String token = requestUtils.getToken();
        if (token == null) {
            log.error("[ZXBroker] Token无效，请先设置Token");
            return OrderResult.fail("Token无效，请先设置Token");
        }

        String directionCode = "BUY".equals(direction) ? "B" : "S";

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", ACTION_SUBMIT_ORDER);
        paramMap.put("PriceType", 0);
        paramMap.put("Direction", directionCode);
        paramMap.put("StockCode", stockCode);
        paramMap.put("Price", price.doubleValue());
        paramMap.put("Volume", quantity);
        paramMap.put("token", token);
        paramMap.put("reqno", System.currentTimeMillis());

        JSONObject res = requestUtils.request(requestUtils.buildParams(paramMap));
        String answerNo = res.getString("ANSWERNO");

        if (answerNo == null) {
            String errorMsg = res.getString("ERRORMSG");
            log.error("[ZXBroker] 委托提交失败: {}", errorMsg);
            return OrderResult.fail("委托提交失败: " + errorMsg);
        }

        log.info("[ZXBroker] 委托提交成功, 订单号: {}", answerNo);
        return OrderResult.builder()
                .success(true)
                .orderId(answerNo)
                .stockCode(stockCode)
                .direction(direction)
                .price(price)
                .quantity(quantity)
                .status(OrderStatus.SUBMITTED)
                .message("委托已提交")
                .submitTime(LocalDateTime.now())
                .build();
    }

    @Override
    public OrderStatus queryOrderStatus(String orderId) {
        log.info("[ZXBroker] 查询委托状态: {}", orderId);

        List<OrderInfo> orders = listTodayAllOrder();
        Optional<OrderInfo> orderOpt = orders.stream()
                .filter(o -> orderId.equals(o.getAnswerNo()))
                .findFirst();

        if (orderOpt.isEmpty()) {
            log.warn("[ZXBroker] 未找到订单: {}", orderId);
            return OrderStatus.PENDING;
        }

        String statusStr = orderOpt.get().getStatus();
        return convertOrderStatus(statusStr);
    }

    @Override
    public Boolean cancelOrder(String orderId) {
        log.info("[ZXBroker] 撤销委托: {}", orderId);

        String token = requestUtils.getToken();
        if (token == null) {
            log.error("[ZXBroker] Token无效，请先设置Token");
            return false;
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", ACTION_CANCEL_ORDER);
        paramMap.put("ContactID", orderId);
        paramMap.put("token", token);
        paramMap.put("reqno", System.currentTimeMillis());

        JSONObject res = requestUtils.request(requestUtils.buildParams(paramMap));
        String errorNo = res.getString("ERRORNO");

        if ("0".equals(errorNo)) {
            log.info("[ZXBroker] 撤单成功: {}", orderId);
            return true;
        }

        log.error("[ZXBroker] 撤单失败: {}", res.getString("ERRORMSG"));
        return false;
    }

    @Override
    public List<OrderResult> getTodayOrders() {
        log.info("[ZXBroker] 获取当日成交订单");

        String token = requestUtils.getToken();
        if (token == null) {
            log.error("[ZXBroker] Token无效，请先设置Token");
            return new ArrayList<>();
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", ACTION_QUERY_FILLED_ORDERS);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", config.getMaxOrderCount());
        paramMap.put("token", token);
        paramMap.put("reqno", System.currentTimeMillis());

        JSONArray results = requestUtils.requestArray(requestUtils.buildParams(paramMap));
        return parseOrderList(results, true);
    }

    @Override
    public List<Position> getPositions() {
        log.info("[ZXBroker] 获取持仓列表");
        return new ArrayList<>();
    }

    /**
     * 等待订单成交
     */
    public boolean waitSuccess(String orderId) {
        log.info("[ZXBroker] 等待订单成交: {}", orderId);

        int times = 0;
        int maxTimes = config.getOrderWaitTimes();
        int interval = config.getOrderWaitInterval();
        int cancelTimes = config.getCancelWaitTimes();

        while (times++ < maxTimes) {
            try {
                Thread.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[ZXBroker] 等待被中断");
                return false;
            }

            List<OrderInfo> orders = listTodayAllOrder();
            Optional<OrderInfo> orderOpt = orders.stream()
                    .filter(o -> orderId.equals(o.getAnswerNo()))
                    .findFirst();

            if (orderOpt.isEmpty()) {
                log.warn("[ZXBroker] 订单状态查询失败: {}", orderId);
                continue;
            }

            String status = orderOpt.get().getStatus();
            log.info("[ZXBroker] 订单 {} 状态: {}", orderId, status);

            if ("已成".equals(status)) {
                log.info("[ZXBroker] 订单成交: {}", orderId);
                return true;
            }

            if ("已报".equals(status)) {
                if (times >= cancelTimes) {
                    log.info("[ZXBroker] 超时撤单: {}", orderId);
                    cancelOrder(orderId);
                }
            }

            if ("已撤".equals(status) || "废单".equals(status)) {
                return false;
            }
        }

        log.error("[ZXBroker] 订单等待超时: {}", orderId);
        return false;
    }

    private List<OrderInfo> listTodayAllOrder() {
        String token = requestUtils.getToken();
        if (token == null) {
            return new ArrayList<>();
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("action", ACTION_QUERY_TODAY_ORDERS);
        paramMap.put("StartPos", 0);
        paramMap.put("MaxCount", config.getMaxOrderCount());
        paramMap.put("token", token);
        paramMap.put("ReqlinkType", 1);
        paramMap.put("reqno", System.currentTimeMillis());

        JSONArray result = requestUtils.requestArray(requestUtils.buildParams(paramMap));
        return parseOrderStatusList(result);
    }

    private List<OrderInfo> parseOrderStatusList(JSONArray result) {
        List<OrderInfo> orderList = new ArrayList<>();
        if (result == null || result.size() <= 1) {
            return orderList;
        }

        for (int i = 1; i < result.size(); i++) {
            String str = result.getString(i);
            String[] split = str.split("\\|");
            if (split.length < 9) {
                continue;
            }

            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setDate(split[0]);
            orderInfo.setTime(split[1]);
            orderInfo.setCode(split[2]);
            orderInfo.setName(split[3]);
            orderInfo.setType(split[4]);
            orderInfo.setDirection(split[5]);
            orderInfo.setStatus(split[6]);
            orderInfo.setAnswerNo(split[9]);

            orderList.add(orderInfo);
        }

        return orderList;
    }

    private List<OrderResult> parseOrderList(JSONArray results, boolean isToday) {
        List<OrderResult> orderList = new ArrayList<>();
        if (results == null || results.size() <= 1) {
            return orderList;
        }

        for (int i = 1; i < results.size(); i++) {
            String result = results.getString(i);
            String[] split = result.split("\\|");

            String name = isToday ? split[0] : split[5];
            String type = isToday ? split[1] : split[6];
            String number = isToday ? split[2] : split[8];
            String price = isToday ? split[3] : split[7];
            String code = isToday ? split[5] : split[4];
            String answerNo = isToday ? split[6] : split[1];

            if ("0".equals(answerNo) || "799999".equals(code)) {
                continue;
            }

            OrderResult orderInfo = OrderResult.builder()
                    .orderId(answerNo)
                    .stockCode(code)
                    .stockName(name)
                    .direction("买入".equals(type) ? "BUY" : "SELL")
                    .price(new BigDecimal(price))
                    .quantity((int) Math.abs(Double.parseDouble(number)))
                    .status(OrderStatus.FILLED)
                    .message("已成交")
                    .build();

            orderList.add(orderInfo);
        }

        return orderList;
    }

    private OrderStatus convertOrderStatus(String statusStr) {
        if ("已成".equals(statusStr)) {
            return OrderStatus.FILLED;
        } else if ("已报".equals(statusStr)) {
            return OrderStatus.SUBMITTED;
        } else if ("部成".equals(statusStr)) {
            return OrderStatus.PARTIAL;
        } else if ("已撤".equals(statusStr) || "已报待撤".equals(statusStr)) {
            return OrderStatus.CANCELLED;
        } else if ("废单".equals(statusStr)) {
            return OrderStatus.REJECTED;
        }
        return OrderStatus.PENDING;
    }

    /**
     * 计算手续费
     */
    public BigDecimal calculateFee(BigDecimal amount) {
        BigDecimal fee = amount.multiply(new BigDecimal("0.0005"));
        BigDecimal minFee = new BigDecimal("5");
        return fee.max(minFee).setScale(2, RoundingMode.HALF_UP);
    }

    @lombok.Data
    private static class OrderInfo {
        private String date;
        private String time;
        private String code;
        private String name;
        private String type;
        private String direction;
        private String status;
        private String answerNo;
    }
}
