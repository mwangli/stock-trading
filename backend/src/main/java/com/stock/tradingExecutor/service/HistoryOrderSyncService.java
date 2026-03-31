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

    private static final int DEFAULT_MAX_COUNT = 100;
    private static final int MAX_RETRY_TIMES = 3;
    private static final int BATCH_SIZE = 50;

    private final HistoryOrderRepository historyOrderRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public HistoryOrderSyncService(HistoryOrderRepository historyOrderRepository,
                                   @Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.historyOrderRepository = historyOrderRepository;
        this.stringRedisTemplate = stringRedisTemplate;
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
     * 执行全量历史订单同步
     * 从当前日期往前推3年的数据
     */
    public SyncResult syncAllHistoryOrders() {
        return syncHistoryOrdersByDateRange(
                LocalDate.now().minusYears(3),
                LocalDate.now()
        );
    }

    /**
     * 按指定日期范围同步历史订单
     */
    public SyncResult syncHistoryOrdersByDateRange(LocalDate startDate, LocalDate endDate) {
        String batchNo = generateBatchNo();
        log.info("[历史订单同步] 开始同步任务, 批次号: {}, 日期范围: {} ~ {}",
                batchNo, startDate.format(DATE_FORMATTER), endDate.format(DATE_FORMATTER));

        long startTime = System.currentTimeMillis();
        int totalFetched = 0;
        int savedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        int startPos = 0;
        boolean hasMore = true;

        try {
            while (hasMore) {
                List<HistoryOrder> orders = fetchOrdersByPage(startDate, endDate, startPos, DEFAULT_MAX_COUNT);

                if (orders.isEmpty()) {
                    log.debug("[历史订单同步] 第{}页数据为空，停止同步", startPos / DEFAULT_MAX_COUNT + 1);
                    hasMore = false;
                    break;
                }

                totalFetched += orders.size();
                log.info("[历史订单同步] 第{}页获取 {} 条订单", startPos / DEFAULT_MAX_COUNT + 1, orders.size());

                SyncPageResult pageResult = saveOrdersWithDedup(orders, batchNo);
                savedCount += pageResult.saved();
                duplicateCount += pageResult.duplicate();
                failedCount += pageResult.failed();

                if (orders.size() < DEFAULT_MAX_COUNT) {
                    hasMore = false;
                } else {
                    startPos += DEFAULT_MAX_COUNT;
                }

                if (startPos >= 5000) {
                    log.warn("[历史订单同步] 单次同步超过5000条，提前结束以避免接口限制");
                    break;
                }
            }

            long costTime = System.currentTimeMillis() - startTime;
            log.info("[历史订单同步] 同步完成, 批次号: {}, 总获取: {}, 新增: {}, 重复: {}, 失败: {}, 耗时: {}ms",
                    batchNo, totalFetched, savedCount, duplicateCount, failedCount, costTime);

            return new SyncResult(totalFetched, savedCount, duplicateCount, failedCount, batchNo, costTime);

        } catch (Exception e) {
            log.error("[历史订单同步] 同步过程发生异常, 批次号: {}", batchNo, e);
            throw new RuntimeException("历史订单同步失败: " + e.getMessage(), e);
        }
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
        params.put("action", "115");
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

                // 跳过表头行（第一行通常是表头）
                if (row.contains("日期|委托编号") || row.startsWith("日期|")) {
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
     * 格式: "日期|委托编号|市场类型|股东帐号|代码|名称|买卖方向|价格|数量|成交金额|流水号|时间|备注|证券全称|"
     */
    private HistoryOrder parseOrderRow(String row) {
        String[] fields = row.split("\\|");

        if (fields.length < 13) {
            log.debug("[历史订单同步] 数据字段不足，跳过: {}", row);
            return null;
        }

        HistoryOrder order = new HistoryOrder();
        order.setOrderDate(fields[0].trim());
        order.setOrderNo(fields[1].trim());
        order.setMarketType(fields[2].trim());
        order.setStockAccount(fields[3].trim());
        order.setStockCode(fields[4].trim());
        order.setStockName(fields[5].trim());
        order.setDirection(fields[6].trim());
        order.setPrice(parseDecimal(fields[7].trim()));
        order.setQuantity(parseInteger(fields[8].trim()));
        order.setAmount(parseDecimal(fields[9].trim()));
        order.setSerialNo(fields[10].trim());
        order.setOrderTime(fields[11].trim());
        order.setRemark(fields[12].trim());
        if (fields.length > 13) {
            order.setFullName(fields[13].trim());
        }

        if (order.getOrderNo() == null || order.getOrderNo().isEmpty()) {
            log.debug("[历史订单同步] 委托编号为空，跳过");
            return null;
        }

        return order;
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

    /**
     * 批量保存订单并进行去重
     */
    @Transactional
    private SyncPageResult saveOrdersWithDedup(List<HistoryOrder> orders, String batchNo) {
        int saved = 0;
        int duplicate = 0;
        int failed = 0;
        LocalDateTime now = LocalDateTime.now();

        List<HistoryOrder> toSave = new ArrayList<>();

        for (HistoryOrder order : orders) {
            try {
                if (historyOrderRepository.existsByOrderNoAndOrderDate(order.getOrderNo(), order.getOrderDate())) {
                    duplicate++;
                    continue;
                }

                order.setSyncBatchNo(batchNo);
                order.setLastSyncTime(now);
                toSave.add(order);

            } catch (Exception e) {
                log.warn("[历史订单同步] 处理订单失败: {}, {}", order.getOrderNo(), e.getMessage());
                failed++;
            }
        }

        if (!toSave.isEmpty()) {
            try {
                historyOrderRepository.saveAll(toSave);
                saved = toSave.size();
            } catch (Exception e) {
                log.error("[历史订单同步] 批量保存失败: {}", e.getMessage(), e);
                failed += toSave.size();
                saved = 0;
            }
        }

        return new SyncPageResult(saved, duplicate, failed);
    }

    private String generateBatchNo() {
        return "HO-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record SyncPageResult(int saved, int duplicate, int failed) {}

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
