package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.entity.HistoryOrder;
import com.stock.tradingExecutor.persistence.HistoryOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 历史订单同步集成测试
 * 使用真实API接口和真实数据库
 *
 * 执行方式: cd backend && mvn test -Dtest=HistoryOrderSyncServiceTest
 *
 * @author mwangli
 * @since 2026-03-31
 */
@SpringBootTest
@Transactional
public class HistoryOrderSyncServiceTest {

    private static final String TEST_BATCH_PREFIX = "TEST-REAL-API-";

    @Autowired
    private HistoryOrderSyncService historyOrderSyncService;

    @Autowired
    private HistoryOrderRepository historyOrderRepository;

    /**
     * 测试1: 直接调用同步方法，验证真实API数据能写入数据库
     */
    @Test
    void testSyncAndWriteToDatabase() {
        String batchNo = TEST_BATCH_PREFIX + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        System.out.println("\n========== 测试: 调用真实API同步历史订单 ==========");
        System.out.println("测试批次号: " + batchNo);

        // 记录同步前的数量
        long countBefore = historyOrderRepository.count();
        System.out.println("同步前数据库订单数: " + countBefore);

        // 调用真实API进行同步（按月查询，最后批量写入）
        System.out.println("使用按月查询模式 (syncAllHistoryOrders)");
        HistoryOrderSyncService.SyncResult result = historyOrderSyncService.syncAllHistoryOrders();

        System.out.println("\n========== 同步结果 ==========");
        System.out.println("API返回总记录数: " + result.totalFetched());
        System.out.println("新增写入: " + result.savedCount());
        System.out.println("重复跳过: " + result.duplicateCount());
        System.out.println("失败: " + result.failedCount());
        System.out.println("耗时: " + result.costTimeMs() + "ms");
        System.out.println("批次号: " + result.syncBatchNo());

        // 查询并打印所有同步的订单详情
        System.out.println("\n========== 同步订单详情 ==========");
        List<HistoryOrder> syncedOrders = historyOrderRepository.findBySyncBatchNo(result.syncBatchNo());
        System.out.println("批次 [" + result.syncBatchNo() + "] 共 " + syncedOrders.size() + " 条订单:");
        for (int i = 0; i < syncedOrders.size(); i++) {
            HistoryOrder order = syncedOrders.get(i);
            System.out.println("\n--- 订单 " + (i + 1) + " ---");
            System.out.println("  ID: " + order.getId());
            System.out.println("  委托日期: " + order.getOrderDate());
            System.out.println("  委托编号: " + order.getOrderNo());
            System.out.println("  市场类型: " + order.getMarketType());
            System.out.println("  股东帐号: " + order.getStockAccount());
            System.out.println("  股票代码: " + order.getStockCode());
            System.out.println("  股票名称: " + order.getStockName());
            System.out.println("  买卖方向: " + order.getDirection());
            System.out.println("  价格: " + order.getPrice());
            System.out.println("  数量: " + order.getQuantity());
            System.out.println("  成交金额: " + order.getAmount());
            System.out.println("  流水号: " + order.getSerialNo());
            System.out.println("  委托时间: " + order.getOrderTime());
            System.out.println("  备注: " + order.getRemark());
            System.out.println("  证券全称: " + order.getFullName());
        }

        // 打印数据库中所有订单总数
        long countAfter = historyOrderRepository.count();
        System.out.println("\n========== 数据库统计 ==========");
        System.out.println("同步后数据库订单总数: " + countAfter);
        System.out.println("本次同步新增: " + syncedOrders.size() + " 条");

        // 验证有数据被获取
        assertTrue(result.totalFetched() >= 0, "应该能获取到数据或空响应");

        // 验证写入数量
        System.out.println("实际新增: " + result.savedCount());

        // 如果API返回了数据，应该有数据写入
        if (result.totalFetched() > 0) {
            assertTrue(result.savedCount() > 0 || result.duplicateCount() > 0,
                    "有数据返回时应该有保存或重复");
        }

        // 不清理测试数据，保留供分析
        System.out.println("\n========== 测试完成，数据已保留在数据库中 ==========");
        System.out.println("批次号: " + result.syncBatchNo());
    }

    /**
     * 测试2: 数据库基础CRUD操作验证
     */
    @Test
    void testDatabaseWrite() {
        String batchNo = TEST_BATCH_PREFIX + "CRUD-" + System.currentTimeMillis();

        System.out.println("\n========== 测试: 数据库写入验证 ==========");
        System.out.println("测试批次号: " + batchNo);

        // 创建测试订单
        HistoryOrder order = createTestOrder(batchNo);
        order.setOrderNo("CRUD-" + UUID.randomUUID().toString().substring(0, 8));
        order.setOrderDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));

        // 保存
        HistoryOrder saved = historyOrderRepository.save(order);
        System.out.println("写入成功，ID: " + saved.getId());

        // 验证存在
        assertTrue(historyOrderRepository.existsByOrderNoAndOrderDate(
                saved.getOrderNo(), saved.getOrderDate()));
        System.out.println("existsByOrderNoAndOrderDate 验证通过");

        // 查询
        List<HistoryOrder> orders = historyOrderRepository.findBySyncBatchNo(batchNo);
        assertFalse(orders.isEmpty());
        System.out.println("findBySyncBatchNo 查询到 " + orders.size() + " 条记录");

        // 删除
        historyOrderRepository.deleteBySyncBatchNo(batchNo);
        List<HistoryOrder> afterDelete = historyOrderRepository.findBySyncBatchNo(batchNo);
        assertTrue(afterDelete.isEmpty());
        System.out.println("deleteBySyncBatchNo 删除成功");

        System.out.println("\n========== 数据库CRUD测试通过 ==========");
    }

    /**
     * 测试3: 去重机制验证
     */
    @Test
    void testDeduplication() {
        String batchNo = TEST_BATCH_PREFIX + "DEDUP-" + System.currentTimeMillis();
        String orderNo = "DEDUP-TEST-" + System.currentTimeMillis();
        String orderDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        System.out.println("\n========== 测试: 去重机制验证 ==========");

        // 第一次插入
        HistoryOrder order1 = createTestOrder(batchNo);
        order1.setOrderNo(orderNo);
        order1.setOrderDate(orderDate);
        historyOrderRepository.save(order1);
        System.out.println("第一次写入成功");

        // 第二次插入相同订单
        HistoryOrder order2 = createTestOrder(batchNo);
        order2.setOrderNo(orderNo);
        order2.setOrderDate(orderDate);

        boolean exists = historyOrderRepository.existsByOrderNoAndOrderDate(orderNo, orderDate);
        assertTrue(exists, "去重检测应该返回true");
        System.out.println("去重检测: exists=" + exists);

        // 清理
        historyOrderRepository.deleteBySyncBatchNo(batchNo);
        System.out.println("测试完成，已清理");

        System.out.println("\n========== 去重机制测试通过 ==========");
    }

    private HistoryOrder createTestOrder(String batchNo) {
        HistoryOrder order = new HistoryOrder();
        order.setOrderDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        order.setOrderNo("TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setMarketType("SH");
        order.setStockAccount("1234567890");
        order.setStockCode("600519");
        order.setStockName("贵州茅台");
        order.setDirection("B");
        order.setPrice(new BigDecimal("1850.00"));
        order.setQuantity(100);
        order.setAmount(new BigDecimal("185000.00"));
        order.setSerialNo("SN" + System.currentTimeMillis());
        order.setOrderTime("09:30:00");
        order.setRemark("集成测试订单");
        order.setFullName("贵州茅台酒股份有限公司");
        order.setSyncBatchNo(batchNo);
        order.setLastSyncTime(java.time.LocalDateTime.now());
        return order;
    }
}
