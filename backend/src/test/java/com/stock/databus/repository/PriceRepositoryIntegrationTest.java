package com.stock.databus.repository;

import com.stock.databus.entity.StockPrice;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 价格数据 Repository 集成测试
 * 使用真实 MongoDB 数据库进行测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/stock_trading_test"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PriceRepositoryIntegrationTest {

    @Autowired
    private PriceRepository priceRepository;

    private static final String TEST_CODE = "000001";

    @BeforeEach
    void setUp() {
        priceRepository.deleteByCode(TEST_CODE);
    }

    @Test
    @Order(1)
    @DisplayName("保存和查询单条数据")
    void testSaveAndFind() {
        StockPrice price = createTestPrice(TEST_CODE, LocalDate.now());
        priceRepository.save(price);

        List<StockPrice> found = priceRepository.findByCodeOrderByDateAsc(TEST_CODE);
        assertEquals(1, found.size());
        assertEquals(TEST_CODE, found.get(0).getCode());
    }

    @Test
    @Order(2)
    @DisplayName("按日期范围查询")
    void testFindByDateRange() {
        // 保存 30 天数据
        for (int i = 0; i < 30; i++) {
            StockPrice price = createTestPrice(TEST_CODE, LocalDate.now().minusDays(i));
            priceRepository.save(price);
        }

        LocalDate startDate = LocalDate.now().minusDays(15);
        LocalDate endDate = LocalDate.now();

        List<StockPrice> found = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(
            TEST_CODE, startDate, endDate
        );

        assertTrue(found.size() > 0);
        assertTrue(found.size() <= 30);
    }

    @Test
    @Order(3)
    @DisplayName("检查数据是否存在")
    void testExists() {
        LocalDate existingDate = LocalDate.now();
        StockPrice price = createTestPrice(TEST_CODE, existingDate);
        priceRepository.save(price);

        assertTrue(priceRepository.existsByCodeAndDate(TEST_CODE, existingDate));
        assertFalse(priceRepository.existsByCodeAndDate(TEST_CODE, existingDate.plusDays(1)));
    }

    @Test
    @Order(4)
    @DisplayName("唯一索引测试 - 重复数据应失败")
    void testUniqueIndex() {
        LocalDate date = LocalDate.now();
        StockPrice price1 = createTestPrice(TEST_CODE, date);
        StockPrice price2 = createTestPrice(TEST_CODE, date);

        priceRepository.save(price1);

        // 尝试保存重复数据（应失败或更新）
        assertThrows(Exception.class, () -> {
            priceRepository.save(price2);
        });
    }

    @Test
    @Order(5)
    @DisplayName("删除数据")
    void testDelete() {
        StockPrice price = createTestPrice(TEST_CODE, LocalDate.now());
        priceRepository.save(price);

        priceRepository.deleteByCode(TEST_CODE);

        List<StockPrice> found = priceRepository.findByCodeOrderByDateAsc(TEST_CODE);
        assertTrue(found.isEmpty());
    }

    private StockPrice createTestPrice(String code, LocalDate date) {
        StockPrice price = new StockPrice();
        price.setCode(code);
        price.setName("测试股票");
        price.setDate(date);
        price.setOpenPrice(new BigDecimal("10.00"));
        price.setHighPrice(new BigDecimal("10.50"));
        price.setLowPrice(new BigDecimal("9.80"));
        price.setClosePrice(new BigDecimal("10.20"));
        price.setVolume(new BigDecimal("10000"));
        price.setAmount(new BigDecimal("102000"));
        return price;
    }

    @AfterEach
    void tearDown() {
        priceRepository.deleteByCode(TEST_CODE);
    }
}