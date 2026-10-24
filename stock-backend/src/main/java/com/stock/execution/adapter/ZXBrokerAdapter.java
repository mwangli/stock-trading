// AI_GENERATE_START-------------------
package com.stock.execution.adapter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.execution.config.TradingProperties;
import com.stock.execution.config.ZXBrokerConfig;
import com.stock.execution.entity.OrderStatus;
import com.stock.execution.entity.Position;
import com.stock.execution.parser.ZXBrokerResponseParser;
import com.stock.execution.vo.AccountStatus;
import com.stock.execution.vo.BrokerFillSnapshot;
import com.stock.execution.vo.BrokerOrderSnapshot;
import com.stock.execution.vo.OrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * ZX证券适配器。
 * 首期恢复只读账户、持仓、委托和成交能力，真实写操作由独立总门禁保护。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZXBrokerAdapter implements BrokerAdapter {

    private static final int ACTION_GET_PRICE = 33;
    private static final int ACTION_SUBMIT_ORDER = 110;
    private static final int ACTION_CANCEL_ORDER = 111;
    private static final int ACTION_QUERY_TODAY_ORDERS = 113;
    private static final int ACTION_QUERY_TODAY_FILLS = 114;
    private static final int ACTION_QUERY_HISTORY_FILLS = 115;
    private static final int ACTION_GET_ACCOUNT = 116;
    private static final int ACTION_GET_POSITIONS = 117;
    private static final int ACTION_QUERY_HISTORY_ORDERS = 5018;
    private static final DateTimeFormatter BROKER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ZXRequestUtils requestUtils;
    private final ZXBrokerConfig config;
    private final TradingProperties tradingProperties;
    private final ZXBrokerResponseParser responseParser;

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "ZX证券";
    }

    /** {@inheritDoc} */
    @Override
    public AccountStatus getAccountInfo() {
        log.info("[ZXBroker] 获取账户资金信息");
        JSONArray rows = queryArray(ACTION_GET_ACCOUNT, Map.of("ReqlinkType", 1));
        if (rows.size() < 2) {
            log.warn("[ZXBroker] 账户资金响应为空");
            return null;
        }

        String[] fields = responseParser.splitRow(rows.getString(1));
        if (fields.length < 8) {
            log.warn("[ZXBroker] 账户资金字段不足，实际字段数={}", fields.length);
            return null;
        }

        BigDecimal availableCash = responseParser.parseDecimal(fields[3]);
        BigDecimal totalAssets = responseParser.parseDecimal(fields[4]);
        BigDecimal totalPosition = responseParser.parseDecimal(fields[5]);
        BigDecimal frozenAmount = responseParser.parseDecimal(fields[7]);
        if (availableCash == null || totalAssets == null || totalPosition == null || frozenAmount == null) {
            log.warn("[ZXBroker] 账户资金包含无法解析的数值字段");
            return null;
        }

        AccountStatus status = new AccountStatus();
        status.setAvailableCash(availableCash);
        status.setTotalAssets(totalAssets);
        status.setTotalPosition(totalPosition);
        status.setFrozenAmount(frozenAmount);
        return status;
    }

    /** {@inheritDoc} */
    @Override
    public BigDecimal getRealtimePrice(String stockCode) {
        log.info("[ZXBroker] 获取股票实时价格: stockCode={}", stockCode);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("stockcode", stockCode);
        parameters.put("action", ACTION_GET_PRICE);
        parameters.put("ReqlinkType", 1);
        parameters.put("Level", 1);
        parameters.put("UseBPrice", 1);

        JSONObject response = requestUtils.request(requestUtils.buildParams(parameters));
        BigDecimal price = response.getBigDecimal("PRICE");
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[ZXBroker] 未获得有效实时价格: stockCode={}", stockCode);
            return null;
        }
        return price;
    }

    /** {@inheritDoc} */
    @Override
    public OrderResult submitOrder(String direction, String stockCode, BigDecimal price, Integer quantity) {
        log.info("[ZXBroker] 请求提交委托: direction={}, stockCode={}, price={}, quantity={}",
                direction, stockCode, price, quantity);
        if (!tradingProperties.isLiveWriteAllowed()) {
            log.warn("[ZXBroker] 真实委托被交易写入总门禁阻止");
            return OrderResult.fail("真实交易写入门禁未开启");
        }

        String directionCode = normalizeWriteDirection(direction);
        if (directionCode == null) {
            return OrderResult.fail("不支持的买卖方向");
        }
        String token = requireToken();
        if (token == null) {
            return OrderResult.fail("券商会话无效，请先登录");
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("action", ACTION_SUBMIT_ORDER);
        parameters.put("PriceType", 0);
        parameters.put("Direction", directionCode);
        parameters.put("StockCode", stockCode);
        parameters.put("Price", price);
        parameters.put("Volume", quantity);
        parameters.put("token", token);

        JSONObject response = requestUtils.request(requestUtils.buildParams(parameters));
        String orderId = response.getString("ANSWERNO");
        if (orderId == null || orderId.isBlank()) {
            log.warn("[ZXBroker] 委托提交失败，错误码={}", response.getString("ERRORNO"));
            return OrderResult.fail("券商拒绝委托");
        }

        return OrderResult.builder()
                .success(true)
                .orderId(orderId)
                .stockCode(stockCode)
                .direction(direction.toUpperCase(Locale.ROOT))
                .price(price)
                .quantity(quantity)
                .status(OrderStatus.SUBMITTED)
                .message("委托已提交")
                .submitTime(LocalDateTime.now())
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public OrderStatus queryOrderStatus(String orderId) {
        log.info("[ZXBroker] 查询委托状态: orderId={}", orderId);
        Optional<BrokerOrderSnapshot> order = getTodayOrderSnapshots().stream()
                .filter(item -> orderId.equals(item.getOrderId()))
                .findFirst();
        if (order.isEmpty()) {
            log.warn("[ZXBroker] 当日委托中未找到订单: orderId={}", orderId);
            return OrderStatus.UNKNOWN;
        }
        return order.get().getStatus();
    }

    /** {@inheritDoc} */
    @Override
    public Boolean cancelOrder(String orderId) {
        log.info("[ZXBroker] 请求撤销委托: orderId={}", orderId);
        if (!tradingProperties.isLiveWriteAllowed()) {
            log.warn("[ZXBroker] 真实撤单被交易写入总门禁阻止");
            return false;
        }
        String token = requireToken();
        if (token == null) {
            return false;
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("action", ACTION_CANCEL_ORDER);
        parameters.put("ContactID", orderId);
        parameters.put("token", token);
        JSONObject response = requestUtils.request(requestUtils.buildParams(parameters));
        return "0".equals(response.getString("ERRORNO"));
    }

    /** {@inheritDoc} */
    @Override
    public List<OrderResult> getTodayOrders() {
        return getTodayFillSnapshots().stream()
                .map(fill -> OrderResult.builder()
                        .success(true)
                        .orderId(fill.getOrderId())
                        .stockCode(fill.getStockCode())
                        .stockName(fill.getStockName())
                        .direction(fill.getDirection())
                        .price(fill.getFillPrice())
                        .quantity(fill.getFillQuantity())
                        .amount(fill.getFillAmount())
                        .status(OrderStatus.FILLED)
                        .message("已成交")
                        .fillTime(fill.getFillTime())
                        .build())
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Position> getPositions() {
        log.info("[ZXBroker] 获取持仓列表");
        JSONArray rows = queryArray(ACTION_GET_POSITIONS, Map.of("ReqlinkType", 1));
        List<Position> positions = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            try {
                String[] fields = responseParser.splitRow(rows.getString(index));
                if (fields.length < 16) {
                    log.warn("[ZXBroker] 持仓第{}行字段不足，实际字段数={}", index, fields.length);
                    continue;
                }
                Integer totalQuantity = responseParser.parseInteger(fields[1]);
                Integer availableQuantity = responseParser.parseInteger(fields[2]);
                BigDecimal averageCost = responseParser.parseDecimal(fields[3]);
                BigDecimal currentPrice = responseParser.parseDecimal(fields[4]);
                if (totalQuantity == null || availableQuantity == null || currentPrice == null) {
                    log.warn("[ZXBroker] 持仓第{}行包含无法解析的核心字段", index);
                    continue;
                }

                Position position = new Position();
                position.setStockName(responseParser.trimToNull(fields[0]));
                position.setQuantity(totalQuantity);
                position.setAvailableQuantity(availableQuantity);
                position.setFrozenQuantity(Math.max(0, totalQuantity - availableQuantity));
                position.setAvgCost(averageCost);
                position.setCurrentPrice(currentPrice);
                position.setStockCode(responseParser.trimToNull(fields[9]));
                position.setMarket(responseParser.trimToNull(fields[15]));
                positions.add(position);
            } catch (RuntimeException exception) {
                log.warn("[ZXBroker] 持仓第{}行解析失败: {}", index, exception.getMessage());
            }
        }
        return positions;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAuthenticated() {
        return Boolean.TRUE.equals(config.getEnabled()) && requestUtils.getToken() != null;
    }
    /** {@inheritDoc} */
    @Override
    public void ensureAuthenticated() {
        requestUtils.requireToken();
    }

    /** {@inheritDoc} */
    @Override
    public List<BrokerOrderSnapshot> getTodayOrderSnapshots() {
        log.info("[ZXBroker] 获取当日全部委托");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("ReqlinkType", 1);
        parameters.put("StartPos", 0);
        parameters.put("MaxCount", config.getMaxOrderCount());
        return responseParser.parseOrderRows(queryArray(ACTION_QUERY_TODAY_ORDERS, parameters));
    }

    /** {@inheritDoc} */
    @Override
    public List<BrokerFillSnapshot> getTodayFillSnapshots() {
        log.info("[ZXBroker] 获取当日成交");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("ReqlinkType", 1);
        parameters.put("StartPos", 0);
        parameters.put("MaxCount", config.getMaxOrderCount());
        return responseParser.parseFillRows(queryArray(ACTION_QUERY_TODAY_FILLS, parameters), true);
    }

    /** {@inheritDoc} */
    @Override
    public List<BrokerOrderSnapshot> getHistoryOrderSnapshots(LocalDate startDate, LocalDate endDate) {
        log.info("[ZXBroker] 获取历史委托: startDate={}, endDate={}", startDate, endDate);
        return responseParser.parseOrderRows(queryHistoryArray(ACTION_QUERY_HISTORY_ORDERS, startDate, endDate));
    }

    /** {@inheritDoc} */
    @Override
    public List<BrokerFillSnapshot> getHistoryFillSnapshots(LocalDate startDate, LocalDate endDate) {
        log.info("[ZXBroker] 获取历史成交: startDate={}, endDate={}", startDate, endDate);
        return responseParser.parseFillRows(queryHistoryArray(ACTION_QUERY_HISTORY_FILLS, startDate, endDate), false);
    }

    /**
     * 使用运行环境配置的资金账号和加密密码登录券商平台。
     *
     * @return 登录是否成功
     */
    public boolean loginConfiguredAccount() {
        log.info("[ZXBroker] 使用配置账号登录券商平台");
        String token = requestUtils.loginConfiguredAccount();
        return token != null && !token.isBlank();
    }


    /**
     * 按历史费率估算佣金。
     * 最终实盘费用必须以后续真实交割单配置为准。
     *
     * @param amount 成交金额
     * @return 估算佣金
     */
    public BigDecimal calculateFee(BigDecimal amount) {
        BigDecimal fee = amount.multiply(new BigDecimal("0.0005"));
        return fee.max(new BigDecimal("5")).setScale(2, RoundingMode.HALF_UP);
    }

    private JSONArray queryHistoryArray(int action, LocalDate startDate, LocalDate endDate) {
        JSONArray allRows = new JSONArray();
        int pageSize = Math.max(1, config.getHistoryPageSize());
        int maxRecords = Math.max(pageSize, config.getHistoryMaxRecordsPerWindow());
        int startPosition = 0;

        while (startPosition < maxRecords) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("ReqlinkType", 1);
            parameters.put("StartPos", startPosition);
            parameters.put("MaxCount", pageSize);
            parameters.put("BeginDate", startDate.format(BROKER_DATE_FORMAT));
            parameters.put("EndDate", endDate.format(BROKER_DATE_FORMAT));
            JSONArray pageRows = queryArray(action, parameters);

            int dataCount = 0;
            for (int index = 0; index < pageRows.size(); index++) {
                String row = pageRows.getString(index);
                if (!responseParser.isHeaderOrEmpty(row)) {
                    allRows.add(row);
                    dataCount++;
                }
            }
            if (dataCount == 0 || dataCount < pageSize) {
                break;
            }
            startPosition += dataCount;
            pauseBetweenHistoryRequests();
        }
        if (allRows.size() >= maxRecords) {
            log.warn("[ZXBroker] 历史窗口达到最大记录数限制: action={}, maxRecords={}", action, maxRecords);
        }
        return allRows;
    }

    private JSONArray queryArray(int action, Map<String, Object> extraParameters) {
        String token = requireToken();
        if (token == null) {
            return new JSONArray();
        }
        Map<String, Object> parameters = new HashMap<>(extraParameters);
        parameters.put("action", action);
        parameters.put("token", token);
        parameters.put("Token", token);
        JSONArray rows = requestUtils.requestArray(requestUtils.buildParams(parameters));
        return rows != null ? rows : new JSONArray();
    }

    private String requireToken() {
        return requestUtils.requireToken();
    }

    private String normalizeWriteDirection(String direction) {
        if ("BUY".equalsIgnoreCase(direction)) {
            return "B";
        }
        if ("SELL".equalsIgnoreCase(direction)) {
            return "S";
        }
        return null;
    }

    private void pauseBetweenHistoryRequests() {
        try {
            Thread.sleep(Math.max(0, config.getHistoryRequestIntervalMs()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("历史券商查询被中断", exception);
        }
    }
}
// AI_GENERATE_END-------------------
