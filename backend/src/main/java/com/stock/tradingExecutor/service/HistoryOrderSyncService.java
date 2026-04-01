package com.stock.tradingExecutor.service;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.tradingExecutor.domain.entity.HistoryOrder;
import com.stock.tradingExecutor.persistence.HistoryOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 历史订单同步服务
 * 对接券商API实现历史订单数据的同步、解析、存储
 *
 * @author mwangli
 * @since 2026-03-31
 */
@Slf4j
@Service
public class HistoryOrderSyncService {

    private static final String API_URL = "https://weixin.citicsinfo.com/reqxml";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String REDIS_TOKEN_KEY = "LOGIN_TOKEN";

    private static final int DEFAULT_MAX_COUNT = 1000;
    private static final int MAX_RETRY_TIMES = 3;
    private static final int BATCH_SIZE = 50;

    private final HistoryOrderRepository historyOrderRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final HistoryOrderPersistenceService historyOrderPersistenceService;

    @Autowired
    public HistoryOrderSyncService(HistoryOrderRepository historyOrderRepository,
                                   @Autowired(required = false) StringRedisTemplate stringRedisTemplate,
                                   HistoryOrderPersistenceService historyOrderPersistenceService) {
        this.historyOrderRepository = historyOrderRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.historyOrderPersistenceService = historyOrderPersistenceService;
    }

    /**
     * 同步结果封装
     */
    public record SyncResult(
            int totalFetched,
            int savedCount,
            int duplicateCount,
            int failedCount,
            String syncBatchNo,
            long costTimeMs
    ) {}

    /**
     * 执行全量历史订单同步（按月查询，最后批量写入）
     * 从2023年1月开始，按月同步到当前月份
     */
    public SyncResult syncAllHistoryOrders() {
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.now();
        return syncHistoryOrdersByMonth(startDate, endDate);
    }

    /**
     * 按月查询历史订单，最后批量写入数据库
     */
    public SyncResult syncHistoryOrdersByMonth(LocalDate startDate, LocalDate endDate) {
        String batchNo = generateBatchNo();
        log.info("[历史订单同步] 开始按月同步任务, 批次号: {}, 日期范围: {} ~ {}",
                batchNo, startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));

        long startTime = System.currentTimeMillis();
        List<HistoryOrder> allOrders = new ArrayList<>();
        int totalMonths = 0;

        LocalDate monthStart = startDate.withDayOfMonth(1);
        while (!monthStart.isAfter(endDate)) {
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            if (monthEnd.isAfter(endDate)) {
                monthEnd = endDate;
            }

            totalMonths++;
            log.info("[历史订单同步] 正在查询第 {}/{} 月: {} ~ {}",
                    totalMonths, "?", monthStart.format(DATE_FORMATTER), monthEnd.format(DATE_FORMATTER));

            List<HistoryOrder> monthOrders = fetchAllOrdersInRange(monthStart, monthEnd);
            log.info("[历史订单同步] {} 年 {} 月获取 {} 条订单",
                    monthStart.getYear(), monthStart.getMonthValue(), monthOrders.size());
            allOrders.addAll(monthOrders);

            monthStart = monthStart.plusMonths(1);
        }

        log.info("[历史订单同步] 共扫描 {} 个月份，累计获取 {} 条订单，开始批量写入数据库", totalMonths, allOrders.size());

        HistoryOrderPersistenceService.SyncPageResult finalResult = historyOrderPersistenceService.saveOrders(allOrders, batchNo);

        long costTime = System.currentTimeMillis() - startTime;
        log.info("[历史订单同步] 同步完成, 批次号: {}, 总获取: {}, 新增: {}, 重复: {}, 失败: {}, 耗时: {}ms",
                batchNo, allOrders.size(), finalResult.saved(), finalResult.duplicate(), finalResult.failed(), costTime);

        return new SyncResult(allOrders.size(), finalResult.saved(), finalResult.duplicate(), finalResult.failed(), batchNo, costTime);
    }

    /**
     * 获取指定日期范围内的所有订单（自动翻页）
     */
    private List<HistoryOrder> fetchAllOrdersInRange(LocalDate startDate, LocalDate endDate) {
        List<HistoryOrder> allOrders = new ArrayList<>();
        int startPos = 0;
        boolean hasMore = true;

        while (hasMore) {
            List<HistoryOrder> pageOrders = fetchOrdersByPage(startDate, endDate, startPos, DEFAULT_MAX_COUNT);

            if (pageOrders.isEmpty()) {
                hasMore = false;
                break;
            }

            allOrders.addAll(pageOrders);

            if (pageOrders.size() < DEFAULT_MAX_COUNT) {
                hasMore = false;
            } else {
                startPos += DEFAULT_MAX_COUNT;
            }

            if (startPos >= 5000) {
                log.warn("[历史订单同步] 单月超过5000条，提前结束");
                break;
            }
        }

        return allOrders;
    }

    /**
     * 按指定日期范围同步历史订单（原有方法保留）
     */
    public SyncResult syncHistoryOrdersByDateRange(LocalDate startDate, LocalDate endDate) {
        String batchNo = generateBatchNo();
        log.info("[历史订单同步] 开始同步任务, 批次号: {}, 日期范围: {} ~ {}",
                batchNo, startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));

        long startTime = System.currentTimeMillis();

        List<HistoryOrder> orders = fetchAllOrdersInRange(startDate, endDate);
        HistoryOrderPersistenceService.SyncPageResult result = historyOrderPersistenceService.saveOrders(orders, batchNo);

        long costTime = System.currentTimeMillis() - startTime;
        log.info("[历史订单同步] 同步完成, 批次号: {}, 总获取: {}, 新增: {}, 重复: {}, 失败: {}, 耗时: {}ms",
                batchNo, orders.size(), result.saved(), result.duplicate(), result.failed(), costTime);

        return new SyncResult(orders.size(), result.saved(), result.duplicate(), result.failed(), batchNo, costTime);
    }

    /**
     * 从Redis获取Token
     */
    private String getTokenFromRedis() {
        if (stringRedisTemplate == null) {
            log.warn("[历史订单同步] StringRedisTemplate未注入，无法从Redis获取Token");
            return null;
        }
        try {
            String token = stringRedisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
            if (token != null && !token.isEmpty()) {
                log.info("[历史订单同步] 从Redis获取Token成功，长度: {}", token.length());
                return token;
            }
        } catch (Exception e) {
            log.warn("[历史订单同步] 从Redis获取Token失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 分页获取订单数据
     */
    private List<HistoryOrder> fetchOrdersByPage(LocalDate startDate, LocalDate endDate, int startPos, int maxCount) {
        Map<String, Object> params = buildRequestParams(startDate, endDate, startPos, maxCount);

        log.info("[历史订单同步] 请求参数: {}", params);

        for (int retry = 0; retry < MAX_RETRY_TIMES; retry++) {
            try {
                String response = HttpUtil.createPost(API_URL)
                        .form(params)
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15")
                        .header("Referer", "https://weixin.citicsinfo.com/tztweb/hq/index.html")
                        .execute()
                        .body();

                if (response == null || response.isEmpty()) {
                    log.warn("[历史订单同步] 第{}次重试: 接口返回空", retry + 1);
                    continue;
                }

                log.info("[历史订单同步] API原始响应: {}", response);
                return parseOrdersFromResponse(response);

            } catch (Exception e) {
                log.warn("[历史订单同步] 第{}次重试失败: {}", retry + 1, e.getMessage());
                if (retry == MAX_RETRY_TIMES - 1) {
                    log.error("[历史订单同步] 达到最大重试次数，获取订单数据失败", e);
                    return new ArrayList<>();
                }
            }
        }

        return new ArrayList<>();
    }

    /**
     * 构建请求参数
     */
    private Map<String, Object> buildRequestParams(LocalDate startDate, LocalDate endDate, int startPos, int maxCount) {
        Map<String, Object> params = new HashMap<>();
        params.put("MobileCode", "13278828091");
        params.put("CHANNEL", "");
        params.put("Reqno", System.currentTimeMillis());
        params.put("ReqlinkType", "1");
        params.put("newindex", "1");
        params.put("action", "5018");
        params.put("StartPos", startPos);
        params.put("MaxCount", maxCount);
        params.put("intacttoserver", "@ClZvbHVtZUluZm8JAAAAMTdBQy00QzAx");
        params.put("cfrom", "H5");
        params.put("tfrom", "PC");
        params.put("BeginDate", startDate.format(DATE_FORMATTER));
        params.put("EndDate", endDate.format(DATE_FORMATTER));

        String token = getTokenFromRedis();
        if (token == null || token.isEmpty()) {
            log.warn("[历史订单同步] Token为空，请先注入有效Token到Redis key: {}", REDIS_TOKEN_KEY);
        } else {
            params.put("Token", token);
        }

        return params;
    }

    /**
     * 解析接口响应中的GRID0字段
     * GRID0格式: "日期|委托编号|市场类型|股东帐号|代码|名称|买卖方向|价格|数量|成交金额|流水号|时间|备注|证券全称|"
     */
    private List<HistoryOrder> parseOrdersFromResponse(String response) {
        List<HistoryOrder> orders = new ArrayList<>();

        try {
            JSONObject jsonResponse = JSONObject.parseObject(response);

            int maxCount = jsonResponse.getIntValue("MAXCOUNT", -1);
            if (maxCount == 0) {
                log.info("[历史订单同步] API返回MAXCOUNT=0，该日期范围内无订单数据");
                return orders;
            }

            JSONArray grid0 = jsonResponse.getJSONArray("GRID0");

            if (grid0 == null || grid0.isEmpty()) {
                log.debug("[历史订单同步] GRID0为空或不存在，响应内容: {}", response.substring(0, Math.min(200, response.length())));
                return orders;
            }

            for (int i = 0; i < grid0.size(); i++) {
                String row = grid0.getString(i);
                if (row == null || row.isEmpty()) {
                    continue;
                }

                // 跳过表头行（第一行通常是表头，包含"委托日期"或"日期"字段）
                if (row.contains("委托日期|") || row.startsWith("日期|") || row.contains("证券代码|证券")) {
                    log.debug("[历史订单同步] 跳过表头行: {}", row);
                    continue;
                }

                try {
                    HistoryOrder order = parseOrderRow(row);
                    if (order != null) {
                        orders.add(order);
                    }
                } catch (Exception e) {
                    log.warn("[历史订单同步] 解析第{}行数据失败: {}", i + 1, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[历史订单同步] 解析响应失败: {}", e.getMessage(), e);
        }

        return orders;
    }

    /**
     * 解析单行订单数据
     * action=5018 格式: "委托日期|时间|证券代码|证券名称|委托类别|买卖方向|委托状态|委托价格|数量|委托编号|均价|成交数量|股东代码|交易类别|"
     * 例如: "20240506|093258|603098|森特股份|委托|卖出|全部成交|9.6700|100|6906|9.6700|100|A826158733|上海|"
     */
    private HistoryOrder parseOrderRow(String row) {
        String[] fields = row.split("\\|");

        if (fields.length < 13) {
            log.debug("[历史订单同步] 数据字段不足，跳过: {}", row);
            return null;
        }

        HistoryOrder order = new HistoryOrder();
        order.setOrderDate(fields[0].trim());
        order.setOrderTime(fields[1].trim());
        order.setStockCode(fields[2].trim());
        order.setStockName(fields[3].trim());
        order.setRemark(fields[4].trim());
        order.setDirection(mapDirection(fields[5].trim()));
        order.setPrice(parseDecimal(fields[7].trim()));
        order.setQuantity(parseInteger(fields[8].trim()));
        order.setOrderNo(fields[9].trim());
        order.setAmount(parseDecimal(fields[10].trim()));
        order.setSerialNo(fields[11].trim());
        order.setStockAccount(fields[12].trim());
        order.setMarketType(fields[13].trim());

        // 设置订单提交时间（委托日期+委托时间）
        order.setOrderSubmitTime(parseOrderSubmitTime(order.getOrderDate(), order.getOrderTime()));

        if (order.getOrderNo() == null || order.getOrderNo().isEmpty()) {
            log.debug("[历史订单同步] 委托编号为空，跳过");
            return null;
        }

        return order;
    }

    /**
     * 解析订单提交时间
     */
    private LocalDateTime parseOrderSubmitTime(String orderDate, String orderTime) {
        try {
            String dateTimeStr = orderDate + orderTime;
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * 映射买卖方向
     */
    private String mapDirection(String direction) {
        if (direction == null) return direction;
        return switch (direction) {
            case "买入" -> "B";
            case "卖出" -> "S";
            default -> direction;
        };
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String generateBatchNo() {
        return "HO-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 获取最近一次同步的订单数量
     */
    public long getLatestSyncCount() {
        var batchNos = historyOrderRepository.findLatestSyncBatchNo(
                org.springframework.data.domain.PageRequest.of(0, 1)
        );
        if (batchNos.isEmpty()) {
            return 0;
        }
        return historyOrderRepository.countBySyncBatchNo(batchNos.get(0));
    }

    /**
     * 清理指定天数之前的同步批次
     */
    @Transactional
    public void cleanupOldSyncBatches(int daysToKeep) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(daysToKeep);
        List<HistoryOrder> oldOrders = historyOrderRepository.findRecentlySynced(cutoffTime);

        if (!oldOrders.isEmpty()) {
            log.info("[历史订单同步] 清理 {} 条过期同步记录", oldOrders.size());
        }
    }
}
