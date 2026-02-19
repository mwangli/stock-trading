package online.mwang.stockTrading.modules.datacollection;

import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.mapper.StockInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StockInfoMapper 数据访问层测试类
 * 测试用例 TC-001-015 至 TC-001-020
 */
@SpringBootTest
@Transactional
@DisplayName("StockInfo Mapper测试")
class StockInfoMapperTest {

    @Autowired
    private StockInfoMapper stockInfoMapper;

    private StockInfo testStock;

    @BeforeEach
    void setUp() {
        // 创建测试用的股票信息
        testStock = new StockInfo();
        testStock.setCode("TEST" + System.currentTimeMillis()); // 使用时间戳确保唯一
        testStock.setName("测试股票");
        testStock.setMarket("SZ");
        testStock.setIndustry("测试行业");
        testStock.setListingDate(LocalDate.now());
        testStock.setIsSt(false);
        testStock.setIsTradable(true);
        testStock.setCreateTime(new Date());
        testStock.setUpdateTime(new Date());
        testStock.setDeleted("0");
        testStock.setSelected("0");
    }

    @Nested
    @DisplayName("TC-001-015: 测试插入股票信息")
    class InsertTests {

        @Test
        @DisplayName("TC-001-015: 正常插入股票信息")
        void testInsert_Success() {
            // Given: 准备股票信息对象
            StockInfo stock = testStock;

            // When: 调用插入方法
            int result = stockInfoMapper.insert(stock);

            // Then: 验证插入成功
            assertEquals(1, result, "应返回影响行数1");
            assertNotNull(stock.getId(), "ID应自动生成");
            assertTrue(stock.getId() > 0, "ID应为正数");

            // 验证可以查询到
            StockInfo saved = stockInfoMapper.getByCode(stock.getCode());
            assertNotNull(saved, "应能查询到插入的数据");
        }
    }

    @Nested
    @DisplayName("TC-001-016: 测试根据代码查询")
    class GetByCodeTests {

        @Test
        @DisplayName("TC-001-016: 查询存在的股票")
        void testGetByCode_Exists() {
            // Given: 先插入测试数据
            stockInfoMapper.insert(testStock);

            // When: 根据代码查询
            StockInfo result = stockInfoMapper.getByCode(testStock.getCode());

            // Then: 验证返回正确对象
            assertNotNull(result, "应返回非空对象");
            assertEquals(testStock.getCode(), result.getCode(), "代码应匹配");
            assertEquals(testStock.getName(), result.getName(), "名称应匹配");
            assertEquals(testStock.getMarket(), result.getMarket(), "市场应匹配");
        }

        @Test
        @DisplayName("TC-001-019: 查询不存在的股票")
        void testGetByCode_NotExists() {
            // Given: 不存在的股票代码
            String nonExistCode = "NOTEXIST999";

            // When: 查询不存在的股票
            StockInfo result = stockInfoMapper.getByCode(nonExistCode);

            // Then: 验证返回null
            assertNull(result, "不存在的股票应返回null");
        }
    }

    @Nested
    @DisplayName("TC-001-017: 测试更新股票信息")
    class UpdateTests {

        @Test
        @DisplayName("TC-001-017: 更新股票信息")
        void testUpdate_Success() {
            // Given: 先插入数据
            stockInfoMapper.insert(testStock);
            
            // 修改字段
            testStock.setName("更新后的名称");
            testStock.setIndustry("更新后的行业");
            testStock.setUpdateTime(new Date());

            // When: 调用更新方法
            int result = stockInfoMapper.updateById(testStock);

            // Then: 验证更新成功
            assertEquals(1, result, "应返回影响行数1");

            // 验证字段值已改变
            StockInfo updated = stockInfoMapper.getByCode(testStock.getCode());
            assertEquals("更新后的名称", updated.getName(), "名称应已更新");
            assertEquals("更新后的行业", updated.getIndustry(), "行业应已更新");
        }
    }

    @Nested
    @DisplayName("TC-001-018: 测试删除股票")
    class DeleteTests {

        @Test
        @DisplayName("TC-001-018: 删除股票")
        void testDelete_Success() {
            // Given: 先插入数据
            stockInfoMapper.insert(testStock);
            String code = testStock.getCode();

            // When: 调用删除方法
            int result = stockInfoMapper.deleteByCode(code);

            // Then: 验证删除成功
            assertEquals(1, result, "应返回影响行数1");

            // 验证查询返回null
            StockInfo deleted = stockInfoMapper.getByCode(code);
            assertNull(deleted, "删除后查询应返回null");
        }
    }

    @Nested
    @DisplayName("TC-001-020: 测试批量插入")
    class BatchInsertTests {

        @Test
        @DisplayName("TC-001-020: 批量插入性能测试")
        void testBatchInsert_Performance() {
            // Given: 准备100条数据
            List<StockInfo> stockList = new ArrayList<>();
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < 100; i++) {
                StockInfo stock = new StockInfo();
                stock.setCode("BATCH" + System.currentTimeMillis() + i);
                stock.setName("批量测试" + i);
                stock.setMarket(i % 2 == 0 ? "SH" : "SZ");
                stock.setIndustry("批量测试行业");
                stock.setIsSt(false);
                stock.setIsTradable(true);
                stock.setCreateTime(new Date());
                stock.setUpdateTime(new Date());
                stock.setDeleted("0");
                stock.setSelected("0");
                stockList.add(stock);
            }

            // When: 批量插入
            int successCount = 0;
            for (StockInfo stock : stockList) {
                try {
                    int result = stockInfoMapper.insert(stock);
                    if (result > 0) successCount++;
                } catch (Exception e) {
                    // 忽略重复键错误
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            // Then: 验证结果
            assertEquals(100, successCount, "应全部插入成功");
            assertTrue(duration < 1000, "批量插入100条应在1秒内完成，实际耗时: " + duration + "ms");
            
            System.out.println("批量插入100条数据耗时: " + duration + "ms");
        }
    }

    @Nested
    @DisplayName("getNameByCode测试")
    class GetNameByCodeTests {

        @Test
        @DisplayName("根据代码获取名称")
        void testGetNameByCode_Success() {
            // Given: 先插入数据
            stockInfoMapper.insert(testStock);

            // When: 根据代码获取名称
            String name = stockInfoMapper.getNameByCode(testStock.getCode());

            // Then: 验证返回正确的名称
            assertNotNull(name, "名称不应为空");
            assertEquals(testStock.getName(), name, "名称应匹配");
        }

        @Test
        @DisplayName("不存在的代码返回null")
        void testGetNameByCode_NotExists() {
            // When: 查询不存在的代码
            String name = stockInfoMapper.getNameByCode("NOTEXIST");

            // Then: 验证返回null
            assertNull(name, "不存在的代码应返回null");
        }
    }
}
