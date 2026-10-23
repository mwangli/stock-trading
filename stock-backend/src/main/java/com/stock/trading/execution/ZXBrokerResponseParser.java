// AI_GENERATE_START--
package com.stock.trading.execution;

import com.alibaba.fastjson2.JSONArray;
import com.stock.trading.entity.OrderStatus;
import com.stock.trading.vo.BrokerFillSnapshot;
import com.stock.trading.vo.BrokerOrderSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * ZX 券商竖线分隔报文解析器。
 *
 * 负责账户、持仓、委托和成交响应中的基础字段转换，不发起网络请求。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
public class ZXBrokerResponseParser {

    private static final DateTimeFormatter BROKER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 解析委托列表。
     *
     * @param rows 券商原始行
     * @return 有效委托快照
     */
    public List<BrokerOrderSnapshot> parseOrderRows(JSONArray rows) {
        List<BrokerOrderSnapshot> orders = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            String row = rows.getString(index);
            if (isHeaderOrEmpty(row)) {
                continue;
            }
            try {
                String[] fields = splitRow(row);
                if (fields.length < 14) {
                    log.warn("[ZXBroker] 委托第{}行字段不足，实际字段数={}", index, fields.length);
                    continue;
                }
                BrokerOrderSnapshot order = parseOrder(fields);
                if (order != null) {
                    orders.add(order);
                }
            } catch (RuntimeException exception) {
                log.warn("[ZXBroker] 委托第{}行解析失败: {}", index, exception.getMessage());
            }
        }
        return orders;
    }

    /**
     * 解析成交列表。
     *
     * @param rows 券商原始行
     * @param todayFormat 是否为当日成交格式
     * @return 有效成交快照
     */
    public List<BrokerFillSnapshot> parseFillRows(JSONArray rows, boolean todayFormat) {
        List<BrokerFillSnapshot> fills = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            String row = rows.getString(index);
            if (isHeaderOrEmpty(row)) {
                continue;
            }
            try {
                String[] fields = splitRow(row);
                if (fields.length < 12) {
                    log.warn("[ZXBroker] 成交第{}行字段不足，实际字段数={}", index, fields.length);
                    continue;
                }
                BrokerFillSnapshot fill = todayFormat
                        ? buildFill(fields[6], fields[5], fields[0], fields[1], fields[3], fields[2], fields[10], fields[11])
                        : buildFill(fields[1], fields[4], fields[5], fields[6], fields[7], fields[8], fields[0], fields[11]);
                if (fill != null) {
                    fills.add(fill);
                }
            } catch (RuntimeException exception) {
                log.warn("[ZXBroker] 成交第{}行解析失败: {}", index, exception.getMessage());
            }
        }
        return fills;
    }

    /**
     * 判断响应行是否为表头或空行。
     *
     * @param row 原始响应行
     * @return 是否应跳过
     */
    public boolean isHeaderOrEmpty(String row) {
        return row == null || row.isBlank() || row.contains("委托日期|") || row.startsWith("日期|")
                || row.contains("证券代码|证券");
    }

    /**
     * 按券商协议拆分字段。
     *
     * @param row 原始响应行
     * @return 字段数组
     */
    public String[] splitRow(String row) {
        return row == null ? new String[0] : row.split("\\|", -1);
    }

    /**
     * 解析金额或价格字段。
     *
     * @param value 原始文本
     * @return 数值，无法解析时返回空
     */
    public BigDecimal parseDecimal(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 解析数量字段。
     *
     * @param value 原始文本
     * @return 整数，无法解析时返回空
     */
    public Integer parseInteger(String value) {
        BigDecimal decimal = parseDecimal(value);
        return decimal != null ? decimal.intValue() : null;
    }

    /**
     * 清理空白文本。
     *
     * @param value 原始文本
     * @return 去除首尾空白后的文本，空值返回 null
     */
    public String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BrokerOrderSnapshot parseOrder(String[] fields) {
        BigDecimal orderPrice = parseDecimal(fields[7]);
        Integer orderQuantity = parseInteger(fields[8]);
        if (orderPrice == null || orderQuantity == null) {
            return null;
        }
        return BrokerOrderSnapshot.builder()
                .orderId(trimToNull(fields[9])).stockCode(trimToNull(fields[2]))
                .stockName(trimToNull(fields[3])).direction(mapDirection(fields[5]))
                .orderPrice(orderPrice).orderQuantity(orderQuantity)
                .averageFillPrice(parseDecimal(fields[10])).filledQuantity(parseInteger(fields[11]))
                .rawStatus(trimToNull(fields[6])).status(mapOrderStatus(fields[6]))
                .shareholderAccount(trimToNull(fields[12])).market(trimToNull(fields[13]))
                .orderTime(parseDateTime(fields[0], fields[1])).build();
    }

    private BrokerFillSnapshot buildFill(String orderId, String stockCode, String stockName, String direction,
                                         String priceValue, String quantityValue, String dateValue, String timeValue) {
        BigDecimal price = parseDecimal(priceValue);
        Integer quantity = parseInteger(quantityValue);
        if (price == null || quantity == null || quantity == 0) {
            return null;
        }
        int absoluteQuantity = Math.abs(quantity);
        return BrokerFillSnapshot.builder()
                .orderId(trimToNull(orderId)).stockCode(trimToNull(stockCode)).stockName(trimToNull(stockName))
                .direction(mapDirection(direction)).fillPrice(price).fillQuantity(absoluteQuantity)
                .fillAmount(price.multiply(BigDecimal.valueOf(absoluteQuantity)))
                .fillTime(parseDateTime(dateValue, timeValue)).build();
    }

    private String mapDirection(String rawDirection) {
        String direction = trimToNull(rawDirection);
        if (direction == null) {
            return "UNKNOWN";
        }
        if ("买入".equals(direction) || "B".equalsIgnoreCase(direction) || "BUY".equalsIgnoreCase(direction)) {
            return "BUY";
        }
        if ("卖出".equals(direction) || "S".equalsIgnoreCase(direction) || "SELL".equalsIgnoreCase(direction)) {
            return "SELL";
        }
        return "UNKNOWN";
    }

    private OrderStatus mapOrderStatus(String rawStatus) {
        String status = trimToNull(rawStatus);
        if (status == null) {
            return OrderStatus.UNKNOWN;
        }
        return switch (status) {
            case "已成", "全部成交" -> OrderStatus.FILLED;
            case "已报", "已申报" -> OrderStatus.SUBMITTED;
            case "部成", "部分成交" -> OrderStatus.PARTIAL;
            case "已撤", "已报待撤", "全部撤单" -> OrderStatus.CANCELLED;
            case "废单", "拒绝" -> OrderStatus.REJECTED;
            default -> OrderStatus.UNKNOWN;
        };
    }

    private LocalDateTime parseDateTime(String dateValue, String timeValue) {
        String dateText = trimToNull(dateValue);
        String timeText = trimToNull(timeValue);
        if (dateText == null) {
            return null;
        }
        try {
            LocalDate date = dateText.contains("-")
                    ? LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE)
                    : LocalDate.parse(dateText, BROKER_DATE_FORMAT);
            if (timeText == null) {
                return date.atStartOfDay();
            }
            LocalTime time = timeText.contains(":")
                    ? LocalTime.parse(timeText, DateTimeFormatter.ISO_LOCAL_TIME)
                    : LocalTime.parse(timeText, DateTimeFormatter.ofPattern("HHmmss"));
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
// AI_GENERATE_END--
