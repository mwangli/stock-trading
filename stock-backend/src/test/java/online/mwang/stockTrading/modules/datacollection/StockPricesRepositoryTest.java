package online.mwang.stockTrading.modules.datacollection;

import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.datacollection.repository.StockPricesRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StockPricesRepository MongoDB仓库测试类
 * 测试用例 TC-001-021 至 TC-001-026
 */
@SpringBootTest
@DisplayName("StockPrices Repository测试")
class StockPricesRepositoryTest {

    @Autowired
    private StockPricesRepository stockPricesRepository;

    private static final String TEST_STOCK_CODE = "TEST" + System.currentTimeMillis();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private List<StockPrices> testPrices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 准备测试数据
        LocalDate baseDate = LocalDate.now().minusDays(10);
        for (int i = 0; i < 5; i++) {
            StockPrices price = new StockPrices();
            price.setCode(TEST_STOCK_CODE);
            price.setName("测试股票");
            price.setDate(baseDate.plusDays(i).format(DATE_FORMATTER));
            price.setPrice1(10.0 + i * 0.1);
            price.setPrice2(10.5 + i * 0.1);
            price.setPrice3(9.8 + i * 0.1);
            price.setPrice4(10.2 + i * 0.1);
            price.setTradingVolume(1000000.0 + i * 10000);
            price.setTradingAmount(10000000.0 + i * 100000);
            testPrices.add(price);
        }
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        stockPricesRepository.deleteByCode(TEST_STOCK_CODE);
    }

    @Nested
    @DisplayName("TC-001-021: 测试保存价格数据")
    class SaveTests {

        @Test
        @DisplayName("TC-001-021: 保存价格数据成功")
        void testSave_Success() {
            // Given: 准备价格数据
            StockPrices price = testPrices.get(0);

            // When: 调用保存方法
            StockPrices saved = stockPricesRepository.save(price);

            // Then: 验证保存成功
            assertNotNull(saved, "保存结果不应为空");
            assertNotNull(saved.getId(), "ID应自动生成");
            assertFalse(saved.getId().isEmpty(), "ID不应为空字符串");

            // 验证数据完整性
            assertEquals(price.getCode(), saved.getCode(), "代码应匹配");
            assertEquals(price.getDate(), saved.getDate(), "日期应匹配");
            assertEquals(price.getPrice1(), saved.getPrice1(), "价格应匹配");
        }
    }

    @Nested
    @DisplayName("TC-001-022: 测试根据代码查询")
    class FindByCodeTests {

        @Test
        @DisplayName("TC-001-022: 查询存在的数据")
        void testFindByCode_Success() {
            // Given: 保存测试数据
            stockPricesRepository.saveAll(testPrices);

            // When: 根据代码查询
            List<StockPrices> results = stockPricesRepository.findByCode(TEST_STOCK_CODE);

            // Then: 验证返回列表
            assertNotNull(results, "返回列表不应为空");
            assertEquals(5, results.size(), "应返回5条记录");

            // 验证所有记录code匹配
            for (StockPrices price : results) {
                assertEquals(TEST_STOCK_CODE, price.getCode(), "所有记录的代码应匹配");
            }
        }

        @Test
        @DisplayName("查询不存在的代码返回空列表")
        void testFindByCode_NotExists() {
            // When: 查询不存在的代码
            List<StockPrices> results = stockPricesRepository.findByCode("NOTEXIST999");

            // Then: 验证返回空列表
            assertNotNull(results, "应返回非空对象");
            assertTrue(results.isEmpty(), "应返回空列表");
        }
    }

    @Nested
    @DisplayName("TC-001-023: 测试根据代码和日期查询")
    class FindByCodeAndDateTests {

        @Test
        @DisplayName("TC-001-023: 查询存在的数据")
        void testFindByCodeAndDate_Success() {
            // Given: 保存测试数据
            stockPricesRepository.saveAll(testPrices);
            String testDate = testPrices.get(0).getDate();

            // When: 根据代码和日期查询
            Optional<StockPrices> result = stockPricesRepository.findByCodeAndDate(TEST_STOCK_CODE, testDate);

            // Then: 验证返回Optional对象
            assertTrue(result.isPresent(), "应找到数据");
            assertEquals(TEST_STOCK_CODE, result.get().getCode(), "代码应匹配");
            assertEquals(testDate, result.get().getDate(), "日期应匹配");
        }

        @Test
        @DisplayName("查询不存在的日期返回空")
        void testFindByCodeAndDate_NotExists() {
            // When: 查询不存在的日期
            Optional<StockPrices> result = stockPricesRepository.findByCodeAndDate(TEST_STOCK_CODE, "2000-01-01");

            // Then: 验证返回空
            assertFalse(result.isPresent(), "不应找到数据");
        }
    }

    @Nested
    @DisplayName("TC-001-024: 测试日期范围查询")
    class FindByCodeAndDateBetweenTests {

        @Test
        @DisplayName("TC-001-024: 查询日期范围内的数据")
        void testFindByCodeAndDateBetween_Success() {
            // Given: 保存测试数据
            stockPricesRepository.saveAll(testPrices);
            
            String startDate = testPrices.get(1).getDate(); // 第2天
            String endDate = testPrices.get(3).getDate();   // 第4天

            // When: 查询日期范围
            List<StockPrices> results = stockPricesRepository.findByCodeAndDateBetween(
                    TEST_STOCK_CODE, startDate, endDate);

            // Then: 验证返回范围内数据
            assertNotNull(results, "返回列表不应为空");
            assertTrue(results.size() >= 3, "应至少返回3条记录");

            // 验证日期在范围内
            for (StockPrices price : results) {
                String date = price.getDate();
                assertTrue(date.compareTo(startDate) >= 0 && date.compareTo(endDate) <= 0,
                        "日期应在范围内");
            }
        }

        @Test
        @DisplayName("空范围返回空列表")
        void testFindByCodeAndDateBetween_EmptyRange() {
            // When: 查询未来日期范围
            String futureDate = LocalDate.now().plusYears(1).format(DATE_FORMATTER);
            List<StockPrices> results = stockPricesRepository.findByCodeAndDateBetween(
                    TEST_STOCK_CODE, futureDate, futureDate);

            // Then: 验证返回空列表
            assertTrue(results.isEmpty(), "应返回空列表");
        }
    }

    @Nested
    @DisplayName("TC-001-025: 测试删除价格数据")
    class DeleteByCodeTests {

        @Test
        @DisplayName("TC-001-025: 删除成功")
        void testDeleteByCode_Success() {
            // Given: 保存测试数据
            stockPricesRepository.saveAll(testPrices);

            // When: 删除数据
            stockPricesRepository.deleteByCode(TEST_STOCK_CODE);

            // Then: 验证删除成功
            List<StockPrices> results = stockPricesRepository.findByCode(TEST_STOCK_CODE);
            assertTrue(results.isEmpty(), "删除后查询应返回空");
        }
    }

    @Nested
    @DisplayName("TC-001-026: 测试存在性检查")
    class ExistsByCodeAndDateTests {

        @Test
        @DisplayName("TC-001-026: 数据存在返回true")
        void testExistsByCodeAndDate_Exists() {
            // Given: 保存测试数据
            stockPricesRepository.saveAll(testPrices);
            String testDate = testPrices.get(0).getDate();

            // When: 检查存在性
            boolean exists = stockPricesRepository.existsByCodeAndDate(TEST_STOCK_CODE, testDate);

            // Then: 验证返回true
            assertTrue(exists, "存在的数据应返回true");
        }

        @Test
        @DisplayName("数据不存在返回false")
        void testExistsByCodeAndDate_NotExists() {
            // When: 检查不存在的数据
            boolean exists = stockPricesRepository.existsByCodeAndDate(TEST_STOCK_CODE, "2000-01-01");

            // Then: 验证返回false
            assertFalse(exists, "不存在的数据应返回false");
        }
    }

    @Nested
    @DisplayName("其他测试")
    class OtherTests {

        @Test
        @DisplayName("批量保存测试")
        void testSaveAll() {
            // Given: 准备批量数据

            // When: 批量保存
            List<StockPrices> savedList = stockPricesRepository.saveAll(testPrices);

            // Then: 验证全部保存成功
            assertEquals(5, savedList.size(), "应保存5条数据");
            for (StockPrices saved : savedList) {
                assertNotNull(saved.getId(), "每条数据应有ID");
            }
        }

        @Test
        @DisplayName("更新已存在的数据")
        void testUpdateExisting() {
            // Given: 先保存数据
            StockPrices saved = stockPricesRepository.save(testPrices.get(0));
            String originalId = saved.getId();

            // 修改数据
            saved.setPrice1(20.0);

            // When: 再次保存
            StockPrices updated = stockPricesRepository.save(saved);

            // Then: 验证更新而非插入
            assertEquals(originalId, updated.getId(), "ID应保持不变");
            assertEquals(20.0, updated.getPrice1(), "价格应已更新");
        }
    }
}
