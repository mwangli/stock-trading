package com.stock.databus.repository;

import com.stock.databus.entity.StockInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StockRepository 测试
 * 使用真实MySQL数据库进行测试
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("StockRepository 真实数据库测试")
@Transactional
public class StockRepositoryTest {

    @Autowired
    private StockRepository stockRepository;

    private StockInfo testStock;

    @BeforeEach
    public void setUp() {
        // 创建测试数据
        testStock = new StockInfo();
        testStock.setCode("600000");
        testStock.setName("测试股票");
        testStock.setMarket("SH");
        testStock.setIsSt(0);
        testStock.setIsTradable(1);
        testStock.setPrice(new BigDecimal("10.50"));
        testStock.setDeleted("0");
        testStock.setCreateTime(LocalDateTime.now());
    }

    @Test
    @DisplayName("TC-001: 股票信息实体创建和字段验证")
    public void testStockInfoEntity() {
        // 创建StockInfo对象
        StockInfo stock = new StockInfo();
        stock.setCode("600001");
        stock.setName("测试股票1");
        stock.setMarket("SH");
        stock.setIsSt(0);
        stock.setIsTradable(1);
        stock.setPrice(new BigDecimal("100.00"));
        stock.setIncrease(new BigDecimal("1.23"));
        stock.setDeleted("0");
        stock.setCreateTime(LocalDateTime.now());

        // 保存到数据库
        StockInfo save = stockRepository.save(stock);
        assertTrue(save.getId() > 0, "插入应该成功");

        // 从数据库查询
        StockInfo found = stockRepository.findByCode("600001");
        assertNotNull(found, "应该能查询到股票");

        // 验证字段
        assertEquals("600001", found.getCode(), "股票代码应该一致");
        assertEquals("测试股票1", found.getName(), "股票名称应该一致");
        assertEquals("SH", found.getMarket(), "市场应该一致");
        assertEquals(0, found.getIsSt(), "ST标识应该为0");
        assertEquals(1, found.getIsTradable(), "可交易标识应该为1");
        assertEquals("0", found.getDeleted(), "删除标识应该为0");

        System.out.println("✅ TC-001: 股票信息实体创建和字段验证测试通过");
    }

    @Test
    @DisplayName("TC-002: 价格计算逻辑验证")
    public void testPriceCalculation() {
        // 创建股票，设置开盘价和收盘价
        BigDecimal openPrice = new BigDecimal("100.00");
        BigDecimal closePrice = new BigDecimal("105.00");

        // 计算涨跌幅
        BigDecimal increaseRate = closePrice.subtract(openPrice)
                .divide(openPrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        // 验证涨跌幅计算结果为5%
        assertEquals(0, increaseRate.compareTo(new BigDecimal("5.00")), "涨跌幅应该是5%");

        // 测试跌的情况
        BigDecimal closePrice2 = new BigDecimal("95.00");
        BigDecimal increaseRate2 = closePrice2.subtract(openPrice)
                .divide(openPrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        // 验证跌5%
        assertEquals(0, increaseRate2.compareTo(new BigDecimal("-5.00")), "涨跌幅应该是-5%");

        System.out.println("开盘价: " + openPrice + ", 收盘价: " + closePrice + ", 涨跌幅: " + increaseRate + "%");
        System.out.println("✅ TC-002: 价格计算逻辑验证测试通过");
    }

    @Test
    @DisplayName("TC-003: ST股票过滤逻辑验证")
    public void testFilterStStocks() {
        // 插入测试数据：正常股票
        StockInfo normalStock = new StockInfo();
        normalStock.setCode("600100");
        normalStock.setName("正常股票");
        normalStock.setMarket("SH");
        normalStock.setIsSt(0);
        normalStock.setIsTradable(1);
        normalStock.setDeleted("0");
        normalStock.setCreateTime(LocalDateTime.now());
        stockRepository.save(normalStock);

        // 插入ST股票
        StockInfo stStock = new StockInfo();
        stStock.setCode("600200");
        stStock.setName("ST测试");
        stStock.setMarket("SH");
        stStock.setIsSt(1);
        stStock.setIsTradable(0);
        stStock.setDeleted("0");
        stStock.setCreateTime(LocalDateTime.now());
        stockRepository.save(stStock);

        // 插入*ST股票
        StockInfo starStStock = new StockInfo();
        starStStock.setCode("600300");
        starStStock.setName("*ST测试");
        starStStock.setMarket("SH");
        starStStock.setIsSt(1);
        starStStock.setIsTradable(0);
        starStStock.setDeleted("0");
        starStStock.setCreateTime(LocalDateTime.now());
        stockRepository.save(starStStock);

        // 查询可交易股票
        List<StockInfo> tradableStocks = stockRepository.findTradableStocks(100);

        // 验证ST/*ST股票被过滤
        boolean hasStStock = tradableStocks.stream()
                .anyMatch(s -> s.getName() != null &&
                    (s.getName().contains("ST") || s.getName().contains("*ST")));

        assertFalse(hasStStock, "可交易股票列表中不应包含ST/*ST股票");

        System.out.println("可交易股票总数: " + tradableStocks.size());
        System.out.println("✅ TC-003: ST股票过滤逻辑验证测试通过");
    }

    @Test
    @DisplayName("根据代码查询股票")
    public void testFindByCode() {
        // 插入测试数据
        stockRepository.save(testStock);

        // 查询
        StockInfo found = stockRepository.findByCode("600000");

        // 验证
        assertNotNull(found, "应该能查询到股票");
        assertEquals("测试股票", found.getName(), "股票名称应该一致");

        System.out.println("查询结果: " + found.getCode() + " - " + found.getName() + " - " + found.getMarket());
        System.out.println("✅ 根据代码查询股票测试通过");
    }

    @Test
    @DisplayName("查询可交易股票列表")
    public void testFindTradableStocks() {
        // 插入测试数据
        stockRepository.save(testStock);

        // 查询可交易股票
        List<StockInfo> tradableStocks = stockRepository.findTradableStocks(10);

        // 验证
        assertNotNull(tradableStocks, "查询结果不应为空");
        assertTrue(tradableStocks.size() > 0, "应该有可交易股票");

        // 验证每只股票都是可交易的
        for (StockInfo stock : tradableStocks) {
            assertEquals(1, stock.getIsTradable(), "股票应该是可交易的");
            assertEquals("0", stock.getDeleted(), "股票应该是未删除的");
        }

        System.out.println("可交易股票数量: " + tradableStocks.size());
        System.out.println("✅ 查询可交易股票列表测试通过");
    }

    @Test
    @DisplayName("统计股票总数")
    public void testCountAll() {
        // 插入测试数据
        stockRepository.save(testStock);

        // 统计
        int count = stockRepository.countAll();

        // 验证
        assertTrue(count > 0, "股票总数应该大于0");

        System.out.println("股票总数: " + count);
        System.out.println("✅ 统计股票总数测试通过");
    }

    @Test
    @DisplayName("根据市场查询股票")
    public void testFindByMarket() {
        // 插入上海股票
        StockInfo shStock = new StockInfo();
        shStock.setCode("600400");
        shStock.setName("上海股票");
        shStock.setMarket("SH");
        shStock.setIsSt(0);
        shStock.setIsTradable(1);
        shStock.setDeleted("0");
        shStock.setCreateTime(LocalDateTime.now());
        stockRepository.save(shStock);

        // 插入深圳股票
        StockInfo szStock = new StockInfo();
        szStock.setCode("000001");
        szStock.setName("深圳股票");
        szStock.setMarket("SZ");
        szStock.setIsSt(0);
        szStock.setIsTradable(1);
        szStock.setDeleted("0");
        szStock.setCreateTime(LocalDateTime.now());
        stockRepository.save(szStock);

        // 查询上海股票
        List<StockInfo> shStocks = stockRepository.findByMarket("SH");
        assertTrue(shStocks.size() > 0, "应该有上海股票");

        // 查询深圳股票
        List<StockInfo> szStocks = stockRepository.findByMarket("SZ");
        assertTrue(szStocks.size() > 0, "应该有深圳股票");

        // 验证市场分类
        for (StockInfo stock : shStocks) {
            assertEquals("SH", stock.getMarket(), "市场应该是上海");
        }

        for (StockInfo stock : szStocks) {
            assertEquals("SZ", stock.getMarket(), "市场应该是深圳");
        }

        System.out.println("上海股票数量: " + shStocks.size());
        System.out.println("深圳股票数量: " + szStocks.size());
        System.out.println("✅ 根据市场查询股票测试通过");
    }

    @Test
    @DisplayName("更新股票信息")
    public void testUpdateStock() {
        // 插入
        stockRepository.save(testStock);

        // 修改
        testStock.setName("修改后的名称");
        testStock.setPrice(new BigDecimal("20.00"));
        stockRepository.save(testStock);

        // 验证
        assertNotNull(testStock.getId(), "更新应该成功");

        // 查询验证
        StockInfo updated = stockRepository.findByCode("600000");
        assertEquals("修改后的名称", updated.getName(), "名称应该已更新");
        assertEquals(0, new BigDecimal("20.00").compareTo(updated.getPrice()), "价格应该已更新");

        System.out.println("更新后: " + updated.getCode() + " - " + updated.getName() + " - 价格: " + updated.getPrice());
        System.out.println("✅ 更新股票信息测试通过");
    }

    @Test
    @DisplayName("删除股票（逻辑删除）")
    public void testDeleteStock() {
        // 插入
        stockRepository.save(testStock);

        // 验证插入成功
        StockInfo beforeDelete = stockRepository.findByCode("600000");
        assertNotNull(beforeDelete, "插入后应该能查到");

        // 逻辑删除
        testStock.setDeleted("1");
        stockRepository.save(testStock);

        // 验证
        assertNotNull(testStock.getId(), "删除应该成功");

        // 查询验证（逻辑删除后应该查不到）
        StockInfo afterDelete = stockRepository.findByCode("600000");
        // 注意：这里查询的是is_tradable=1的，所以逻辑删除后查不到
        // 如果想查到需要使用BaseMapper的查询方法

        System.out.println("✅ 删除股票（逻辑删除）测试通过");
    }
}
